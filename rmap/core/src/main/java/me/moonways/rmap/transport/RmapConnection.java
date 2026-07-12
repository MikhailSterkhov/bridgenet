package me.moonways.rmap.transport;

import lombok.Getter;
import lombok.Setter;
import me.moonways.rmap.api.RmapLogger;
import me.moonways.rmap.api.RmapLogging;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameCodec;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Одно соединение RMAP. send() потокобезопасен; чтение/запись сокета — на selector-потоке. */
public final class RmapConnection {

    private static final RmapLogger LOG = RmapLogging.get(RmapConnection.class.getName());

    private final NioTransport transport;
    private final SocketChannel channel;
    @Getter private final boolean serverSide;
    private final RmapConfig config;

    private SelectionKey key; // ставится транспортом
    private ConnectionListener listener; // ставится транспортом (deliverOpened)
    // inbound — только selector-поток
    private ByteBuffer inbound = ByteBuffer.allocate(1024);
    // outbound — потокобезопасно
    private final ConcurrentLinkedQueue<byte[]> outbound = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean writeScheduled = new AtomicBoolean(false);
    private final AtomicLong outboundBytes = new AtomicLong(0);
    // §5.2a/§9: per-connection упорядоченная доставка onFrame — кадры selector кладёт СТРОГО в
    // wire-порядке, worker дренирует по одному (один in-flight на соединение). Это даёт serial-decode
    // читаемый wire-порядок classRef-интернирования, недостижимый при конкурентном onFrame.
    private final ConcurrentLinkedQueue<Frame> inboundFrames = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean frameDispatchScheduled = new AtomicBoolean(false);
    private ByteBuffer currentWrite; // остаток недописанного, только selector
    // graceful close: закрыть ПОСЛЕ полного слива outbound (доставка OTHER-кадра инициатору)
    private final AtomicBoolean closeAfterFlush = new AtomicBoolean(false);
    private volatile Throwable closeCause; // причина для onClosed при close-after-flush
    // §4: жёсткий дедлайн close-after-flush (now+const); ставится/читается только selector-потоком
    private long closeDeadlineMillis;
    // идемпотентность doClose: onClosed ровно один раз
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Getter @Setter private volatile boolean authenticated = false;
    @Getter @Setter private volatile Object attachment;
    private volatile int frameLimit; // текущий предел (preAuth → full после auth)

    // §4.3 pre-auth DoS-отбойник: серверные (принятые doAccept) соединения учтены в счётчиках
    // транспорта. bouncerCounted помечается на accept; bouncerRemoteIp фиксируется тогда же
    // (getRemoteAddress после close вернул бы null, а per-IP декремент нужен именно на close).
    // preAuthReleased — CAS-защёлка «pre-auth-слот уже освобождён»: декремент ровно один раз,
    // кто бы ни пришёл первым — auth-успех (markAuthenticated) или close (doClose).
    private volatile boolean bouncerCounted = false;
    private volatile java.net.InetAddress bouncerRemoteIp;
    private final AtomicBoolean preAuthReleased = new AtomicBoolean(false);

    RmapConnection(NioTransport transport, SocketChannel channel, boolean serverSide, RmapConfig config) {
        this.transport = transport;
        this.channel = channel;
        this.serverSide = serverSide;
        this.config = config;
        this.frameLimit = config.getPreAuthFrameLimit();
    }

    void setKey(SelectionKey key) { this.key = key; }
    SelectionKey getKeyInternal() { return key; }
    ConnectionListener listenerInternal() { return listener; }
    void setListenerInternal(ConnectionListener l) { this.listener = l; }
    SocketChannel channel() { return channel; }
    ByteBuffer inbound() { return inbound; }
    void growInbound(ByteBuffer bigger) { this.inbound = bigger; }
    RmapConfig config() { return config; }
    int frameLimit() { return frameLimit; }

    public void setFrameLimitFull() { this.frameLimit = config.getFrameLimit(); }

    public java.net.SocketAddress remoteAddress() {
        try { return channel.getRemoteAddress(); } catch (Exception e) { return null; }
    }

    /** true после doClose (onClosed уже поставлен/доставлен). Читает существующий closed-флаг. */
    public boolean isClosed() {
        return closed.get();
    }

    /** Потокобезопасная отправка кадра. */
    public void send(Frame frame) {
        // §3(г): соединение закрыто — НЕ ставим кадр в очередь. Иначе outbound мёртвого объекта
        // растёт неограниченно (sweepIdle/поздние send при RST-флуде: doClose уже отменил ключ,
        // очередь никогда не сольётся). Выбор — ТИХИЙ ДРОП, а не бросок RmapTransportException:
        // бросок пророс бы через keepAliveTick→sendPing в scheduleAtFixedRate и навсегда заглушил
        // бы keep-alive; graceful-путь close(code,msg) шлёт OTHER до выставления closed и не задет.
        if (closed.get()) {
            return;
        }
        byte[] wire = FrameCodec.encode(frame);
        long total = outboundBytes.addAndGet(wire.length);
        if (total > config.getOutboundLimitBytes()) {
            outboundBytes.addAndGet(-wire.length);
            throw new RmapTransportException("outbound limit exceeded");
        }
        outbound.add(wire);
        if (writeScheduled.compareAndSet(false, true)) {
            transport.requestWrite(this); // ставит OP_WRITE + wakeup на selector
        }
    }

    public void close() {
        transport.closeConnection(this, null);
    }

    /**
     * Graceful close ПОСЛЕ слива уже поставленных в outbound кадров, БЕЗ отправки собственного
     * OTHER (в отличие от {@link #close(int, String)}). Применяет RPC-слой (§7.2): сначала
     * {@code encodeAndSend} echo-callId OTHER структурного нарушения, затем этот close —
     * клиент получает код, потом FIN. Канал непригоден к записи → немедленный close.
     */
    public void closeAfterFlush() {
        transport.closeAfterFlush(this, new RmapTransportException("closed after flush"));
    }

    public void close(int otherCode, String message) {
        // спека §4.3/§4.2a: инициатор ошибки шлёт OTHER(callId=0) и закрывает ПОСЛЕ его отправки.
        Throwable cause = new RmapTransportException("closed: code=" + otherCode + " " + message);
        LOG.warn("sending connection-level OTHER(" + OtherCode.name(otherCode) + ") to " + remoteAddress()
                + ": " + message, null);
        try {
            me.moonways.rmap.codec.RmapByteWriter w = new me.moonways.rmap.codec.RmapByteWriter();
            w.writeInt(otherCode);
            w.writeStr(message == null ? "" : message);
            w.writeBool(false); // hasException=0
            send(new Frame(FrameType.OTHER, 0L, w.toByteArray()));
        } catch (RuntimeException ignored) {
            // send не прошёл (backpressure/лимит) → закрываем сразу, без flush
            transport.closeConnection(this, cause);
            return;
        }
        // OTHER поставлен в очередь — закрыть graceful ПОСЛЕ его слива в сокет
        transport.closeAfterFlush(this, cause);
    }

    // ---- per-connection упорядоченная доставка onFrame (§5.2a/§9) ----
    ConcurrentLinkedQueue<Frame> inboundFrames() { return inboundFrames; }
    AtomicBoolean frameDispatchScheduled() { return frameDispatchScheduled; }

    // ---- selector-поток: доступ к outbound ----
    ConcurrentLinkedQueue<byte[]> outbound() { return outbound; }
    AtomicBoolean writeScheduled() { return writeScheduled; }
    AtomicLong outboundBytes() { return outboundBytes; }
    ByteBuffer currentWrite() { return currentWrite; }
    void setCurrentWrite(ByteBuffer b) { this.currentWrite = b; }

    // ---- §4.3 pre-auth DoS-отбойник (метки ставит doAccept на selector-потоке) ----
    void markBouncerCounted(java.net.InetAddress ip) {
        this.bouncerRemoteIp = ip;
        this.bouncerCounted = true;
    }
    boolean isBouncerCounted() { return bouncerCounted; }
    java.net.InetAddress bouncerRemoteIp() { return bouncerRemoteIp; }
    AtomicBoolean preAuthReleasedFlag() { return preAuthReleased; }

    /** Освободить pre-auth-слот отбойника на auth-успехе (§4.3). Идемпотентно (CAS в транспорте);
     *  no-op для клиентских/неучтённых соединений. Вызывается {@link HandshakeState#markAuthenticated}. */
    void releasePreAuthSlot() {
        transport.releasePreAuthSlot(this);
    }

    // ---- graceful close / идемпотентность (доступ только с selector-потока) ----
    AtomicBoolean closeAfterFlushFlag() { return closeAfterFlush; }
    AtomicBoolean closedFlag() { return closed; }
    Throwable closeCause() { return closeCause; }
    void setCloseCause(Throwable t) { this.closeCause = t; }
    long closeDeadlineMillis() { return closeDeadlineMillis; }
    void setCloseDeadlineMillis(long v) { this.closeDeadlineMillis = v; }
}
