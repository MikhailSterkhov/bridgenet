package me.moonways.rmap.api;

import me.moonways.rmap.codec.CodecRegistry;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.rpc.ClientSession;
import me.moonways.rmap.rpc.ConnectionCodec;
import me.moonways.rmap.rpc.ExportAudit;
import me.moonways.rmap.rpc.ExportOptions;
import me.moonways.rmap.rpc.InterfaceManifest;
import me.moonways.rmap.rpc.RmapCallOptions;
import me.moonways.rmap.rpc.RmapProxy;
import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeCodec;
import me.moonways.rmap.transport.HandshakeState;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.transport.RmapTransportException;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;

import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    /** Завершение клиентских future — НЕ на decode/scheduler-потоке (§9): блокирующий continuation
     *  юзера не стопорит ни serial-decode, ни keep-alive. */
    private final ExecutorService callbackPool;
    /** Делегат serial-decode DONE/OTHER (wire-порядок §5.2a); отдельный от callback-пула. */
    private final ExecutorService decodeExecutor;
    private final CodecRegistry codecRegistry = new CodecRegistry();
    private final RmapCodec codec = new RmapCodec(codecRegistry);
    /** Сессии по соединению; per-authenticated-connection (§7.2). */
    private final Map<RmapConnection, ClientSession> sessions = new ConcurrentHashMap<>();
    /** lookup-спеки (path → digest+whitelist аудита), переживают reconnect для сидирования новой сессии. */
    private final Map<String, LookupSpec> registeredLookups = new ConcurrentHashMap<>();
    private final AtomicInteger generationSeq = new AtomicInteger(0);
    /** Текущая живая сессия для диспетчеризации прокси; обновляется на каждую новую аутентификацию. */
    private volatile ClientSession session;

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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("rmap-client-sched"));
        this.callbackPool = Executors.newFixedThreadPool(2, daemonFactory("rmap-client-callback"));
        this.decodeExecutor = Executors.newSingleThreadExecutor(daemonFactory("rmap-client-decode"));
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
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

    /**
     * JDK-прокси remote-интерфейса НЕМЕДЛЕННО (§7.1). Клиентский аудит {@link ExportAudit.Mode#CLIENT}
     * валидирует параметры и собирает whitelist+digest (интерфейсы в позиции возврата не отвергаются —
     * клиент не знает wrap-набор сервера). LOOKUP уходит лениво при первом вызове; subjectId кэшируется
     * на сессию, после reconnect (новая сессия) LOOKUP повторяется.
     */
    public <T> T lookup(String path, Class<T> iface) {
        InterfaceManifest manifest = ExportAudit.audit(iface, ExportOptions.defaults(), codecRegistry,
                ExportAudit.Mode.CLIENT);
        LookupSpec spec = new LookupSpec(manifest.getDigest(), manifest.getDecodeWhitelist());
        registeredLookups.put(path, spec);
        ClientSession s = session; // если сессия уже жива — расширяем её decode-whitelist немедленно
        if (s != null) {
            s.connCodec().addWhitelist(spec.whitelist);
        }
        RmapProxy handler = new RmapProxy(this, path, iface, manifest.getDigest(), null);
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
        return iface.cast(proxy);
    }

    /** View-прокси с переопределённым per-call deadline (§7.1): тот же handler-контракт, другой дефолт. */
    @SuppressWarnings("unchecked")
    public <T> T withOptions(T proxy, RmapCallOptions opts) {
        RmapProxy handler = (RmapProxy) Proxy.getInvocationHandler(proxy);
        RmapProxy view = handler.viewWith(opts);
        Class<?> iface = handler.iface();
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, view);
    }

    /** Живая аутентифицированная сессия для диспетчеризации прокси, либо {@code null}. */
    public ClientSession liveSession() {
        ClientSession s = session;
        if (s == null) {
            return null;
        }
        RmapConnection c = s.connection();
        if (c.isClosed() || !c.isAuthenticated()) {
            return null;
        }
        return s;
    }

    /** Дефолтный call-deadline в миллисекундах (§7.1, {@code RmapConfig.callTimeout}). */
    public long callTimeoutMillis() {
        return config.getCallTimeout().toMillis();
    }

    /**
     * Атомарно и идемпотентно поднимает {@link ClientSession} для аутентифицированного соединения
     * (§9: post-auth кадр может опередить onAuthenticated-коллбек). Сидирует decode-whitelist сессии
     * объединением всех известных lookup-спеков; новая сессия — новый {@code generation}.
     */
    private ClientSession ensureSession(RmapConnection conn) {
        ClientSession existing = sessions.get(conn);
        if (existing != null) {
            return existing;
        }
        synchronized (conn) {
            ClientSession s = sessions.get(conn);
            if (s != null) {
                return s;
            }
            Set<String> union = new LinkedHashSet<>();
            for (LookupSpec spec : registeredLookups.values()) {
                union.addAll(spec.whitelist);
            }
            ConnectionCodec connCodec = new ConnectionCodec(codec, union);
            s = new ClientSession(this, conn, connCodec, codec, callbackPool, decodeExecutor, scheduler,
                    generationSeq.incrementAndGet());
            connCodec.setRefContext(s); // клиентский RefContext (§10): REMOTE_REF → ref-прокси
            sessions.put(conn, s);
            this.session = s;
            // §3(б)-аналог: соединение уже закрыто (onClosed опередил) — снять и fail-fast.
            if (conn.isClosed()) {
                sessions.remove(conn);
                if (this.session == s) {
                    this.session = null;
                }
                s.failAllPending(new RmapConnectionException("connection closed"));
            }
            return s;
        }
    }

    /** lookup-спека: digest+decode-whitelist аудита; переживает reconnect для сидирования новой сессии. */
    private static final class LookupSpec {
        final long digest;
        final Set<String> whitelist;

        LookupSpec(long digest, Set<String> whitelist) {
            this.digest = digest;
            this.whitelist = whitelist;
        }
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
        // call-слой: остановить decode/callback-пулы (pending уже fail-fast'ятся на onClosed).
        decodeExecutor.shutdownNow();
        callbackPool.shutdownNow();
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
                        ensureSession(conn);                   // call-слой готов ДО завершения connect-future
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
                // §9: первый post-auth кадр может опередить onAuthenticated-коллбек — поднимаем
                // сессию идемпотентно, чтобы DONE/OTHER/LOOKUP_ACK не потерялись.
                ClientSession s = ensureSession(conn);
                switch (frame.getType()) {
                    case PONG: {
                        Runnable cb = onPongCallback;
                        if (cb != null) {
                            cb.run();
                        }
                        break;
                    }
                    case PING:
                        conn.send(new Frame(FrameType.PONG, 0L, frame.getPayload()));
                        break;
                    case LOOKUP_ACK:
                        s.onLookupAck(frame); // без TLV → прямо на worker-потоке
                        break;
                    case DONE:
                        s.onDone(frame);      // serial-decode
                        break;
                    case OTHER:
                        s.onOther(frame);     // serial-decode
                        break;
                    default:
                        break;                // прочие post-auth кадры игнорируем
                }
            } else if (hs != null) {
                hs.onFrame(conn, frame);
            }
        }

        @Override
        public void onClosed(RmapConnection conn, Throwable cause) {
            // §7.2 fast-fail: ВСЕ pending этой сессии немедленно completeExceptionally + снятие таймеров.
            ClientSession s = sessions.remove(conn);
            if (s != null) {
                s.failAllPending(new RmapConnectionException("connection closed", cause));
                if (session == s) {
                    session = null;
                }
            }
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
