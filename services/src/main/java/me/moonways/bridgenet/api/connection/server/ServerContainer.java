package me.moonways.bridgenet.api.connection.server;

import me.moonways.bridgenet.api.inject.Depend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Depend
public class ServerContainer {

    private final Map<String, Server> serverMap = Collections.synchronizedMap(new HashMap<>());
}
