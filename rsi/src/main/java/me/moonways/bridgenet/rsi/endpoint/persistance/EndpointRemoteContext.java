package me.moonways.bridgenet.rsi.endpoint.persistance;

import me.moonways.bridgenet.api.command.process.CommandService;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.mtp.BridgenetNetworkController;

public final class EndpointRemoteContext {

    @Inject
    private BridgenetNetworkController networkController;
    @Inject
    private EventService eventService;
    @Inject
    private BeansService beansService;
    @Inject
    private CommandService commandService;

    /**
     * Зарегистрировать терминальную команду в Bridgenet.
     * @param object - команда.
     */
    public void registerCommand(Object object) {
        commandService.register(object.getClass(), object);
    }

    /**
     * Зарегистрировать слушатель входящих сообщений
     * по протоколу MTP.
     *
     * @param object - инстанс слушателя.
     */
    public void registerMessageListener(Object object) {
        networkController.register(object);
    }

    /**
     * Зарегистрировать слушатель входящих событий
     * сервера Bridgenet.
     *
     * @param object - инстанс слушателя.
     */
    public void registerEventListener(Object object) {
        eventService.registerHandler(object);
    }

    /**
     * Проинициализировать компоненты объекта, которые
     * требуют инжекции бинов и других сущностей
     * реализованной технологии Dependency Injection.
     *
     * @param object - объект, который нуждается в инициализации.
     */
    public void inject(Object object) {
        beansService.inject(object);
    }

    /**
     * Сохранить экземпляр объекта как бин.
     * <p>
     * Класс бина в данном случае будет браться
     * напрямую из объекта, который мы указываем
     * в сигнатуру.
     *
     * @param object - инстанс бина.
     */
    public void bind(Object object) {
        beansService.bind(object);
    }
}
