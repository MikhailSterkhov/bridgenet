package me.moonways.bridgenet.services.loader.endpoint;

import lombok.Getter;
import lombok.SneakyThrows;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.inject.bean.service.BeansStore;
import me.moonways.bridgenet.api.util.reflection.ReflectionUtils;
import me.moonways.bridgenet.assembly.OverridenProperty;

import java.lang.reflect.Field;
import java.util.Map;

public abstract class EndpointServiceObject {

    private final EndpointRemoteContext endpointContext = new EndpointRemoteContext();

    @Inject
    private BeansService beansService;
    @Inject
    private BeansStore beansStore;

    @Getter
    private Endpoint endpoint;

    @SneakyThrows
    public void init(Endpoint endpoint) {
        this.endpoint = endpoint;

        beansService.inject(endpointContext);

        construct(endpointContext);
        injectInternal();
    }

    protected void construct(EndpointRemoteContext context) throws Throwable {
        // override me.
    }

    protected final EndpointRemoteContext getEndpointContext() {
        return endpointContext;
    }

    /**
     * Заполняем инжекцией переменные, которые
     * проинициализированы и находятся в нашем
     * объекте эндпоинта.
     */
    private void injectInternal() {
        for (Field declaredField : getClass().getDeclaredFields()) {
            if (declaredField.isAnnotationPresent(Inject.class)) {
                continue;
            }
            ReflectionUtils.grantAccess(declaredField);
            try {
                Object value = declaredField.get(this);

                if (value != null) {
                    Class<?> type = value.getClass();
                    if (beansStore.isStored(type)) {
                        continue;
                    }
                    if (type.getPackage().getName().startsWith(OverridenProperty.BEANS_PACKAGE.get())) {
                        if (type.isAssignableFrom(Autobind.class)) {
                            beansService.bind(value);
                        } else {
                            beansService.fakeBind(value);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String configKey) {
        Map<String, Object> hashConfig = endpoint.getConfig().getHashConfig();
        return (T) hashConfig.get(configKey);
    }
}
