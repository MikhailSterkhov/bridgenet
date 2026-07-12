package me.moonways.rmap.api;

import me.moonways.rmap.rpc.RmapProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** Явное освобождение remote-ref'а (§10): немедленный синхронный {@code REF_RELEASE} серверу
 *  (в дополнение к автоматическому GC-триггерируемому батчу). Аргумент — JDK-прокси, полученный
 *  из wrapped-возврата; иначе {@link IllegalArgumentException}. */
public final class RmapRefs {

    private RmapRefs() {
    }

    /** Освободить remote-ref, стоящий за {@code proxy}. No-op для уже мёртвой/переустановленной
     *  сессии (серверная таблица уже очищена разрывом). */
    public static void release(Object proxy) {
        if (proxy == null || !Proxy.isProxyClass(proxy.getClass())) {
            throw new IllegalArgumentException("not an RMAP remote-ref proxy");
        }
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (!(handler instanceof RmapProxy) || !((RmapProxy) handler).isRefMode()) {
            throw new IllegalArgumentException("not an RMAP remote-ref proxy");
        }
        ((RmapProxy) handler).releaseRef();
    }
}
