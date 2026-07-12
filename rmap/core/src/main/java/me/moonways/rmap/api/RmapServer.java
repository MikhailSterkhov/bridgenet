package me.moonways.rmap.api;

import me.moonways.rmap.codec.CodecRegistry;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.rpc.ConnectionCodec;
import me.moonways.rmap.rpc.ExportAudit;
import me.moonways.rmap.rpc.ExportOptions;
import me.moonways.rmap.rpc.InterfaceManifest;
import me.moonways.rmap.rpc.RmapAgent;
import me.moonways.rmap.rpc.SerialExecutor;
import me.moonways.rmap.rpc.SubjectRegistry;
import me.moonways.rmap.transport.ConnectionListener;
import me.moonways.rmap.transport.HandshakeState;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.OtherCode;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Серверный эндпоинт RMAP: слушает сокет, гонит каждое входящее соединение через взаимный
 * HMAC-handshake (§4.3) и после аутентификации обслуживает call-слой (§7): LOOKUP/RGET/DONE/
 * OTHER/CANCEL через per-connection {@link RmapAgent}, keep-alive PONG на PING (§4.4).
 *
 * <p>Один фиксированный invoke-пул {@code max(2, cores)} (daemon) на сервер обслуживает decode+
 * invoke+encode всех агентов (worker-pool транспорта им не используется — §9); закрывается в
 * {@link #stop()}.
 */
public final class RmapServer {

    private final RmapConfig config;
    private final ScheduledExecutorService scheduler;
    /** §9: invoke-пул (один на сервер) — decode/invoke/encode агентов; НЕ worker-pool транспорта. */
    private final ExecutorService invokePool;
    private final Set<RmapConnection> connections = ConcurrentHashMap.newKeySet();
    private final Map<RmapConnection, RmapAgent> agents = new ConcurrentHashMap<>();
    /** per-subject serial-dispatch executors (server-global; общий invoke-пул). */
    private final Map<Integer, SerialExecutor> subjectSerial = new ConcurrentHashMap<>();
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();
    private final SubjectRegistry registry = new SubjectRegistry();
    private final CodecRegistry codecRegistry = new CodecRegistry();
    private final RmapCodec codec = new RmapCodec(codecRegistry);

    private volatile InetSocketAddress bindAddress;
    private volatile NioTransport transport;
    private volatile Consumer<RmapConnection> onAuthenticatedCallback;
    private volatile boolean started;
    /** decode-whitelist соединения (§5.1) = объединение манифестов всех экспортов; готов на start(). */
    private volatile Set<String> unionWhitelist = Collections.emptySet();
    /** Порог lease remote-ref'ов (§10, дефолт 10 мин). Тест/интроспекция — {@link #setRefLeaseTimeoutMillis};
     *  публичный конфиг (RmapConfig.refLeaseTimeout) — задача 6. */
    private volatile long refLeaseTimeoutMillis = 10L * 60_000L;

    RmapServer(RmapConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("rmap-server-sched"));
        int invokeThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.invokePool = Executors.newFixedThreadPool(invokeThreads, daemonFactory("rmap-invoke"));
    }

    public void bind(InetSocketAddress address) {
        this.bindAddress = address;
    }

    public void onAuthenticated(Consumer<RmapConnection> callback) {
        this.onAuthenticatedCallback = callback;
    }

    /** Топик — пространство имён экспортов; идемпотентен (§11). */
    public Topic put(String topic) {
        return topics.computeIfAbsent(topic, name -> new Topic(name));
    }

    public void start() {
        if (bindAddress == null) {
            throw new IllegalStateException("bind(address) must be called before start()");
        }
        // union-whitelist decode всех манифестов (§5.1); готовим ДО приёма соединений.
        Set<String> union = new LinkedHashSet<>();
        for (SubjectRegistry.Subject subject : registry.all()) {
            union.addAll(subject.getManifest().getDecodeWhitelist());
        }
        this.unionWhitelist = Collections.unmodifiableSet(union);
        this.started = true; // export после start() → IllegalStateException
        this.transport = NioTransport.startServer(bindAddress, config, new Listener());
        // idle-sweep: закрыть соединения без входящего трафика дольше idleTimeout (§4.4).
        long idleMillis = config.getIdleTimeout().toMillis();
        scheduler.scheduleAtFixedRate(this::sweepIdle, idleMillis, idleMillis, TimeUnit.MILLISECONDS);
        // ref-lease-sweep: раз в минуту проактивно эвиктим протухшие remote-ref'ы (§10).
        scheduler.scheduleAtFixedRate(this::sweepRefs, 60_000L, 60_000L, TimeUnit.MILLISECONDS);
    }

    public int boundPort() {
        NioTransport t = transport;
        return t == null ? -1 : t.boundPort();
    }

    public void stop() {
        scheduler.shutdownNow();
        invokePool.shutdownNow();
        NioTransport t = transport;
        if (t != null) {
            t.stop();
        }
    }

    /** Пространство имён экспортов (§8, §11). Все {@code export} — ДО {@link #start()}. */
    public final class Topic {

        private final String topic;

        Topic(String topic) {
            this.topic = topic;
        }

        public <T> void export(String name, Class<T> iface, T impl) {
            export(name, iface, impl, ExportOptions.defaults());
        }

        public <T> void export(String name, Class<T> iface, T impl, ExportOptions opts) {
            if (started) {
                throw new IllegalStateException("export after start() is not allowed: " + topic + "/" + name);
            }
            String path = topic + "/" + name;
            // §8: export-time audit НЕМЕДЛЕННО — RmapExportException до старта.
            InterfaceManifest manifest = ExportAudit.audit(iface, opts, codecRegistry);
            registry.register(path, iface, impl, manifest, opts); // повторный path → RmapExportException
        }
    }

    /** Порог lease remote-ref'ов для новых соединений (§10). Тест/интроспекция; конфиг — задача 6. */
    public void setRefLeaseTimeoutMillis(long millis) {
        this.refLeaseTimeoutMillis = millis;
    }

    /** Живые per-connection агенты (интроспекция для тестов/метрик; SPI RmapMetrics — задача 6). */
    public java.util.Collection<RmapAgent> activeAgents() {
        return agents.values();
    }

    private void sweepRefs() {
        for (RmapAgent agent : agents.values()) {
            agent.sweepExpiredRefs();
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
                // post-auth: call-слой. ensureAgent идемпотентен — первый RGET может опередить
                // завершение onAuthenticated-коллбека (§9-конкурентность).
                ensureAgent(conn).onFrame(frame);
            } else {
                hs.onFrame(conn, frame);
            }
        }

        @Override
        public void onClosed(RmapConnection conn, Throwable cause) {
            connections.remove(conn);
            RmapAgent agent = agents.remove(conn);
            if (agent != null) {
                agent.onClosed(); // §2(б): чистим queued (утечка QueuedCall при разрыве до invoke)
            }
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

    /**
     * Атомарно и идемпотентно поднимает per-connection {@link RmapAgent} с собственным
     * {@link ConnectionCodec} (union-whitelist сервера). Первый post-auth кадр может опередить
     * завершение onAuthenticated-коллбека (§9), поэтому создаётся лениво под монитором соединения.
     */
    private RmapAgent ensureAgent(RmapConnection conn) {
        RmapAgent existing = agents.get(conn);
        if (existing != null) {
            return existing;
        }
        synchronized (conn) {
            RmapAgent agent = agents.get(conn);
            if (agent != null) {
                return agent;
            }
            ConnectionCodec connCodec = new ConnectionCodec(codec, unionWhitelist);
            agent = new RmapAgent(conn, connCodec, codec, codecRegistry, registry, invokePool, subjectSerial,
                    transport, config, refLeaseTimeoutMillis);
            agents.put(conn, agent);
            // §3(б)-аналог: если соединение уже закрыто, снимаем — onClosed мог пройти до put.
            if (conn.isClosed()) {
                agents.remove(conn);
                agent.onClosed();
            }
            return agent;
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
