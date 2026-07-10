package me.moonways.bridgenet.services.loader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public final class ServiceInfo {

    private final String name;
    private final Class<?> modelClass;
}
