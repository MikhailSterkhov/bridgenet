package me.moonways.bridgenet.rsi.module;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
@Builder(builderMethodName = "newBuilder", toBuilder = true, builderClassName = "Builder")
public class ModuleID implements Serializable {

    private static final long serialVersionUID = 4034834092900643050L;

    private final int namespaceId;
    private final String namespace;

    public static ModuleID of(int namespaceId, String namespace) {
        return new ModuleID(namespaceId, namespace);
    }

    public static ModuleID of(int namespaceId) {
        String generatedNamespace = UUID.nameUUIDFromBytes(Integer.toString(namespaceId).getBytes()).toString();
        return of(namespaceId, generatedNamespace);
    }
}
