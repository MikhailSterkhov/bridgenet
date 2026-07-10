package me.moonways.bridgenet.bootstrap.restart;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.inject.bean.service.BeansStore;
import me.moonways.bridgenet.bootstrap.AppBootstrap;
import me.moonways.bridgenet.services.loader.EndpointsService;

import java.util.ArrayList;

@Log4j2
@Autobind
public final class RestartService {

    @Inject
    private AppBootstrap appBootstrap;
    @Inject
    private BeansStore beansStore;
    @Inject
    private BeansService beansService;
    @Inject
    private EndpointsService remoteServicesManagement;

    public void doRestart() {
        log.info("§6Restarting an application...");

        remoteServicesManagement.unbindEndpoints();
        appBootstrap.justShutdown();

        new ArrayList<>(beansStore.getTotalBeans()).forEach(beansService::unbind);

        log.info("§6Replay the application restart...");

        new AppBootstrap().start(new String[0]);
    }
}
