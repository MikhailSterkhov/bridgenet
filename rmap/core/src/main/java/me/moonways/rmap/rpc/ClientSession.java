package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapConnectionException;
import me.moonways.rmap.api.RmapRemoteException;
import me.moonways.rmap.api.RmapStaleRefException;
import me.moonways.rmap.api.RmapTimeoutException;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.lang.reflect.Method;
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
 * <p><b>Потоки.</b> DONE/OTHER декодируются строго последовательно на {@link #decodeSerial}
 * (wire-порядок class-интернирования §5.2a). Завершение future — на выделенном callback-пуле
 * (не на decode и не на scheduler-потоке): блокирующий continuation юзера не стопорит ни decode,
 * ни keep-alive (§9). LOOKUP_ACK не несёт TLV и обрабатывается прямо на worker-потоке.
 */
public final class ClientSession {

    private final RmapConnection conn;
    private final ConnectionCodec connCodec;
    private final RmapCodec codec;
    private final Executor callbackPool;
    private final SerialExecutor decodeSerial;
    private final ScheduledExecutorService scheduler;
    private final int generation;

    private final ConcurrentHashMap<Long, PendingCall> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Integer>> subjectIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LookupWait> pendingLookups = new ConcurrentHashMap<>();
    private final AtomicLong callIds = new AtomicLong(1L);
    private final AtomicLong droppedLate = new AtomicLong(0L);

    public ClientSession(RmapConnection conn, ConnectionCodec connCodec, RmapCodec codec,
                         Executor callbackPool, Executor decodeExecutor,
                         ScheduledExecutorService scheduler, int generation) {
        this.conn = conn;
        this.connCodec = connCodec;
        this.codec = codec;
        this.callbackPool = callbackPool;
        this.decodeSerial = new SerialExecutor(decodeExecutor);
        this.scheduler = scheduler;
        this.generation = generation;
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
                return;
            }
            cancelTimer(pc);
            completeLater(pc.future, null, mapOther(other));
        });
    }

    // ---- завершение вызовов ------------------------------------------------------------------

    /** Deadline истёк (§7.1): снять pending, отправить CANCEL с его callId, завершить future таймаутом. */
    private void onDeadline(long callId) {
        PendingCall pc = pending.remove(callId);
        if (pc == null) {
            return; // уже завершён (DONE/OTHER/closed/cancel)
        }
        sendCancel(callId); // §4.2a: CANCEL несёт callId отменяемого вызова
        completeLater(pc.future, null, new RmapTimeoutException("call timed out after deadline"));
    }

    /** Пользователь отменил async-future ({@code cancel(true)}): снять pending+таймер, отправить CANCEL. */
    void onUserCancel(long callId) {
        PendingCall pc = pending.remove(callId);
        if (pc != null) {
            cancelTimer(pc);
            sendCancel(callId);
        }
    }

    /** Разрыв соединения (§4.4/§7.2): ВСЕ pending + pending-lookups fail-fast, таймеры сняты. */
    public void failAllPending(Throwable cause) {
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

    private void sendCancel(long callId) {
        conn.send(new Frame(FrameType.CANCEL, callId, EMPTY)); // без TLV; закрытое соединение молча дропнет
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
