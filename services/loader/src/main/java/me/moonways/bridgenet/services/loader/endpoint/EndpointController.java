package me.moonways.bridgenet.services.loader.endpoint;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;

import java.util.Collections;
import java.util.List;

public class EndpointController {

    private final EndpointRunner runner = new EndpointRunner();
    private final EndpointLoader loader = new EndpointLoader();

    @Inject
    private BeansService beansService;

    private List<Endpoint> endpointsList;

    public void injectInternalComponents() {
        beansService.inject(runner);
        beansService.inject(loader);
    }

    public void findEndpoints() {
        endpointsList = loader.lookupStoredEndpoints();
    }

    public void bindEndpoints() {
        for (Endpoint endpoint : endpointsList) {
            runner.start(endpoint);
        }
    }

    public void unbindEndpoints() {
        for (Endpoint endpoint : endpointsList) {
            runner.stop(endpoint);
        }
    }

    public List<Endpoint> getEndpoints() {
        return Collections.unmodifiableList(endpointsList);
    }
}
