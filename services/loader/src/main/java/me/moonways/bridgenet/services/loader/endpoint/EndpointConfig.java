package me.moonways.bridgenet.services.loader.endpoint;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Map;

@Getter
@ToString
@RequiredArgsConstructor
public class EndpointConfig {

    private final String jar;
    private final Map<String, Object> hashConfig;
}
