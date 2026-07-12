package me.moonways.rmap.transport;

import lombok.Getter;
import lombok.Setter;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameCodec;
import me.moonways.rmap.wire.FrameType;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Одно соединение RMAP. send() потокобезопасен; чтение/запись сокета — на selector-потоке. */
public final class RmapConnection {

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
    private ByteBuffer currentWrite; // остаток недописанного, только selector
    // graceful close: закрыть ПОСЛЕ полного слива outbound (доставка OTHER-кадра инициатору)
    private final AtomicBoolean closeAfterFlush = new AtomicBoolean(false);
    private volatile Throwable closeCause; // причина для onClosed при close-after-flush
    // идемпотентность doClose: onClosed ровно один раз
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Getter @Setter private volatile boolean authenticated = false;
    @Getter @Setter private volatile Object attachment;
    private volatile int frameLimit; // текущий предел (preAuth → full после auth)

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

    /** Потокобезопасная отправка кадра. */
    public void send(Frame frame) {
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

    public void close(int otherCode, String message) {
        // спека §4.3/§4.2a: инициатор ошибки шлёт OTHER(callId=0) и закрывает ПОСЛЕ его отправки.
        Throwable cause = new RmapTransportException("closed: code=" + otherCode + " " + message);
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

    // ---- selector-поток: доступ к outbound ----
    ConcurrentLinkedQueue<byte[]> outbound() { return outbound; }
    AtomicBoolean writeScheduled() { return writeScheduled; }
    AtomicLong outboundBytes() { return outboundBytes; }
    ByteBuffer currentWrite() { return currentWrite; }
    void setCurrentWrite(ByteBuffer b) { this.currentWrite = b; }

    // ---- graceful close / идемпотентность (доступ только с selector-потока) ----
    AtomicBoolean closeAfterFlushFlag() { return closeAfterFlush; }
    AtomicBoolean closedFlag() { return closed; }
    Throwable closeCause() { return closeCause; }
    void setCloseCause(Throwable t) { this.closeCause = t; }
}
