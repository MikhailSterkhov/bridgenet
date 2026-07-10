package me.moonways.bridgenet.bootstrap.hook.type;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.bootstrap.AppBootstrap;
import me.moonways.bridgenet.bootstrap.hook.BootstrapHook;
import me.moonways.bridgenet.services.loader.EndpointsService;
import org.jetbrains.annotations.NotNull;

public class RunEndpointsHook extends BootstrapHook {

    @Inject
    private EndpointsService endpointsService;

    @Override
    protected void process(@NotNull AppBootstrap bootstrap) {
        if (endpointsService != null) {
            endpointsService.initConfig();
            endpointsService.initEndpointsController();
            endpointsService.bindEndpoints();
        }
    }
}
