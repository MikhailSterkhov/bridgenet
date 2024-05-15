package me.moonways.bridgenet.test.engine.module.impl;

import me.moonways.bridgenet.api.command.CommandRegistry;
import me.moonways.bridgenet.test.engine.module.TestEngineModuleAdapter;
import me.moonways.bridgenet.test.engine.module.TestModuleBeans;

import java.util.Collections;

public class CommandsModule extends TestEngineModuleAdapter {

    public CommandsModule() {
        super(TestModuleBeans.builder()
                .packagesToScanning(
                        Collections.singletonList(
                                fromClassPackage(CommandRegistry.class)
                        ))
                .build());
    }
}
