package me.moonways.bridgenet.test.engine.component.module.impl;

import me.moonways.bridgenet.services.loader.EndpointsService;
import me.moonways.bridgenet.test.engine.component.module.ModuleAdapter;
import me.moonways.bridgenet.test.engine.component.module.ModuleConfig;

import java.util.Arrays;

public class ServicesModule extends ModuleAdapter {

    private static final String SERVICES_MODEL__PACKAGE = "me.moonways.bridgenet.model";

    public ServicesModule() {
        super(ModuleConfig.builder()
                .dependencies(
                        Arrays.asList(
                                EventsModule.class,
                                DatabasesModule.class,
                                MtpModule.class,
                                CommandsModule.class
                        ))
                .packagesToScanning(
                        Arrays.asList(
                                SERVICES_MODEL__PACKAGE,
                                fromClassPackage(EndpointsService.class)
                        ))
                .build());
    }
}
