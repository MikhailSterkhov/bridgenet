package me.moonways.bridgenet.test.engine.component.module.impl;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.services.loader.EndpointsService;
import me.moonways.bridgenet.test.data.ExampleClient;
import me.moonways.bridgenet.test.engine.component.module.ModuleAdapter;
import me.moonways.bridgenet.test.engine.component.module.ModuleConfig;
import me.moonways.bridgenet.test.engine.flow.TestFlowContext;

import java.util.Collections;

public class ClientsModule extends ModuleAdapter {

    @Inject
    private BeansService beansService;

    public ClientsModule() {
        super(ModuleConfig.builder()
                .dependencies(
                        Collections.singletonList(
                                ServicesModule.class
                        ))
                .packagesToScanning(
                        Collections.singletonList(
                                fromClassPackage(EndpointsService.class)
                        ))
                .build());
    }

    @Override
    public void install(TestFlowContext context) {
        beansService.subscribeOn(EndpointsService.class, (remoteServicesManagement) ->
                remoteServicesManagement.subscribeExportedAll(this::bindClient));
    }

    private void bindClient() {
        ExampleClient exampleClient = new ExampleClient();
        exampleClient.start();

        beansService.justStore(exampleClient);
    }
}
