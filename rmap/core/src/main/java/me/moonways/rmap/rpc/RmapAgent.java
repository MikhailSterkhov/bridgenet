package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapLogger;
import me.moonways.rmap.api.RmapLogging;
import me.moonways.rmap.api.RmapMetrics;
import me.moonways.rmap.api.Snapshot;
import me.moonways.rmap.codec.CodecContext;
import me.moonways.rmap.codec.CodecRegistry;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Серверный обработчик post-auth кадров ОДНОГО соединения (§7, §9). Живёт per-connection.
 *
 * <p><b>Serial-decode.</b> LOOKUP/RGET/OTHER идут через {@link #decodeSerial} — per-connection
 * {@link SerialExecutor} поверх общего invoke-пула: TLV-декод строго в wire-порядке (инвариант
 * class-интернирования §5.2a). CANCEL/REF_RELEASE/PING/PONG не несут TLV-объектов и обрабатываются
 * сразу на worker-потоке транспорта.
 *
 * <p><b>Разделение нарушений (§7.2/§7.3).</b> Структурные (argCount≠арность, subjectId&lt;-1,
 * малформ TLV, type-mismatch аргумента) → OTHER (эхо callId) + graceful close соединения.
 * Прикладные (SUBJECT_UNDEFINED/DIGEST_MISMATCH/INVALID_SIGNATURE/STALE_REF/TIMED_OUT/INTERNAL_ERROR)
 * → OTHER (эхо callId) БЕЗ close — соединение живёт. Исключение реализации не валит соединение.
 * На прикладных отказах, оставляющих соединение живым, аргументы кадра декодируются-и-выбрасываются
 * (drain), чтобы продвинуть read-интернер (§5.2a): иначе classRef-def пропущенного кадра рассинхронил
 * бы CLASSREF_USE следующего.
 *
 * <p><b>Remote-refs (§10).</b> Per-connection {@link ObjectTable} + {@link RefContextImpl}; ref-форма
 * RGET ({@code subjectId=-1}) адресует запись по refId, {@code REF_RELEASE} освобождает. Манифесты
 * ref-интерфейсов считаются лениво и кэшируются. Разрыв → {@link ObjectTable#clear()}.
 *
 * <p><b>Инвариант in-flight (§9, {@link FlowController}).</b> Счётчик инкрементится РОВНО раз на
 * постановку вызова (enqueue) и декрементится РОВНО раз при завершении: для sync-возврата — в
 * {@code finally} задачи исполнения; для {@code CompletableFuture}-возврата — при завершении future
 * либо истечении дедлайна (атомарная защёлка «ответ отправлен один раз»), т.е. НЕ на invoke-потоке.
 */
public final class RmapAgent {

    private static final RmapLogger LOG = RmapLogging.get(RmapAgent.class.getName());

    private final RmapConnection conn;
    private final ConnectionCodec connCodec;
    private final RmapCodec codec;
    private final CodecRegistry codecRegistry;
    private final SubjectRegistry registry;
    private final Executor invokePool;
    /** per-subject serial-dispatch executors (server-global, общий invoke-пул). */
    private final Map<Integer, SerialExecutor> subjectSerial;
    private final NioTransport transport;
    /** Общий scheduler сервера (§9) — deadline-таймеры async-CF-возвратов (финревью-фикс A/I1). */
    private final ScheduledExecutorService scheduler;
    private final RmapConfig config;
    private final RmapMetrics metrics;

    private final SerialExecutor decodeSerial;
    private final ConcurrentHashMap<Long, QueuedCall> queued = new ConcurrentHashMap<>();
    /** Per-connection input flow-control (§9): инкремент/декремент in-flight и решение pause/resume
     *  OP_READ — атомарно под общим монитором, чтобы порядок постановки pause/resume-задач в FIFO
     *  селектора соответствовал порядку изменения счётчика (финревью-фикс A/C1). */
    private final FlowController flow;

    // remote-refs (§10)
    private final ObjectTable objectTable;
    private final RefContextImpl refContext;
    /** Ленивый кэш манифестов ref-интерфейсов (аудит по first-use, key = интерфейс записи). */
    private final ConcurrentHashMap<Class<?>, InterfaceManifest> refManifests = new ConcurrentHashMap<>();

    public RmapAgent(RmapConnection conn, ConnectionCodec connCodec, RmapCodec codec,
                     CodecRegistry codecRegistry, SubjectRegistry registry, Executor invokePool,
                     Map<Integer, SerialExecutor> subjectSerial, NioTransport transport,
                     ScheduledExecutorService scheduler, RmapConfig config, RmapMetrics metrics) {
        this.conn = conn;
        this.connCodec = connCodec;
        this.codec = codec;
        this.codecRegistry = codecRegistry;
        this.registry = registry;
        this.invokePool = invokePool;
        this.subjectSerial = subjectSerial;
        this.transport = transport;
        this.scheduler = scheduler;
        this.config = config;
        this.metrics = metrics != null ? metrics : RmapMetrics.NO_OP;
        this.flow = new FlowController(config.getMaxInFlightRequests(), new FlowController.Signal() {
            public void pauseReads() { transport.pauseReads(conn); }   // §9 input flow-control
            public void resumeReads() { transport.resumeReads(conn); } // упали ниже лимита → снова читаем RGET
        });
        this.decodeSerial = new SerialExecutor(invokePool);
        this.objectTable = new ObjectTable(config.getRefLeaseTimeout().toMillis());
        this.refContext = new RefContextImpl(objectTable);
        connCodec.setRefContext(refContext); // §10: REMOTE_REF encode на ответах этого соединения
    }

    /** Роутинг post-auth кадра. Вызывается на worker-потоке транспорта (конкурентно, §9). */
    public void onFrame(Frame frame) {
        metrics.frameIn(frame.getPayload().length);
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
                // сервер исходящих вызовов не делает: OTHER — спурьёзный/поздний. Через serial +
                // decode-and-discard, чтобы продвинуть read-интернер (§5.2a), иначе classRef-def
                // такого кадра рассинхронил бы следующий CLASSREF_USE.
                decodeSerial.execute(() -> drainOther(frame));
                break;
            case CANCEL:
                handleCancel(frame);
                break;
            case REF_RELEASE:
                handleRefRelease(frame);
                break;
            default:
                // PONG и любой неожиданный post-auth кадр — игнор.
                break;
        }
    }

    /** Разрыв соединения: не держим QueuedCall (§2(б)) и очищаем ObjectTable (§10 — все refs мертвы). */
    public void onClosed() {
        queued.clear();
        objectTable.clear();
    }

    /** Lease-эвикция ref'ов (§10): вызывается общим scheduler'ом сервера раз в минуту. */
    public void sweepExpiredRefs() {
        int evicted = objectTable.sweepExpired(objectTable.getLeaseTimeoutMillis());
        if (evicted > 0) {
            LOG.debug("ref lease sweep evicted " + evicted + " entries on " + conn.remoteAddress());
        }
    }

    /** ObjectTable соединения (интроспекция для тестов/метрик). */
    ObjectTable objectTable() {
        return objectTable;
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
        CodecContext readCtx = connCodec.readCtx();

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
            handleRefRget(header, in, readCtx, callId); // ref-форма (§10.1)
            return;
        }

        Subject subject = registry.byId(header.getSubjectId());
        if (subject == null) {
            // прикладной отказ (без close): слить аргументы, продвинув read-интернер.
            if (drainArgs(in, readCtx, header.getArgCount(), callId)) {
                sendOther(callId, OtherCode.SUBJECT_UNDEFINED, "unknown subjectId: " + header.getSubjectId());
            }
            return;
        }
        Method method = subject.getManifest().getMethodsById().get(header.getMethodId());
        if (method == null) {
            if (drainArgs(in, readCtx, header.getArgCount(), callId)) {
                sendOther(callId, OtherCode.INVALID_SIGNATURE, "unknown methodId");
            }
            return;
        }
        int arity = method.getParameterCount();
        if (header.getArgCount() != arity) {
            sendOtherAndClose(callId, OtherCode.PROTOCOL_ERROR,
                    "argCount " + header.getArgCount() + " != arity " + arity); // структурное → close
            return;
        }
        Object[] args = decodeArgs(in, readCtx, method, callId);
        if (args == null) {
            return; // decode-сбой уже ответил CODEC_ERROR + close
        }

        long deadlineAt = System.currentTimeMillis() + header.getDeadlineMillis();
        Executor exec = subject.getOpts().isSerialDispatch()
                ? subjectSerial.computeIfAbsent(subject.getId(), k -> new SerialExecutor(invokePool))
                : invokePool;
        enqueueAndSubmit(new QueuedCall(callId, subject.getImpl(), method, args, deadlineAt, exec,
                subject.getManifest().getWrappedInterfaces(), subject.getOpts(),
                method.isAnnotationPresent(Snapshot.class)));
    }

    /** Ref-форма RGET (§10.1): {@code subjectId==-1} → вызов метода по refId из ObjectTable. */
    private void handleRefRget(CallWire.RgetHeader header, RmapByteReader in, CodecContext readCtx, long callId) {
        ObjectTable.Entry entry = objectTable.get(header.getRefId());
        if (entry == null) {
            // ref мёртв/истёк lease — прикладной STALE_REF (без close); слить аргументы (интернер §5.2a).
            metrics.staleRefHit();
            if (drainArgs(in, readCtx, header.getArgCount(), callId)) {
                sendOther(callId, OtherCode.STALE_REF, "stale ref: " + header.getRefId());
            }
            return;
        }
        InterfaceManifest manifest;
        try {
            manifest = refManifest(entry.iface, entry.opts);
        } catch (me.moonways.rmap.api.RmapExportException ex) {
            // I4: ленивый манифест ref-интерфейса бросил (интерфейс непригоден). НЕ виснем — иначе
            // исключение проглотил бы SerialExecutor и клиент ждал бы до deadline. Сливаем аргументы
            // (продвинуть read-интернер §5.2a) и отвечаем без close: соединение и прочие subject'ы живы.
            if (drainArgs(in, readCtx, header.getArgCount(), callId)) {
                sendOther(callId, OtherCode.INVALID_SIGNATURE, "ref interface not exportable: " + ex.getMessage());
            }
            return;
        }
        Method method = manifest.getMethodsById().get(header.getMethodId());
        if (method == null) {
            if (drainArgs(in, readCtx, header.getArgCount(), callId)) {
                sendOther(callId, OtherCode.INVALID_SIGNATURE, "unknown methodId on ref");
            }
            return;
        }
        int arity = method.getParameterCount();
        if (header.getArgCount() != arity) {
            sendOtherAndClose(callId, OtherCode.PROTOCOL_ERROR,
                    "argCount " + header.getArgCount() + " != arity " + arity);
            return;
        }
        Object[] args = decodeArgs(in, readCtx, method, callId);
        if (args == null) {
            return;
        }
        long deadlineAt = System.currentTimeMillis() + header.getDeadlineMillis();
        // v1: ref-вызовы не сериализуются per-subject — на общий invoke-пул (deadline/CANCEL общие).
        enqueueAndSubmit(new QueuedCall(callId, entry.impl, method, args, deadlineAt, invokePool,
                manifest.getWrappedInterfaces(), entry.opts,
                method.isAnnotationPresent(Snapshot.class)));
    }

    /** Манифест ref-интерфейса записи, посчитанный лениво (аудит с opts-родителя) и кэшируемый. */
    private InterfaceManifest refManifest(Class<?> iface, ExportOptions opts) {
        return refManifests.computeIfAbsent(iface,
                k -> ExportAudit.audit(k, opts != null ? opts : ExportOptions.defaults(), codecRegistry));
    }

    /**
     * Декод argCount×TLV-аргументов метода с проверкой типов. Возврат {@code null} = decode-сбой или
     * type-mismatch: ответ CODEC_ERROR + close уже отправлен, вызывающий прекращает обработку.
     */
    private Object[] decodeArgs(RmapByteReader in, CodecContext readCtx, Method method, long callId) {
        int arity = method.getParameterCount();
        Object[] args = new Object[arity];
        Class<?>[] paramTypes = method.getParameterTypes();
        try {
            for (int i = 0; i < arity; i++) {
                Object value = codec.decode(in, readCtx);
                Class<?> expected = boxed(paramTypes[i]);
                if (value != null && !expected.isInstance(value)) {
                    sendOtherAndClose(callId, OtherCode.CODEC_ERROR,
                            "arg " + i + " type mismatch: expected " + expected.getName());
                    return null;
                }
                args[i] = value;
            }
        } catch (RmapCodecException e) {
            sendOtherAndClose(callId, OtherCode.CODEC_ERROR, "arg decode failed: " + e.getMessage());
            return null;
        }
        return args;
    }

    /**
     * Долг-фикс задачи 3: на прикладном отказе, оставляющем соединение живым, декодировать-и-выбросить
     * {@code argCount} TLV, продвинув read-интернер. Возврат {@code true} → можно слать non-closing
     * OTHER; {@code false} → сам decode структурно сломан (интернер уже мог рассинхрониться) → отправлен
     * CODEC_ERROR + close, вызывающий прекращает обработку.
     */
    private boolean drainArgs(RmapByteReader in, CodecContext readCtx, int argCount, long callId) {
        try {
            for (int i = 0; i < argCount; i++) {
                codec.decode(in, readCtx);
            }
            return true;
        } catch (RmapCodecException e) {
            sendOtherAndClose(callId, OtherCode.CODEC_ERROR, "arg drain failed: " + e.getMessage());
            return false;
        }
    }

    /** OTHER от пира (спурьёзный/поздний): decode-and-discard для продвижения read-интернера (§5.2a).
     *  Структурный малформ payload'а → close (интернер уже мог рассинхрониться). */
    private void drainOther(Frame frame) {
        try {
            CallWire.decodeOther(reader(frame), codec, connCodec.readCtx());
            LOG.debug("dropped spurious OTHER from peer on " + conn.remoteAddress());
        } catch (RmapCodecException e) {
            sendOtherAndClose(0L, OtherCode.PROTOCOL_ERROR, "malformed OTHER from peer");
        }
        // успешный decode продвинул read-интернер; сам OTHER дропаем (сервер исходящих вызовов не делает).
    }

    private void enqueueAndSubmit(QueuedCall call) {
        queued.put(call.callId, call);
        metrics.callStarted();
        flow.onEnqueue(); // §9/финревью-фикс A(C1): инкремент in-flight + возможный pauseReads под локом
        call.executor.execute(() -> runCall(call)); // invoke — ВНЕ лока, исполнение не сериализуется
    }

    private void runCall(QueuedCall call) {
        boolean success = false;
        boolean asyncPending = false;
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
                result = call.method.invoke(call.impl, call.args);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                sendInternalError(call.callId, cause); // прикладное исключение — без close
                return;
            } catch (Throwable t) {
                sendInternalError(call.callId, t); // серверный сбой invoke — без close
                return;
            }
            // §5/§7: агент распаковывает Optional/CompletableFuture-возврат по СИГНАТУРЕ метода —
            // на провод едет распакованное значение (эти обёртки НЕ кодируются как значения).
            Class<?> ret = call.method.getReturnType();
            if (ret == CompletableFuture.class && result instanceof CompletableFuture) {
                // §9/финревью-фикс A(I1): НЕ блокируем invoke-поток на future.get() — блокирующий get
                // морил бы ВЕСЬ invokePool (и вместе с ним serial-decode всех соединений) под N медленными
                // CF-вызовами; CF, завершаемый ДРУГИМ RMAP-вызовом на том же пуле, давал бы циклический
                // дедлок. Подписываемся неблокирующе (whenComplete) + deadline-таймер на scheduler'е;
                // in-flight декремент (flow-control C1) и ответ — РОВНО раз. async-путь владеет finish.
                asyncPending = true;
                completeFromFuture(call, (CompletableFuture<?>) result);
                return;
            }
            if (ret == Optional.class && result instanceof Optional) {
                result = ((Optional<?>) result).orElse(null); // present → значение, empty → NULL
            }
            success = sendResult(call, result);
        } finally {
            if (!asyncPending) { // async-путь снимет in-flight при завершении future/дедлайне (finishAsync)
                onCallFinished();
                metrics.callCompleted(success);
            }
        }
    }

    /**
     * Кодирует и шлёт DONE(результат); возврат — успех (для метрики). {@code void}/empty Optional → TLV NULL.
     *
     * <p>Долг-фикс задачи 3: encode результата может бросить {@link RmapCodecException} (полиморфный подтип
     * мимо аудита) — тогда write-интернер откачен (ConnectionCodec) и мы отвечаем internal-error (EXCEPTION-
     * TLV интернер не трогает), а не тихо роняем ответ. Долг-фикс задачи 6: ПОЛЬЗОВАТЕЛЬСКИЙ ValueCodec.write
     * (§5.3) может бросить ЛЮБОЙ RuntimeException, не только RmapCodecException — узкий catch пропускал бы его,
     * клиент не получал бы ни DONE, ни OTHER и висел бы до deadline. ConnectionCodec.encodeAndSend откатывает
     * write-интернер на ЛЮБОЙ RuntimeException — здесь достаточно широкого catch, интернер остаётся цел.
     */
    private boolean sendResult(QueuedCall call, Object result) {
        final Object encoded = result;
        // §10: @Snapshot-метод кодирует корень значением (даже wrap-тип), вложенные ref-поля — рефами.
        final Object snapshotRoot = call.snapshot ? encoded : null;
        try {
            connCodec.encodeAndSend(conn, FrameType.DONE, call.callId,
                    call.wrapSet, snapshotRoot, call.parentOpts,
                    (out, ctx) -> codec.encode(out, encoded, ctx));
            return true;
        } catch (RuntimeException e) {
            sendInternalError(call.callId, e);
            return false;
        }
    }

    /**
     * Неблокирующая распаковка {@code CompletableFuture}-возврата (§9/финревью-фикс A/I1). Подписка через
     * {@code whenComplete} (ответ DONE либо internal-error) + deadline-таймер на общем scheduler'е (ответ
     * OTHER TIMED_OUT). Гонка whenComplete↔таймер разрешается атомарной защёлкой {@code answered} — ответ
     * И завершение вызова (in-flight декремент для flow-control §9) происходят РОВНО раз. Оба пути
     * потокобезопасны: {@code encodeAndSend} под write-локом, {@code onCallFinished} под локом {@link FlowController}.
     */
    private void completeFromFuture(QueuedCall call, CompletableFuture<?> cf) {
        AtomicBoolean answered = new AtomicBoolean(false);
        ScheduledFuture<?> timer;
        try {
            long delay = Math.max(1L, call.deadlineAtMillis - System.currentTimeMillis());
            timer = scheduler.schedule(() -> {
                if (answered.compareAndSet(false, true)) {
                    sendOther(call.callId, OtherCode.TIMED_OUT, "async result deadline exceeded"); // без close
                    finishAsync(false);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rej) {
            timer = null; // scheduler остановлен (сервер закрывается) — дедлайн не взводим
        }
        final ScheduledFuture<?> deadlineTimer = timer;
        cf.whenComplete((value, err) -> {
            if (!answered.compareAndSet(false, true)) {
                return; // deadline-таймер уже ответил TIMED_OUT и снял in-flight
            }
            if (deadlineTimer != null) {
                deadlineTimer.cancel(false);
            }
            boolean ok = false;
            if (err != null) {
                Throwable cause = err instanceof CompletionException && err.getCause() != null
                        ? err.getCause() : err;
                sendInternalError(call.callId, cause); // приложение зафейлило future — без close
            } else {
                ok = sendResult(call, value);
            }
            finishAsync(ok);
        });
    }

    /** Завершение async-CF-вызова: in-flight декремент (flow-control §9) + метрика — вызывается РОВНО раз
     *  (защёлка {@code answered} в {@link #completeFromFuture}). */
    private void finishAsync(boolean success) {
        onCallFinished();
        metrics.callCompleted(success);
    }

    private void onCallFinished() {
        // §9/финревью-фикс A(C1): декремент in-flight + возможный resumeReads под локом (симметрично
        // enqueueAndSubmit). Вызывается РОВНО раз на вызов (sync-finally / finishAsync / deadline-таймер).
        flow.onFinish();
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

    // ---- REF_RELEASE (§4.2, §10) ----

    private void handleRefRelease(Frame frame) {
        long[] refIds;
        try {
            refIds = CallWire.decodeRefRelease(reader(frame)); // без TLV-объектов → интернер не трогает
        } catch (RmapCodecException e) {
            return; // малформ REF_RELEASE — молча дропаем (callId=0, без ответа)
        }
        for (long refId : refIds) {
            objectTable.release(refId); // неизвестный refId — молча + метрика (§10)
        }
        metrics.refReleased(refIds.length);
    }

    // ---- отправка ответов (§4.2a: эхо callId; connection-level OTHER — callId=0) ----

    private void sendOther(long callId, int code, String message) {
        LOG.debug("sending OTHER(" + OtherCode.name(code) + ") callId=" + callId + ": " + message);
        connCodec.encodeAndSend(conn, FrameType.OTHER, callId,
                (out, ctx) -> CallWire.encodeOther(out, code, message));
    }

    private void sendOtherAndClose(long callId, int code, String message) {
        sendOther(callId, code, message);
        conn.closeAfterFlush(); // закрыть ПОСЛЕ слива OTHER (клиент получит код, затем FIN).
    }

    private void sendInternalError(long callId, Throwable cause) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
        LOG.warn("sending OTHER(INTERNAL_ERROR) callId=" + callId + ": " + message, cause);
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

    /** Поставленный на исполнение вызов (для CANCEL-снятия и deadline-чека). Иммутабельный.
     *  Несёт разрешённые {@code executor}/{@code impl} и параметры encode ответа (§10: wrap-набор,
     *  opts-родителя для регистрации вложенных рефов, флаг @Snapshot). */
    private static final class QueuedCall {
        final long callId;
        final Object impl;
        final Method method;
        final Object[] args;
        final long deadlineAtMillis;
        final Executor executor;
        final Set<Class<?>> wrapSet;
        final ExportOptions parentOpts;
        final boolean snapshot;

        QueuedCall(long callId, Object impl, Method method, Object[] args, long deadlineAtMillis,
                   Executor executor, Set<Class<?>> wrapSet, ExportOptions parentOpts, boolean snapshot) {
            this.callId = callId;
            this.impl = impl;
            this.method = method;
            this.args = args;
            this.deadlineAtMillis = deadlineAtMillis;
            this.executor = executor;
            this.wrapSet = wrapSet;
            this.parentOpts = parentOpts;
            this.snapshot = snapshot;
        }
    }
}
