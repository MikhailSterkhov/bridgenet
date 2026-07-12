package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapConnectionException;
import me.moonways.rmap.api.RmapLogger;
import me.moonways.rmap.api.RmapLogging;
import me.moonways.rmap.api.RmapMetrics;
import me.moonways.rmap.api.RmapRemoteException;
import me.moonways.rmap.api.RmapStaleRefException;
import me.moonways.rmap.api.RmapTimeoutException;
import me.moonways.rmap.codec.RefContext;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.codec.RmapCodecException;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Состояние ОДНОГО аутентифицированного клиентского соединения (§7.2): корреляция вызовов,
 * lookup-кэш subjectId, deadline-таймеры. Создаётся при onAuthenticated и кладётся клиентом в
 * реестр по соединению; разрыв → новая сессия (новый {@link #generation}), кэш subjectId пуст,
 * LOOKUP повторяется лениво.
 *
 * <p><b>Remote-refs (§10).</b> Реализует клиентский {@link RefContext}: рефы клиент в v1 НЕ выдаёт
 * ({@link #remoteInterfaceFor} → null), а входящий {@code REMOTE_REF} превращает в ref-прокси
 * ({@link #proxyForRef}), привязанный к этой сессии (её {@link #generation}); GC-освобождение —
 * через {@link RefReleaser}, явное — через {@link #releaseRefs}.
 *
 * <p><b>Потоки.</b> DONE/OTHER декодируются строго последовательно на {@link #decodeSerial}
 * (wire-порядок class-интернирования §5.2a). Завершение future — на выделенном callback-пуле
 * (не на decode и не на scheduler-потоке): блокирующий continuation юзера не стопорит ни decode,
 * ни keep-alive (§9). LOOKUP_ACK не несёт TLV и обрабатывается прямо на worker-потоке.
 */
public final class ClientSession implements RefContext {

    private static final RmapLogger LOG = RmapLogging.get(ClientSession.class.getName());

    private final RmapClient client;
    private final RmapConnection conn;
    private final ConnectionCodec connCodec;
    private final RmapCodec codec;
    private final Executor callbackPool;
    private final SerialExecutor decodeSerial;
    private final ScheduledExecutorService scheduler;
    private final int generation;
    private final RmapMetrics metrics;
    private final RefReleaser refReleaser;
    private volatile ScheduledFuture<?> refReleaserTask;

    private final ConcurrentHashMap<Long, PendingCall> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Integer>> subjectIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LookupWait> pendingLookups = new ConcurrentHashMap<>();
    private final AtomicLong callIds = new AtomicLong(1L);
    private final AtomicLong droppedLate = new AtomicLong(0L);
    // отправка CANCEL идёт через это семя (дефолт — реальный кадр, тест инъектирует провал); см. sendCancel.
    private volatile CancelSender cancelSender;

    public ClientSession(RmapClient client, RmapConnection conn, ConnectionCodec connCodec, RmapCodec codec,
                         Executor callbackPool, Executor decodeExecutor,
                         ScheduledExecutorService scheduler, int generation, RmapMetrics metrics) {
        this.client = client;
        this.conn = conn;
        this.connCodec = connCodec;
        this.codec = codec;
        this.callbackPool = callbackPool;
        this.decodeSerial = new SerialExecutor(decodeExecutor);
        this.scheduler = scheduler;
        this.generation = generation;
        this.metrics = metrics != null ? metrics : RmapMetrics.NO_OP;
        this.cancelSender = callId -> conn.send(new Frame(FrameType.CANCEL, callId, EMPTY));
        this.refReleaser = new RefReleaser(this);
        // GC-триггерируемый REF_RELEASE (§10): дренаж очереди phantom'ов раз в 1с на общем scheduler'е.
        try {
            this.refReleaserTask = scheduler.scheduleAtFixedRate(refReleaser::tick, 1000L, 1000L,
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rej) {
            this.refReleaserTask = null; // scheduler остановлен (клиент закрывается)
        }
    }

    public RmapConnection connection() {
        return conn;
    }

    public ConnectionCodec connCodec() {
        return connCodec;
    }

    /** Номер сессии (инкремент на каждую новую аутентифицированную сессию) — для stale-ref (задача 5). */
    public int generation() {
        return generation;
    }

    /** Поздних/отброшенных ответов (метрика §7.2). */
    public long droppedLateResponses() {
        return droppedLate.get();
    }

    // ---- исходящий вызов (§7.1) --------------------------------------------------------------

    /**
     * Регистрирует вызов и (по резолву subjectId) отправляет RGET. Возвращает future немедленно
     * (sync-путь блокируется на нём в прокси, async-путь отдаёт юзеру). PendingCall регистрируется
     * ДО отправки; после — проверка {@code conn.isClosed()} закрывает гонку «закрылось между
     * регистрацией и send» (§4.4).
     */
    public CallFuture startCall(String path, long digest, Method method, long methodId,
                                Object[] args, long deadlineMillis) {
        long deadlineAt = System.currentTimeMillis() + Math.max(1L, deadlineMillis);
        long callId = callIds.getAndIncrement();
        CallFuture cf = new CallFuture(this, callId);
        PendingCall pc = new PendingCall(cf, method);
        pending.put(callId, pc);

        try {
            long delay = Math.max(1L, deadlineAt - System.currentTimeMillis());
            pc.timer = scheduler.schedule(() -> onDeadline(callId), delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rej) {
            pending.remove(callId);
            cf.completeExceptionally(new RmapConnectionException("client scheduler stopped"));
            return cf;
        }
        // закрыть гонку put↔timer: если failAllPending забрал запись между pending.put и присвоением
        // pc.timer, его cancelTimer видел timer==null (no-op) → таймер осиротел бы (самоистечёт, future
        // уже завершён — не hang, но утечка scheduler-задачи). Перечитываем: записи нет ⇒ снимаем сами.
        if (!pending.containsKey(callId)) {
            cancelTimer(pc);
            return cf; // future уже завершён failAllPending — RGET/LOOKUP не шлём
        }
        // fast-fail: соединение закрылось между live-check в прокси и регистрацией.
        if (conn.isClosed()) {
            PendingCall r = pending.remove(callId);
            if (r != null) {
                cancelTimer(r);
                cf.completeExceptionally(new RmapConnectionException("connection closed"));
            }
            return cf;
        }

        CompletableFuture<Integer> sf = resolveSubject(path, digest);
        sf.whenComplete((subjectId, err) -> {
            if (!pending.containsKey(callId)) {
                return; // вызов уже завершён (timeout/closed/cancel) — RGET не шлём
            }
            if (err != null) {
                PendingCall r = pending.remove(callId);
                if (r != null) {
                    cancelTimer(r);
                    completeLater(cf, null, err);
                }
                return;
            }
            int remaining = (int) Math.max(1L,
                    Math.min(deadlineAt - System.currentTimeMillis(), CallWire.MAX_DEADLINE_MILLIS));
            int argCount = args == null ? 0 : args.length;
            connCodec.encodeAndSend(conn, FrameType.RGET, callId, (out, ctx) -> {
                CallWire.encodeRgetHeader(out, subjectId, 0L, methodId, remaining, argCount);
                if (args != null) {
                    for (Object arg : args) {
                        codec.encode(out, arg, ctx);
                    }
                }
            });
            // fast-fail: закрылось между регистрацией и отправкой (send в закрытое молча дропнут).
            if (conn.isClosed()) {
                PendingCall r = pending.remove(callId);
                if (r != null) {
                    cancelTimer(r);
                    completeLater(cf, null, new RmapConnectionException("connection closed"));
                }
            }
        });
        return cf;
    }

    /** Резолв subjectId по path: кэш per-сессия; miss → один LOOKUP (interfaceDigest клиентского аудита). */
    private CompletableFuture<Integer> resolveSubject(String path, long digest) {
        CompletableFuture<Integer> existing = subjectIds.get(path);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Integer> created = new CompletableFuture<>();
        CompletableFuture<Integer> prev = subjectIds.putIfAbsent(path, created);
        if (prev != null) {
            return prev;
        }
        long lookupCallId = callIds.getAndIncrement();
        pendingLookups.put(lookupCallId, new LookupWait(path, created));
        try {
            connCodec.encodeAndSend(conn, FrameType.LOOKUP, lookupCallId,
                    (out, ctx) -> CallWire.encodeLookup(out, path, digest));
        } catch (RuntimeException ex) {
            pendingLookups.remove(lookupCallId);
            subjectIds.remove(path, created);
            created.completeExceptionally(new RmapConnectionException("lookup send failed", ex));
        }
        return created;
    }

    // ---- remote-ref вызов и release (§10) ----------------------------------------------------

    /**
     * Исходящий вызов по remote-ref: ref-форма RGET ({@code subjectId=-1, refId}) без LOOKUP/digest
     * (§10.1). Регистрация pending + deadline-таймер идентичны {@link #startCall}. Generation-гейт
     * (мёртв после reconnect) проверяется в {@link RmapProxy} ДО вызова — здесь сессия считается живой.
     */
    public CallFuture startRefCall(long refId, Method method, long methodId, Object[] args, long deadlineMillis) {
        long deadlineAt = System.currentTimeMillis() + Math.max(1L, deadlineMillis);
        long callId = callIds.getAndIncrement();
        CallFuture cf = new CallFuture(this, callId);
        PendingCall pc = new PendingCall(cf, method);
        pending.put(callId, pc);

        try {
            long delay = Math.max(1L, deadlineAt - System.currentTimeMillis());
            pc.timer = scheduler.schedule(() -> onDeadline(callId), delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rej) {
            pending.remove(callId);
            cf.completeExceptionally(new RmapConnectionException("client scheduler stopped"));
            return cf;
        }
        if (!pending.containsKey(callId)) {
            cancelTimer(pc);
            return cf; // future уже завершён failAllPending
        }
        if (conn.isClosed()) {
            PendingCall r = pending.remove(callId);
            if (r != null) {
                cancelTimer(r);
                cf.completeExceptionally(new RmapConnectionException("connection closed"));
            }
            return cf;
        }

        int remaining = (int) Math.max(1L,
                Math.min(deadlineAt - System.currentTimeMillis(), CallWire.MAX_DEADLINE_MILLIS));
        int argCount = args == null ? 0 : args.length;
        connCodec.encodeAndSend(conn, FrameType.RGET, callId, (out, ctx) -> {
            CallWire.encodeRgetHeader(out, -1, refId, methodId, remaining, argCount); // subjectId=-1 → ref-форма
            if (args != null) {
                for (Object arg : args) {
                    codec.encode(out, arg, ctx);
                }
            }
        });
        if (conn.isClosed()) {
            PendingCall r = pending.remove(callId);
            if (r != null) {
                cancelTimer(r);
                completeLater(cf, null, new RmapConnectionException("connection closed"));
            }
        }
        return cf;
    }

    /** Отправка {@code REF_RELEASE} (client→server, callId=0, без ответа §4.2). Best-effort:
     *  провал send (закрытие/лимит) проглатывается — серверный lease/разрыв уберут ref сами. */
    public void releaseRefs(long[] refIds) {
        if (refIds == null || refIds.length == 0) {
            return;
        }
        try {
            connCodec.encodeAndSend(conn, FrameType.REF_RELEASE, 0L,
                    (out, ctx) -> CallWire.encodeRefRelease(out, refIds));
        } catch (RuntimeException ignored) {
            // best-effort: соединение закрыто/лимит — сервер очистит ref lease'ом либо разрывом
        }
    }

    // ---- RefContext SPI (клиентская сторона, §10) --------------------------------------------

    /** Клиент в v1 рефы НЕ выдаёт: значение всегда кодируется по своим правилам, не как REMOTE_REF. */
    @Override
    public Class<?> remoteInterfaceFor(Object value) {
        return null;
    }

    @Override
    public long registerRef(Object value, Class<?> iface) {
        throw new RmapCodecException("client does not register remote refs in v1");
    }

    /** Входящий {@code REMOTE_REF} → JDK-прокси ref-режима, привязанный к (client, refId, generation).
     *  Регистрируется в {@link RefReleaser} для GC-триггерируемого REF_RELEASE. */
    @Override
    public Object proxyForRef(long refId, Class<?> iface) {
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                RmapProxy.forRef(client, iface, refId, generation));
        refReleaser.register(proxy, refId);
        return proxy;
    }

    // ---- входящие кадры (роутинг из RmapClient) ----------------------------------------------

    /** LOOKUP_ACK (§4.2): без TLV → прямо на worker-потоке. Отрицательный subjectId → PROTOCOL_ERROR. */
    public void onLookupAck(Frame frame) {
        long callId = frame.getCallId();
        int subjectId;
        try {
            subjectId = CallWire.decodeLookupAck(reader(frame));
        } catch (RuntimeException ex) {
            return; // малформ ACK — дроп
        }
        LookupWait lw = pendingLookups.remove(callId);
        if (lw == null) {
            return; // спурьёзный/поздний ACK
        }
        if (subjectId < 0) {
            subjectIds.remove(lw.path, lw.future);
            conn.close(OtherCode.PROTOCOL_ERROR, "negative subjectId in LOOKUP_ACK: " + subjectId);
            completeLater(lw.future, null, new RmapConnectionException("protocol error: negative subjectId"));
            return;
        }
        // inline: триггерит startCall.whenComplete → отправку RGET (non-blocking) прямо здесь.
        lw.future.complete(subjectId);
    }

    /** DONE (§7.2): decode на serial; поздний/отменённый (pending==null) — молча со счётчиком. */
    public void onDone(Frame frame) {
        decodeSerial.execute(() -> {
            long callId = frame.getCallId();
            Object value;
            try {
                value = codec.decode(reader(frame), connCodec.readCtx());
            } catch (RuntimeException ex) {
                return; // малформ DONE — вызов добьёт deadline-таймер
            }
            PendingCall pc = pending.remove(callId);
            if (pc == null) {
                droppedLate.incrementAndGet();
                metrics.lateAnswerDropped();
                return;
            }
            cancelTimer(pc);
            completeLater(pc.future, value, null);
        });
    }

    /** OTHER (§7.3): callId==0 → connection-level close; иначе lookup-fail либо call-fail по коду. */
    public void onOther(Frame frame) {
        decodeSerial.execute(() -> {
            long callId = frame.getCallId();
            CallWire.Other other;
            try {
                other = CallWire.decodeOther(reader(frame), codec, connCodec.readCtx());
            } catch (RuntimeException ex) {
                return; // малформ OTHER — дроп
            }
            LOG.debug("received OTHER(" + me.moonways.rmap.wire.OtherCode.name(other.getCode())
                    + ") callId=" + callId + ": " + other.getMessage());
            if (callId == 0L) {
                conn.close(); // connection-level OTHER post-auth: рвём соединение (reconnect несёт причину)
                return;
            }
            LookupWait lw = pendingLookups.remove(callId);
            if (lw != null) {
                subjectIds.remove(lw.path, lw.future);
                completeLater(lw.future, null, mapOther(other));
                return;
            }
            PendingCall pc = pending.remove(callId);
            if (pc == null) {
                droppedLate.incrementAndGet();
                metrics.lateAnswerDropped();
                return;
            }
            cancelTimer(pc);
            completeLater(pc.future, null, mapOther(other));
        });
    }

    // ---- завершение вызовов ------------------------------------------------------------------

    /** Deadline истёк (§7.1): снять pending, завершить future таймаутом, затем best-effort CANCEL. */
    private void onDeadline(long callId) {
        PendingCall pc = pending.remove(callId);
        if (pc == null) {
            return; // уже завершён (DONE/OTHER/closed/cancel)
        }
        // ПОРЯДОК КРИТИЧЕН: сначала завершаем future, ПОТОМ шлём CANCEL. Иначе провал send под
        // outbound-backpressure (RmapTransportException, §RmapConnection.send) прошёл бы ДО
        // completeLater → future не завершён, callId уже снят из pending (failAllPending не спасёт),
        // sync-вызывающий на cf.get() (без таймаута) виснет НАВСЕГДА. sendCancel — best-effort.
        completeLater(pc.future, null, new RmapTimeoutException("call timed out after deadline"));
        sendCancel(callId); // §4.2a: CANCEL несёт callId отменяемого вызова
    }

    /**
     * Пользователь отменил async-future ({@code cancel(true)}): снять pending+таймер, best-effort CANCEL.
     * Future УЖЕ отменён (super.cancel в {@link CallFuture#cancel} до этого вызова) — порядок «future
     * завершён ПЕРЕД CANCEL» соблюдён; провал sendCancel не должен пробиться в {@code future.cancel(true)}.
     */
    void onUserCancel(long callId) {
        PendingCall pc = pending.remove(callId);
        if (pc != null) {
            cancelTimer(pc);
            sendCancel(callId);
        }
    }

    /** Разрыв соединения (§4.4/§7.2): ВСЕ pending + pending-lookups fail-fast, таймеры сняты,
     *  тик ref-releaser'а снят (рефы этой сессии мертвы — §10). */
    public void failAllPending(Throwable cause) {
        ScheduledFuture<?> rt = refReleaserTask;
        if (rt != null) {
            rt.cancel(false);
        }
        for (Long callId : pending.keySet()) {
            PendingCall pc = pending.remove(callId);
            if (pc != null) {
                cancelTimer(pc);
                completeLater(pc.future, null, cause);
            }
        }
        for (Long callId : pendingLookups.keySet()) {
            LookupWait lw = pendingLookups.remove(callId);
            if (lw != null) {
                subjectIds.remove(lw.path, lw.future);
                completeLater(lw.future, null, cause);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private RuntimeException mapOther(CallWire.Other other) {
        String msg = safe(other.getMessage());
        switch (other.getCode()) {
            case OtherCode.TIMED_OUT:
                return new RmapTimeoutException(msg.isEmpty() ? "remote timed out" : msg);
            case OtherCode.STALE_REF:
                return new RmapStaleRefException(msg.isEmpty() ? "stale ref" : msg);
            case OtherCode.INTERNAL_ERROR:
                if (other.getException() != null) {
                    return new RmapRemoteException(other.getException());
                }
                return new RmapRemoteException("code=INTERNAL_ERROR: " + msg);
            default:
                return new RmapRemoteException("code=" + OtherCode.name(other.getCode()) + ": " + msg);
        }
    }

    private <X> void completeLater(CompletableFuture<X> future, X value, Throwable error) {
        Runnable task = () -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(value);
            }
        };
        try {
            callbackPool.execute(task);
        } catch (RejectedExecutionException rej) {
            task.run(); // callback-пул остановлен (клиент закрывается) — inline best-effort
        }
    }

    /**
     * Отправка CANCEL — <b>best-effort</b> уведомление сервера (§4.2a). Его провал (outbound-лимит →
     * RmapTransportException, закрытие → молчаливый дроп) НЕ должен пробиться ни в scheduler-задачу
     * {@link #onDeadline}, ни в пользовательский {@code future.cancel(true)}: вызов к этому моменту уже
     * завершён локально таймаутом/отменой. Идёт через {@link #cancelSender}-семя (тест инъектирует провал).
     */
    private void sendCancel(long callId) {
        try {
            cancelSender.send(callId);
        } catch (RuntimeException swallowed) {
            // best-effort: провал CANCEL (outbound-лимит → RmapTransportException) не должен пробиться
            // ни в scheduler-задачу onDeadline, ни в пользовательский future.cancel(true). Вызов уже
            // завершён локально таймаутом/отменой; сервер снимет вызов сам по deadline либо разрыву.
        }
    }

    // Семя отправки CANCEL: дефолт — реальный кадр без TLV (закрытое соединение молча дропнет);
    // package-private сеттер позволяет юнит-тесту детерминированно смоделировать провал send().
    interface CancelSender {
        void send(long callId);
    }

    void setCancelSender(CancelSender sender) {
        this.cancelSender = sender;
    }

    private static void cancelTimer(PendingCall pc) {
        ScheduledFuture<?> t = pc.timer;
        if (t != null) {
            t.cancel(false);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static RmapByteReader reader(Frame frame) {
        byte[] p = frame.getPayload();
        return new RmapByteReader(p, 0, p.length);
    }

    private static final byte[] EMPTY = new byte[0];

    /** Future клиентского вызова: {@code cancel(true)} шлёт серверу CANCEL и снимает pending (§7.1). */
    public static final class CallFuture extends CompletableFuture<Object> {
        private final ClientSession session;
        private final long callId;

        CallFuture(ClientSession session, long callId) {
            this.session = session;
            this.callId = callId;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                session.onUserCancel(callId);
            }
            return cancelled;
        }
    }

    private static final class PendingCall {
        final CallFuture future;
        final Method method; // задел (§7.2/задача 5); на клиенте DONE несёт уже распакованное значение
        volatile ScheduledFuture<?> timer;

        PendingCall(CallFuture future, Method method) {
            this.future = future;
            this.method = method;
        }
    }

    private static final class LookupWait {
        final String path;
        final CompletableFuture<Integer> future;

        LookupWait(String path, CompletableFuture<Integer> future) {
            this.path = path;
            this.future = future;
        }
    }
}
