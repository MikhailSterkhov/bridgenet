package me.moonways.rmap.rpc;

import lombok.Value;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** Результат export-audit (§8): контракт интерфейса для call- и codec-слоёв.
 *  {@code decodeWhitelist} — FQN всех классов, легитимных в TLV этого интерфейса (§5.1):
 *  никакой приходящий classRef вне этого набора не резолвится в {@code Class}. */
@Value
public class InterfaceManifest {

    Class<?> iface;
    long digest;
    Map<Long, Method> methodsById;
    Set<String> decodeWhitelist;
    Set<Class<?>> wrappedInterfaces;
}
