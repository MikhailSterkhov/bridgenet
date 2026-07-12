package me.moonways.rmap.rpc;

import me.moonways.rmap.codec.CodecContext;
import me.moonways.rmap.codec.RmapByteReader;
import me.moonways.rmap.codec.RmapCodec;
import me.moonways.rmap.codec.RmapCodecException;
import me.moonways.rmap.rpc.SubjectRegistry.Subject;
import me.moonways.rmap.transport.NioTransport;
import me.moonways.rmap.transport.RmapConfig;
import me.moonways.rmap.transport.RmapConnection;
import me.moonways.rmap.wire.Frame;
import me.moonways.rmap.wire.FrameType;
import me.moonways.rmap.wire.OtherCode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Серверный обработчик post-auth кадров ОДНОГО соединения (§7, §9). Живёт per-connection.
 *
 * <p><b>Serial-decode.</b> LOOKUP/RGET/OTHER идут через {@link #decodeSerial} — per-connection
 * {@link SerialExecutor} поверх общего invoke-пула: TLV-декод строго в wire-порядке (инвариант
 * class-интернирования §5.2a). CANCEL/REF_RELEASE/PING/PONG не несут TLV и обрабатываются сразу
 * на worker-потоке транспорта.
 *
 * <p><b>Разделение нарушений (§7.2/§7.3).</b> Структурные (argCount≠арность, subjectId&lt;-1,
 * малформ TLV, type-mismatch аргумента) → OTHER (эхо callId) + graceful close соединения.
 * Прикладные (SUBJECT_UNDEFINED/DIGEST_MISMATCH/INVALID_SIGNATURE/TIMED_OUT/INTERNAL_ERROR) →
 * OTHER (эхо callId) БЕЗ close — соединение живёт. Исключение реализации не валит соединение.
 *
 * <p><b>Инвариант in-flight (§9).</b> Счётчик инкрементится РОВНО раз на постановку вызова
 * (enqueue) и декрементится РОВНО раз в {@code finally} задачи исполнения (задача всегда сабмитится
 * и всегда выполняется — даже снятая CANCEL'ом/очищенная onClosed лишь пропускает invoke). CANCEL
 * счётчик НЕ трогает. Достижение лимита → {@code pauseReads}; падение ниже → {@code resumeReads}.
 */
public final class RmapAgent {

    private final RmapConnection conn;
    private final ConnectionCodec connCodec;
    private final RmapCodec codec;
    private final SubjectRegistry registry;
    private final Executor invokePool;
    /** per-subject serial-dispatch executors (server-global, общий invoke-пул). */
    private final Map<Integer, SerialExecutor> subjectSerial;
    private final NioTransport transport;
    private final RmapConfig config;

    private final SerialExecutor decodeSerial;
    private final ConcurrentHashMap<Long, QueuedCall> queued = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public RmapAgent(RmapConnection conn, ConnectionCodec connCodec, RmapCodec codec,
                     SubjectRegistry registry, Executor invokePool,
                     Map<Integer, SerialExecutor> subjectSerial, NioTransport transport,
                     RmapConfig config) {
        this.conn = conn;
        this.connCodec = connCodec;
        this.codec = codec;
        this.registry = registry;
        this.invokePool = invokePool;
        this.subjectSerial = subjectSerial;
        this.transport = transport;
        this.config = config;
        this.decodeSerial = new SerialExecutor(invokePool);
    }

    /** Роутинг post-auth кадра. Вызывается на worker-потоке транспорта (конкурентно, §9). */
    public void onFrame(Frame frame) {
        switch (frame.getType()) {
            case PING:
                // PONG — побайтовое эхо timestamp (§4.4); не TLV → прямой send допустим.
                conn.send(new Frame(FrameType.PONG, 0L, frame.getPayload()));
                break;
            case LOOKUP:
                decodeSerial.execute(() -> handleLookup(frame));
                break;
            case RGET:
                decodeSerial.execute(() -> handleRget(frame));
                break;
            case OTHER:
                // сервер исходящих вызовов не делает: OTHER — спурьёзный/поздний ответ, дропаем.
                // Роутим через serial ради инварианта wire-порядка интернирования (§5.2a).
                decodeSerial.execute(() -> { /* drop */ });
                break;
            case CANCEL:
                handleCancel(frame);
                break;
            case REF_RELEASE:
                // задача 5 (ObjectTable); пока дроп.
                break;
            default:
                // PONG и любой неожиданный post-auth кадр — игнор.
                break;
        }
    }

    /** Разрыв соединения: не держим QueuedCall (§2(б)). Уже сабмиченные задачи исполнения
     *  найдут null на {@code remove} и пропустят invoke, задекрементив in-flight в finally. */
    public void onClosed() {
        queued.clear();
    }

    // ---- LOOKUP (§4.2) ----

    private void handleLookup(Frame frame) {
        long callId = frame.getCallId();
        try {
            CallWire.Lookup lookup = CallWire.decodeLookup(reader(frame));
            Subject subject = registry.byPath(lookup.getPath());
            if (subject == null) {
                sendOther(callId, OtherCode.SUBJECT_UNDEFINED, "unknown subject: " + lookup.getPath());
                return;
            }
            if (lookup.getDigest() != subject.getManifest().getDigest()) {
                sendOther(callId, OtherCode.DIGEST_MISMATCH, "interface digest mismatch: " + lookup.getPath());
                return;
            }
            connCodec.encodeAndSend(conn, FrameType.LOOKUP_ACK, callId,
                    (out, ctx) -> CallWire.encodeLookupAck(out, subject.getId()));
        } catch (RmapCodecException e) {
            // малформ LOOKUP-payload — структурное нарушение.
            sendOtherAndClose(callId, OtherCode.PROTOCOL_ERROR, "malformed LOOKUP: " + e.getMessage());
        }
    }

    // ---- RGET (§7.2, §10.1) ----

    private void handleRget(Frame frame) {
        long callId = frame.getCallId();
        RmapByteReader in = reader(frame);

        CallWire.RgetHeader header;
        try {
            header = CallWire.decodeRgetHeader(in);
        } catch (CallWire.WireProtocolException e) {
            sendOtherAndClose(callId, e.otherCode(), e.getMessage());
            return;
        } catch (RmapCodecException e) {
            sendOtherAndClose(callId, OtherCode.CODEC_ERROR, "malformed RGET header: " + e.getMessage());
            return;
        }

        if (header.getSubjectId() == -1) {
            // ref-форма RGET (§10) — задача 5; до неё STALE_REF (прикладной, без close).
            sendOther(callId, OtherCode.STALE_REF, "ref-form RGET not supported yet");
            return;
        }
        Subject subject = registry.byId(header.getSubjectId());
        if (subject == null) {
            sendOther(callId, OtherCode.SUBJECT_UNDEFINED, "unknown subjectId: " + header.getSubjectId());
            return;
        }
        Method method = subject.getManifest().getMethodsById().get(header.getMethodId());
        if (method == null) {
            sendOther(callId, OtherCode.INVALID_SIGNATURE, "unknown methodId");
            return;
        }
        int arity = method.getParameterCount();
        if (header.getArgCount() != arity) {
            sendOtherAndClose(callId, OtherCode.PROTOCOL_ERROR,
                    "argCount " + header.getArgCount() + " != arity " + arity);
            return;
        }

        Object[] args = new Object[arity];
        Class<?>[] paramTypes = method.getParameterTypes();
        CodecContext readCtx = connCodec.readCtx();
        try {
            for (int i = 0; i < arity; i++) {
                Object value = codec.decode(in, readCtx);
                Class<?> expected = boxed(paramTypes[i]);
                if (value != null && !expected.isInstance(value)) {
                    sendOtherAndClose(callId, OtherCode.CODEC_ERROR,
                            "arg " + i + " type mismatch: expected " + expected.getName());
                    return;
                }
                args[i] = value;
            }
        } catch (RmapCodecException e) {
            sendOtherAndClose(callId, OtherCode.CODEC_ERROR, "arg decode failed: " + e.getMessage());
            return;
        }

        long deadlineAt = System.currentTimeMillis() + header.getDeadlineMillis();
        enqueueAndSubmit(new QueuedCall(callId, subject, method, args, deadlineAt));
    }

    private void enqueueAndSubmit(QueuedCall call) {
        queued.put(call.callId, call);
        int n = inFlight.incrementAndGet();
        if (n >= config.getMaxInFlightRequests()) {
            transport.pauseReads(conn); // §9: input flow-control
        }
        Executor exec = call.subject.getOpts().isSerialDispatch()
                ? subjectSerial.computeIfAbsent(call.subject.getId(), k -> new SerialExecutor(invokePool))
                : invokePool;
        exec.execute(() -> runCall(call));
    }

    private void runCall(QueuedCall call) {
        try {
            if (queued.remove(call.callId) == null) {
                return; // снят CANCEL'ом до старта / очищен onClosed — invoke пропускаем.
            }
            if (System.currentTimeMillis() > call.deadlineAtMillis) {
                sendOther(call.callId, OtherCode.TIMED_OUT, "deadline expired in queue"); // без close
                return;
            }
            Object result;
            try {
                result = call.method.invoke(call.subject.getImpl(), call.args);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                sendInternalError(call.callId, cause); // прикладное исключение — без close
                return;
            } catch (Throwable t) {
                sendInternalError(call.callId, t); // серверный сбой invoke — без close
                return;
            }
            // успех: DONE(TLV результата); void → invoke вернул null → TLV NULL.
            connCodec.encodeAndSend(conn, FrameType.DONE, call.callId,
                    (out, ctx) -> codec.encode(out, result, ctx));
        } finally {
            onCallFinished();
        }
    }

    private void onCallFinished() {
        int n = inFlight.decrementAndGet();
        if (n == config.getMaxInFlightRequests() - 1) {
            transport.resumeReads(conn); // упали ниже лимита → снова читаем RGET (§9)
        }
    }

    // ---- CANCEL (§7.1) ----

    private void handleCancel(Frame frame) {
        long callId = frame.getCallId();
        if (callId != 0) {
            // best-effort: снят до старта → ответа не будет; исполняющийся не прерываем;
            // in-flight НЕ трогаем — задекрементит runCall (инвариант выше).
            queued.remove(callId);
        }
    }

    // ---- отправка ответов (§4.2a: эхо callId; connection-level OTHER — callId=0) ----

    private void sendOther(long callId, int code, String message) {
        connCodec.encodeAndSend(conn, FrameType.OTHER, callId,
                (out, ctx) -> CallWire.encodeOther(out, code, message));
    }

    private void sendOtherAndClose(long callId, int code, String message) {
        sendOther(callId, code, message);
        conn.closeAfterFlush(); // закрыть ПОСЛЕ слива OTHER (клиент получит код, затем FIN).
    }

    private void sendInternalError(long callId, Throwable cause) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
        connCodec.encodeAndSend(conn, FrameType.OTHER, callId,
                (out, ctx) -> CallWire.encodeOtherWithException(out, OtherCode.INTERNAL_ERROR, message,
                        o -> codec.encodeThrowable(o, cause, ctx)));
    }

    private static RmapByteReader reader(Frame frame) {
        byte[] p = frame.getPayload();
        return new RmapByteReader(p, 0, p.length);
    }

    private static Class<?> boxed(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == boolean.class) return Boolean.class;
        if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class;
        if (t == char.class) return Character.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == void.class) return Void.class;
        return t;
    }

    /** Поставленный на исполнение вызов (для CANCEL-снятия и deadline-чека). Иммутабельный. */
    private static final class QueuedCall {
        final long callId;
        final Subject subject;
        final Method method;
        final Object[] args;
        final long deadlineAtMillis;

        QueuedCall(long callId, Subject subject, Method method, Object[] args, long deadlineAtMillis) {
            this.callId = callId;
            this.subject = subject;
            this.method = method;
            this.args = args;
            this.deadlineAtMillis = deadlineAtMillis;
        }
    }
}
