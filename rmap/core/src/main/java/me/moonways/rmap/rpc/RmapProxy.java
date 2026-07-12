package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapConnectionException;
import me.moonways.rmap.api.RmapExcluded;
import me.moonways.rmap.api.RmapRemoteException;
import me.moonways.rmap.api.RmapStaleRefException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * {@link InvocationHandler} JDK-прокси remote-интерфейса (§7.1). {@code Object}-методы и
 * {@code @RmapExcluded} обслуживаются локально; остальные уходят вызовом через {@link ClientSession}:
 * sync → блокировка на future с deadline; {@code CompletableFuture<T>} → future сразу
 * ({@code cancel(true)} → CANCEL); {@code Optional<T>} → NULL⇒empty; {@code void} → join, null.
 *
 * <p><b>Два режима.</b> Subject-прокси (из {@code lookup}) адресует экспорт по {@code path/digest}.
 * Ref-прокси ({@link #forRef}, §10) адресует remote-ref по {@code refId} через ref-форму RGET
 * ({@code subjectId=-1}), без LOOKUP/digest, и захватывает {@code generation} породившей сессии:
 * после reconnect (иная сессия) вызов → {@link RmapStaleRefException} ЛОКАЛЬНО, без сети («ref-прокси
 * мертвы навсегда», §4.4).
 *
 * <p>{@code withOptions}-view — новый subject-{@code RmapProxy} с тем же path/iface/digest и клиентом,
 * но другим дефолтным deadline; кэш subjectId и pending-map живут в общей {@link ClientSession}.
 */
public final class RmapProxy implements InvocationHandler {

    /** methodId — SHA-256; кэшируем per-Method (глобально), чтобы не считать на каждый вызов. */
    private static final ConcurrentHashMap<Method, Long> METHOD_IDS = new ConcurrentHashMap<>();

    private final RmapClient client;
    private final String path;
    private final Class<?> iface;
    private final long digest;
    private final RmapCallOptions options; // null → дефолтный call-timeout клиента
    private final boolean refMode;
    private final long refId;
    private final int refGeneration;

    public RmapProxy(RmapClient client, String path, Class<?> iface, long digest, RmapCallOptions options) {
        this.client = client;
        this.path = path;
        this.iface = iface;
        this.digest = digest;
        this.options = options;
        this.refMode = false;
        this.refId = 0L;
        this.refGeneration = 0;
    }

    private RmapProxy(RmapClient client, Class<?> iface, long refId, int refGeneration) {
        this.client = client;
        this.path = null;
        this.iface = iface;
        this.digest = 0L;
        this.options = null;
        this.refMode = true;
        this.refId = refId;
        this.refGeneration = refGeneration;
    }

    /** Ref-прокси (§10): привязан к (client, refId) и generation породившей сессии. */
    public static RmapProxy forRef(RmapClient client, Class<?> iface, long refId, int refGeneration) {
        return new RmapProxy(client, iface, refId, refGeneration);
    }

    public Class<?> iface() {
        return iface;
    }

    public boolean isRefMode() {
        return refMode;
    }

    /** Явный синхронный {@code REF_RELEASE} (§10). No-op если сессия мертва/переустановлена. */
    public void releaseRef() {
        if (!refMode) {
            throw new IllegalStateException("not a remote-ref proxy");
        }
        ClientSession session = client.liveSession();
        if (session != null && session.generation() == refGeneration) {
            session.releaseRefs(new long[]{refId});
        }
        // иначе: сессия ушла/reconnect — серверная таблица уже очищена разрывом, освобождать нечего
    }

    /** View с переопределённым deadline: тот же path/iface/digest/клиент (§7.1). */
    public RmapProxy viewWith(RmapCallOptions opts) {
        return new RmapProxy(client, path, iface, digest, opts);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // 1. Object-методы — локальны (identity + toString), не сетевые (§7.1).
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    return proxy == (args == null ? null : args[0]);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return refMode ? "RmapRef{" + iface.getName() + "#" + refId + "}" : "RmapProxy{" + path + "}";
                default:
                    throw new UnsupportedOperationException("unsupported Object method: " + method.getName());
            }
        }
        // 2. @RmapExcluded — локальный UOE (не часть remote-контракта, §8).
        if (method.isAnnotationPresent(RmapExcluded.class)) {
            throw new UnsupportedOperationException("method excluded from remote contract: " + method.getName());
        }

        Class<?> ret = method.getReturnType();
        boolean async = ret == CompletableFuture.class;
        long methodId = METHOD_IDS.computeIfAbsent(method, MethodIds::methodId);

        ClientSession.CallFuture cf;
        if (refMode) {
            // generation-гейт (§10, §4.4): reconnect случился → ref мёртв ЛОКАЛЬНО, без сетевого вызова.
            ClientSession session = client.liveSession();
            if (session == null || session.generation() != refGeneration) {
                RmapStaleRefException ex = new RmapStaleRefException(
                        "remote ref stale after reconnect/close: refId=" + refId);
                if (async) {
                    CompletableFuture<Object> f = new CompletableFuture<>();
                    f.completeExceptionally(ex);
                    return f;
                }
                throw ex;
            }
            cf = session.startRefCall(refId, method, methodId, args, client.callTimeoutMillis());
        } else {
            // живая сессия: нет соединения/не аутентифицировано → sync бросает, async — failed future.
            ClientSession session = client.liveSession();
            if (session == null) {
                RmapConnectionException ex = new RmapConnectionException("client not connected/authenticated");
                if (async) {
                    CompletableFuture<Object> f = new CompletableFuture<>();
                    f.completeExceptionally(ex);
                    return f;
                }
                throw ex;
            }
            long deadlineMillis = options != null && options.getDeadlineMillis() > 0
                    ? options.getDeadlineMillis()
                    : client.callTimeoutMillis();
            cf = session.startCall(path, digest, method, methodId, args, deadlineMillis);
        }

        // форма возврата (§7.1).
        if (async) {
            return cf; // future сразу; cancel(true) → CANCEL
        }
        if (ret == Optional.class) {
            Object value = joinSync(cf);
            return value == null ? Optional.empty() : Optional.of(value);
        }
        if (ret == void.class || ret == Void.class) {
            joinSync(cf);
            return null;
        }
        return joinSync(cf); // sync scalar/object
    }

    /** Блокирующее ожидание с распаковкой {@link ExecutionException} (deadline обеспечивает таймер). */
    private static Object joinSync(ClientSession.CallFuture cf) {
        try {
            return cf.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RmapConnectionException("call interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RmapRemoteException) {
                throw ((RmapRemoteException) cause).relocalize(); // пришить локальный хвост вызывающего
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RmapConnectionException("call failed", cause);
        }
    }
}
