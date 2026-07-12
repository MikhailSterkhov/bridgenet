package me.moonways.rmap.api;

import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeState;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Серверный эндпоинт RMAP: слушает сокет, гонит каждое входящее соединение через взаимный
 * HMAC-handshake (§4.3) и после аутентификации держит его живым, отвечая PONG на PING (§4.4).
 */
public final class RmapServer {

    private final RmapConfig config;
    private final ScheduledExecutorService scheduler;
    private final Set<RmapConnection> connections = ConcurrentHashMap.newKeySet();

    private volatile InetSocketAddress bindAddress;
    private volatile NioTransport transport;
    private volatile Consumer<RmapConnection> onAuthenticatedCallback;

    RmapServer(RmapConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rmap-server-sched");
            t.setDaemon(true);
            return t;
        });
    }

    public void bind(InetSocketAddress address) {
        this.bindAddress = address;
    }

    public void onAuthenticated(Consumer<RmapConnection> callback) {
        this.onAuthenticatedCallback = callback;
    }

    public void start() {
        if (bindAddress == null) {
            throw new IllegalStateException("bind(address) must be called before start()");
        }
        this.transport = NioTransport.startServer(bindAddress, config, new Listener());
        // idle-sweep: закрыть соединения без входящего трафика дольше idleTimeout (§4.4).
        long idleMillis = config.getIdleTimeout().toMillis();
        scheduler.scheduleAtFixedRate(this::sweepIdle, idleMillis, idleMillis, TimeUnit.MILLISECONDS);
    }

    public int boundPort() {
        NioTransport t = transport;
        return t == null ? -1 : t.boundPort();
    }

    public void stop() {
        scheduler.shutdownNow();
        NioTransport t = transport;
        if (t != null) {
            t.stop();
        }
    }

    private void sweepIdle() {
        long now = System.currentTimeMillis();
        long idleMillis = config.getIdleTimeout().toMillis();
        for (RmapConnection conn : connections) {
            // §3(в): закрытое соединение (в set из-за onClosed-до-onOpened гонки) не трогаем —
            // close по мёртвому объекту лишь плодил бы OTHER-кадры; удаляем из set.
            if (conn.isClosed()) {
                connections.remove(conn);
                continue;
            }
            HandshakeState hs = (HandshakeState) conn.getAttachment();
            if (hs != null && now - hs.lastInboundMillis() > idleMillis) {
                conn.close(OtherCode.TIMED_OUT, "idle timeout");
            }
        }
    }

    private final class Listener implements ConnectionListener {
        @Override
        public void onOpened(RmapConnection conn) {
            ensureHandshake(conn);
        }

        @Override
        public void onFrame(RmapConnection conn, Frame frame) {
            // §9: транспорт диспатчит кадры конкурентно и БЕЗ гарантии, что onFrame не опередит
            // onOpened — HELLO может прийти раньше, чем onOpened успел создать HandshakeState.
            // ensureHandshake атомарно и идемпотентно поднимает машину, поэтому кадр не теряется.
            HandshakeState hs = ensureHandshake(conn);
            hs.touchInbound();
            if (conn.isAuthenticated()) {
                if (frame.getType() == FrameType.PING) {
                    // PONG — побайтовое эхо timestamp (§4.4).
                    conn.send(new Frame(FrameType.PONG, 0L, frame.getPayload()));
                }
            } else {
                hs.onFrame(conn, frame);
            }
        }

        @Override
        public void onClosed(RmapConnection conn, Throwable cause) {
            connections.remove(conn);
        }
    }

    /**
     * Атомарно (под монитором соединения) создаёт per-connection {@link HandshakeState}, стартует
     * его и взводит handshake-timeout — ровно один раз. Вызывается и из onOpened, и из onFrame
     * (§9: кадр может опередить onOpened), поэтому обязана быть идемпотентной.
     */
    private HandshakeState ensureHandshake(RmapConnection conn) {
        HandshakeState existing = (HandshakeState) conn.getAttachment();
        if (existing != null) {
            return existing;
        }
        synchronized (conn) {
            HandshakeState hs = (HandshakeState) conn.getAttachment();
            if (hs != null) {
                return hs;
            }
            hs = new HandshakeState(conn,
                    () -> {
                        Consumer<RmapConnection> cb = onAuthenticatedCallback;
                        if (cb != null) {
                            cb.accept(conn);
                        }
                    },
                    ex -> { /* сервер future не имеет */ });
            // §5: server-side start() кадров не шлёт (только state=WAIT_HELLO) — стартуем машину
            // ДО публикации в attachment, иначе конкурентный onFrame по fast-path увидит state==null
            // и упадёт NPE, оборвав легитимного клиента.
            hs.start();
            conn.setAttachment(hs);
            connections.add(conn);
            // §3(б): onOpened и onClosed — независимые worker-задачи; при мгновенном RST onClosed
            // (connections.remove) мог исполниться ДО этого add → соединение осталось бы навсегда
            // в set, а sweepIdle бесконечно звал бы close по мёртвому объекту. Закрываем гонку.
            if (conn.isClosed()) {
                connections.remove(conn);
            }
            // handshake-timeout: закрыть не-аутентифицировавшееся соединение (§4.3).
            scheduler.schedule(() -> {
                if (!conn.isAuthenticated()) {
                    conn.close(OtherCode.TIMED_OUT, "handshake timeout");
                }
            }, config.getHandshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return hs;
        }
    }
}
