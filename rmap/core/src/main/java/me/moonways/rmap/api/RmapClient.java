package me.moonways.rmap.api;

import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeCodec;
import me.moonways.rmap.transport.HandshakeState;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.transport.RmapTransportException;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Клиентский эндпоинт RMAP: устанавливает соединение, проходит взаимный HMAC-handshake (§4.3),
 * шлёт keep-alive PING и переустанавливает УСТАНОВЛЕННУЮ ранее сессию с экспоненциальным
 * backoff (§4.4). Провал handshake или явный close() reconnect'ом НЕ сопровождаются.
 */
public final class RmapClient {

    private static final long RECONNECT_BASE_MILLIS = 1000L;
    private static final long RECONNECT_CAP_MILLIS = 30_000L;
    private static final long ONCLOSE_GRACE_MILLIS = 2000L;

    private final RmapConfig config;
    private final ScheduledExecutorService scheduler;

    private volatile NioTransport transport;
    private volatile RmapConnection current;
    private volatile CompletableFuture<Void> connectFuture;
    private volatile String host;
    private volatile int port;
    private volatile boolean userClosed;
    /** Хоть раз дошли до AUTH_OK. Дискриминатор §4.4: только УСТАНОВЛЕННУЮ ранее сессию
     *  переустанавливаем реконнектом; провал ПЕРВОГО connect — без reconnect (fail future). */
    private volatile boolean everAuthenticated;
    private volatile long backoffMillis = RECONNECT_BASE_MILLIS;

    private volatile Runnable onAuthenticatedCallback;
    private volatile Runnable onPongCallback;

    RmapClient(RmapConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rmap-client-sched");
            t.setDaemon(true);
            return t;
        });
    }

    public void onAuthenticated(Runnable callback) {
        this.onAuthenticatedCallback = callback;
    }

    public void onPong(Runnable callback) {
        this.onPongCallback = callback;
    }

    /** Устанавливает соединение; future завершается после AUTH_OK. */
    public CompletableFuture<Void> connect(String host, int port) {
        this.host = host;
        this.port = port;
        this.connectFuture = new CompletableFuture<>();
        if (transport == null) {
            transport = NioTransport.clientTransport(config);
        }
        CompletableFuture<Void> f = connectFuture;
        // keep-alive PING + idle-close (§4.4) — один планировщик на весь жизненный цикл клиента.
        long keepAlive = config.getKeepAliveInterval().toMillis();
        scheduler.scheduleAtFixedRate(this::keepAliveTick, keepAlive, keepAlive, TimeUnit.MILLISECONDS);
        doConnect();
        return f;
    }

    private void doConnect() {
        if (userClosed) {
            return; // scheduled-попытка после close() — no-op
        }
        try {
            RmapConnection c = transport.connect(host, port, config, new Listener());
            current = c;
            scheduleHandshakeTimeout(c);
        } catch (RuntimeException e) {
            // §4: transport.connect может бросить СИНХРОННО (UnresolvedAddressException — мимо
            // IOException-обёртки; RmapTransportException при fd-давлении). Исключение из scheduled-
            // задачи ScheduledExecutorService проглатывает → onClosed не будет (соединение не
            // создано) → без этого reconnect больше НИКОГДА не планируется. Восстанавливаем цикл.
            if (userClosed) {
                return;
            }
            if (everAuthenticated) {
                scheduleReconnect(); // реконнект установленной сессии — backoff-цепочка продолжается
            } else {
                CompletableFuture<Void> f = connectFuture; // первый connect — фейлим future
                if (f != null && !f.isDone()) {
                    f.completeExceptionally(e);
                }
            }
        }
    }

    /** Per-attempt handshake-timeout (§4.3): фейлит ПЕРВЫЙ connect-future и рвёт протухший сокет. */
    private void scheduleHandshakeTimeout(RmapConnection c) {
        try {
            scheduler.schedule(() -> {
                if (userClosed || c.isAuthenticated()) {
                    return;
                }
                // handshake не дошёл до AUTH_OK за отведённое время.
                CompletableFuture<Void> f = connectFuture;
                if (f != null && !f.isDone()) {
                    f.completeExceptionally(new RmapTransportException("TIMED_OUT: handshake timeout"));
                }
                c.close(); // §4: закрыть сокет → onClosed → (re)connect по правилам §4.4
            }, config.getHandshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // scheduler остановлен (close())
        }
    }

    public boolean isAuthenticated() {
        RmapConnection c = current;
        return c != null && c.isAuthenticated();
    }

    public void sendPing() {
        RmapConnection c = current;
        if (c != null && c.isAuthenticated()) {
            c.send(new Frame(FrameType.PING, 0L, HandshakeCodec.encodeTimestamp(System.currentTimeMillis())));
        }
    }

    public void close() {
        userClosed = true;
        scheduler.shutdownNow();
        RmapConnection c = current;
        if (c != null) {
            c.close();
        }
        NioTransport t = transport;
        if (t != null) {
            t.stop();
        }
    }

    private void keepAliveTick() {
        RmapConnection c = current;
        HandshakeState hs = c == null ? null : (HandshakeState) c.getAttachment();
        if (c == null || hs == null || !c.isAuthenticated()) {
            return;
        }
        long idle = System.currentTimeMillis() - hs.lastInboundMillis();
        if (idle > config.getIdleTimeout().toMillis()) {
            c.close(); // обрыв установленной сессии → reconnect через onClosed
        } else if (idle >= config.getKeepAliveInterval().toMillis()) {
            sendPing();
        }
    }

    private void scheduleReconnect() {
        if (userClosed) {
            return;
        }
        long delay = backoffMillis;
        backoffMillis = Math.min(backoffMillis * 2, RECONNECT_CAP_MILLIS);
        try {
            scheduler.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // scheduler остановлен (close()) — reconnect не нужен
        }
    }

    private final class Listener implements ConnectionListener {
        @Override
        public void onOpened(RmapConnection conn) {
            HandshakeState hs = new HandshakeState(conn,
                    () -> {
                        everAuthenticated = true;              // сессия установлена (§4.4)
                        backoffMillis = RECONNECT_BASE_MILLIS; // успех → сброс backoff
                        CompletableFuture<Void> f = connectFuture;
                        if (f != null) {
                            f.complete(null); // no-op если уже завершён (реконнект)
                        }
                        Runnable cb = onAuthenticatedCallback;
                        if (cb != null) {
                            cb.run();
                        }
                    },
                    ex -> {
                        CompletableFuture<Void> f = connectFuture;
                        if (f != null) {
                            f.completeExceptionally(ex);
                        }
                    },
                    // §4: попытка брошена, если ПЕРВЫЙ connect-future уже зафейлен (handshake-timeout).
                    // При реконнекте future завершён УСПЕШНО → isCompletedExceptionally()==false → принимаем.
                    () -> {
                        CompletableFuture<Void> f = connectFuture;
                        return f != null && f.isCompletedExceptionally();
                    });
            conn.setAttachment(hs);
            hs.start(); // клиент шлёт HELLO
        }

        @Override
        public void onFrame(RmapConnection conn, Frame frame) {
            HandshakeState hs = (HandshakeState) conn.getAttachment();
            if (hs != null) {
                hs.touchInbound();
            }
            if (conn.isAuthenticated()) {
                if (frame.getType() == FrameType.PONG) {
                    Runnable cb = onPongCallback;
                    if (cb != null) {
                        cb.run();
                    }
                } else if (frame.getType() == FrameType.PING) {
                    conn.send(new Frame(FrameType.PONG, 0L, frame.getPayload()));
                }
            } else if (hs != null) {
                hs.onFrame(conn, frame);
            }
        }

        @Override
        public void onClosed(RmapConnection conn, Throwable cause) {
            if (userClosed) {
                return;
            }
            if (everAuthenticated) {
                // Сессия хоть раз была установлена → держим соединение живым реконнектом (§4.4).
                // Покрывает и обрыв установленной сессии (conn.isAuthenticated()), и провал
                // ПОПЫТКИ реконнекта до auth (conn ещё не authenticated) — обе перевзводят цикл.
                scheduleReconnect();
                return;
            }
            // ПЕРВЫЙ connect ещё не аутентифицировался — reconnect НЕ выполняем (§4.4).
            // handshake-фаза: предпочесть ошибку, произведённую самим handshake (напр. из OTHER-кадра).
            // §9: onClosed может гоняться с in-flight onFrame(OTHER) — даём хендшейку зафейлить future.
            CompletableFuture<Void> f = connectFuture;
            if (f != null && !f.isDone()) {
                long deadline = System.currentTimeMillis() + ONCLOSE_GRACE_MILLIS;
                while (!f.isDone() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!f.isDone()) {
                    f.completeExceptionally(cause != null ? cause
                            : new RmapTransportException("connection closed during handshake"));
                }
            }
            // handshake провалился — reconnect НЕ выполняем (§4.4, резолюция 2).
        }
    }
}
