package me.moonways.bridgenet.rsi.endpoint.persistance;

import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.inject.bean.service.BeansStore;

import java.lang.reflect.Field;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Map;

public abstract class EndpointRemoteObject extends UnicastRemoteObject {

    private static final long serialVersionUID = -1564328046265570096L;
    private final EndpointRemoteContext endpointContext = new EndpointRemoteContext();

    @Inject
    private BeansService beansService;
    @Inject
    private BeansStore beansStore;

    public EndpointRemoteObject() throws RemoteException {
        super();
    }

    @PostConstruct
    private void internal_postConstruct() {
        beansService.inject(endpointContext);

        construct(endpointContext);
        injectInternal();
    }

    protected void construct(EndpointRemoteContext context) {
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
            declaredField.setAccessible(true);
            try {
                Object value = declaredField.get(this);

                if (value != null) {
                    injectInternal(value);
                }
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void injectInternal(Object value) {
        Class<?> type = value.getClass();

        if (Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            injectInternalCollection(value);
            return;
        }

        if (beansStore.isStored(type)) {
            return;
        }
        if (type.getPackage().getName().startsWith("me.moonways")) {
            if (type.isAssignableFrom(Autobind.class)) {
                beansService.bind(value);
            } else {
                beansService.fakeBind(value);
            }
        }
    }

    private void injectInternalCollection(Object value) {
        Class<?> type = value.getClass();
        if (Map.class.isAssignableFrom(type)) {
            ((Map) value).values().forEach(this::injectInternal);
        } else {
            ((Collection) value).forEach(this::injectInternal);
        }
    }
}
