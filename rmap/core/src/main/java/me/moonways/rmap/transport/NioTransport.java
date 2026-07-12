package me.moonways.rmap.transport;

import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plain-NIO транспорт RMAP: 1 selector-поток (только I/O) + worker-pool (спека §9). */
public final class NioTransport {

    private final Selector selector;
    private final ExecutorService workers;
    private final ConcurrentLinkedQueue<Runnable> selectorTasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ServerSocketChannel serverChannel; // null для клиентского транспорта
    private RmapConfig serverConfig;
    private ConnectionListener serverListener;
    private volatile int boundPort = -1;
    private final Thread selectorThread;

    private NioTransport() {
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new RmapTransportException("cannot open selector", e);
        }
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.workers = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "rmap-worker");
            t.setDaemon(true);
            return t;
        });
        this.selectorThread = new Thread(this::loop, "rmap-selector");
        this.selectorThread.setDaemon(true);
    }

    public static NioTransport startServer(InetSocketAddress bind, RmapConfig config, ConnectionListener listener) {
        NioTransport t = new NioTransport();
        t.serverConfig = config;
        t.serverListener = listener;
        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.socket().bind(bind);
            ssc.register(t.selector, SelectionKey.OP_ACCEPT);
            t.serverChannel = ssc;
            t.boundPort = ssc.socket().getLocalPort();
        } catch (IOException e) {
            throw new RmapTransportException("cannot bind " + bind, e);
        }
        t.selectorThread.start();
        return t;
    }

    public static NioTransport clientTransport(RmapConfig config) {
        NioTransport t = new NioTransport();
        t.selectorThread.start();
        return t;
    }

    public int boundPort() {
        return boundPort;
    }

    /** Неблокирующий connect. Возвращает Connection (ещё не соединён — onOpened придёт после finishConnect). */
    public RmapConnection connect(String host, int port, RmapConfig config, ConnectionListener listener) {
        try {
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            RmapConnection conn = new RmapConnection(this, sc, false, config);
            boolean connectedNow = sc.connect(new InetSocketAddress(host, port));
            runOnSelector(() -> {
                try {
                    int ops = connectedNow ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT;
                    SelectionKey k = sc.register(selector, ops, new Attach(conn, listener, config));
                    conn.setKey(k);
                    if (connectedNow) {
                        deliverOpened(conn, listener);
                    }
                } catch (IOException e) {
                    fail(conn, listener, e);
                }
            });
            return conn;
        } catch (IOException e) {
            throw new RmapTransportException("connect failed", e);
        }
    }

    public void stop() {
        running.set(false);
        selector.wakeup();
        try { selectorThread.join(2000); } catch (InterruptedException ignored) { }
        // graceful: дать shutdown-фазе доставить teardown-onClosed, лишь потом форсить
        workers.shutdown();
        try {
            if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try { selector.close(); } catch (IOException ignored) { }
        try { if (serverChannel != null) serverChannel.close(); } catch (IOException ignored) { }
    }

    // ---- внутреннее ----
    private static final class Attach {
        final RmapConnection conn;
        final ConnectionListener listener;
        final RmapConfig config;
        Attach(RmapConnection c, ConnectionListener l, RmapConfig cfg) { conn = c; listener = l; config = cfg; }
    }

    void requestWrite(RmapConnection conn) {
        runOnSelector(() -> {
            SelectionKey k = conn.getKeyInternal();
            if (k != null && k.isValid()) {
                k.interestOps(k.interestOps() | SelectionKey.OP_WRITE);
            }
        });
    }

    void closeConnection(RmapConnection conn, Throwable cause) {
        runOnSelector(() -> doClose(conn, cause));
    }

    /** Graceful close: закрыть ПОСЛЕ полного слива outbound (доставка OTHER-кадра инициатору). */
    void closeAfterFlush(RmapConnection conn, Throwable cause) {
        runOnSelector(() -> {
            SelectionKey k = conn.getKeyInternal();
            if (k == null || !k.isValid() || !conn.channel().isConnected()) {
                // канал ещё/уже не пригоден для записи → закрыть сразу (кадр не уйдёт)
                doClose(conn, cause);
                return;
            }
            conn.setCloseCause(cause);
            conn.closeAfterFlushFlag().set(true);
            k.interestOps(k.interestOps() | SelectionKey.OP_WRITE);
        });
    }

    private void runOnSelector(Runnable task) {
        selectorTasks.add(task);
        selector.wakeup();
    }

    private void loop() {
        while (running.get()) {
            try {
                selector.select(500);
            } catch (IOException e) {
                break;
            }
            Runnable task;
            while ((task = selectorTasks.poll()) != null) {
                task.run();
            }
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey k = it.next();
                it.remove();
                if (!k.isValid()) continue;
                try {
                    if (k.isAcceptable()) doAccept();
                    else if (k.isConnectable()) doFinishConnect(k);
                    else {
                        if (k.isReadable()) doRead(k);
                        if (k.isValid() && k.isWritable()) doWrite(k);
                    }
                } catch (IOException | RuntimeException e) {
                    Attach a = (Attach) k.attachment();
                    if (a != null) doClose(a.conn, e);
                    else k.cancel();
                }
            }
        }
        // shutdown: закрыть все каналы
        for (SelectionKey k : selector.keys()) {
            Attach a = (Attach) k.attachment();
            if (a != null) doClose(a.conn, new RmapTransportException("transport stopped"));
        }
    }

    private void doAccept() throws IOException {
        SocketChannel sc = serverChannel.accept();
        if (sc == null) return;
        sc.configureBlocking(false);
        RmapConnection conn = new RmapConnection(this, sc, true, serverConfig);
        SelectionKey k = sc.register(selector, SelectionKey.OP_READ, new Attach(conn, serverListener, serverConfig));
        conn.setKey(k);
        deliverOpened(conn, serverListener);
    }

    private void doFinishConnect(SelectionKey k) throws IOException {
        Attach a = (Attach) k.attachment();
        SocketChannel sc = a.conn.channel();
        if (sc.finishConnect()) {
            k.interestOps(SelectionKey.OP_READ);
            deliverOpened(a.conn, a.listener);
        }
    }

    private void doRead(SelectionKey k) throws IOException {
        Attach a = (Attach) k.attachment();
        RmapConnection conn = a.conn;
        SocketChannel sc = conn.channel();
        ByteBuffer buf = ByteBuffer.allocate(16 * 1024);
        int n = sc.read(buf);
        if (n < 0) { doClose(conn, null); return; }
        buf.flip();
        appendInbound(conn, buf);
        drainFrames(conn, a.listener);
    }

    private void appendInbound(RmapConnection conn, ByteBuffer incoming) {
        ByteBuffer in = conn.inbound();
        if (in.remaining() < incoming.remaining()) {
            int needed = in.position() + incoming.remaining();
            ByteBuffer bigger = ByteBuffer.allocate(Math.max(in.capacity() * 2, needed));
            in.flip();
            bigger.put(in);
            conn.growInbound(bigger);
            in = bigger;
        }
        in.put(incoming);
    }

    /** Извлечь все полные кадры из inbound; передать в worker. Length-prefix framing. */
    private void drainFrames(RmapConnection conn, ConnectionListener listener) {
        ByteBuffer in = conn.inbound();
        in.flip();
        while (in.remaining() >= 4) {
            in.mark();
            int len = in.getInt();
            if (len < 0 || len > conn.frameLimit()) {
                conn.close(me.moonways.rmap.wire.OtherCode.FRAME_TOO_LARGE, "frame too large: " + len);
                return;
            }
            if (in.remaining() < len) { in.reset(); break; }
            int frameTypeCode = in.get() & 0xFF;
            long callId = in.getLong();
            byte[] payload = new byte[len - FrameCodec.HEADER_AFTER_LEN];
            in.get(payload);
            Frame frame = FrameCodec.decodeBody(frameTypeCode, callId, payload);
            workers.execute(() -> {
                try { listener.onFrame(conn, frame); }
                catch (RuntimeException e) { closeConnection(conn, e); }
            });
        }
        in.compact(); // оставить неполный хвост
    }

    private void doWrite(SelectionKey k) throws IOException {
        Attach a = (Attach) k.attachment();
        RmapConnection conn = a.conn;
        SocketChannel sc = conn.channel();
        while (true) {
            ByteBuffer cur = conn.currentWrite();
            if (cur == null || !cur.hasRemaining()) {
                byte[] next = conn.outbound().poll();
                if (next == null) {
                    // очередь пуста и currentWrite дренирован — всё отправлено.
                    // graceful close: если запрошен close-after-flush, закрываем именно здесь.
                    if (conn.closeAfterFlushFlag().get()) {
                        doClose(conn, conn.closeCause());
                        return;
                    }
                    // снять OP_WRITE
                    k.interestOps(k.interestOps() & ~SelectionKey.OP_WRITE);
                    conn.writeScheduled().set(false);
                    // повторная проверка гонки: если между poll и set кто-то добавил
                    if (!conn.outbound().isEmpty() && conn.writeScheduled().compareAndSet(false, true)) {
                        k.interestOps(k.interestOps() | SelectionKey.OP_WRITE);
                    }
                    return;
                }
                cur = ByteBuffer.wrap(next);
                conn.setCurrentWrite(cur);
                conn.outboundBytes().addAndGet(-next.length);
            }
            int wrote = sc.write(cur);
            if (wrote == 0 && cur.hasRemaining()) return; // сокет полон — ждём следующего OP_WRITE
        }
    }

    private void doClose(RmapConnection conn, Throwable cause) {
        // идемпотентность: onClosed ровно один раз, даже если doClose пришёл двумя путями
        // (например conn.close() + параллельный FIN/ошибка на selector-потоке).
        if (!conn.closedFlag().compareAndSet(false, true)) return;
        SelectionKey k = conn.getKeyInternal();
        if (k != null) k.cancel();
        try { conn.channel().close(); } catch (IOException ignored) { }
        ConnectionListener l = conn.listenerInternal();
        if (l != null) {
            workers.execute(() -> {
                try { l.onClosed(conn, cause); } catch (RuntimeException ignored) { }
            });
        }
    }

    private void deliverOpened(RmapConnection conn, ConnectionListener listener) {
        conn.setListenerInternal(listener);
        workers.execute(() -> {
            try { listener.onOpened(conn); }
            catch (RuntimeException e) { closeConnection(conn, e); }
        });
    }

    private void fail(RmapConnection conn, ConnectionListener listener, Throwable e) {
        conn.setListenerInternal(listener);
        doClose(conn, e);
    }
}
