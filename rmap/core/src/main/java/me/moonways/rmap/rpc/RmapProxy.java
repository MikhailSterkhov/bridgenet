package me.moonways.rmap.rpc;

import me.moonways.rmap.api.RmapClient;
import me.moonways.rmap.api.RmapConnectionException;
import me.moonways.rmap.api.RmapExcluded;
import me.moonways.rmap.api.RmapRemoteException;

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
 * <p>{@code withOptions}-view — новый {@code RmapProxy} с тем же path/iface/digest и клиентом, но
 * другим дефолтным deadline; кэш subjectId и pending-map живут в общей {@link ClientSession}.
 */
public final class RmapProxy implements InvocationHandler {

    /** methodId — SHA-256; кэшируем per-Method (глобально), чтобы не считать на каждый вызов. */
    private static final ConcurrentHashMap<Method, Long> METHOD_IDS = new ConcurrentHashMap<>();

    private final RmapClient client;
    private final String path;
    private final Class<?> iface;
    private final long digest;
    private final RmapCallOptions options; // null → дефолтный call-timeout клиента

    public RmapProxy(RmapClient client, String path, Class<?> iface, long digest, RmapCallOptions options) {
        this.client = client;
        this.path = path;
        this.iface = iface;
        this.digest = digest;
        this.options = options;
    }

    public Class<?> iface() {
        return iface;
    }

    /** View с переопределённым deadline: тот же path/iface/digest/клиент (§7.1). */
    public RmapProxy viewWith(RmapCallOptions opts) {
        return new RmapProxy(client, path, iface, digest, opts);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // 1. Object-методы — локальны (identity + RmapProxy{path}), не сетевые (§7.1).
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    return proxy == (args == null ? null : args[0]);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "RmapProxy{" + path + "}";
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

        // 3. живая сессия: нет соединения/не аутентифицировано → sync бросает, async — failed future.
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
        long methodId = METHOD_IDS.computeIfAbsent(method, MethodIds::methodId);

        ClientSession.CallFuture cf = session.startCall(path, digest, method, methodId, args, deadlineMillis);

        // 4. форма возврата (§7.1).
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
