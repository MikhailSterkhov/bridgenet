package me.moonways.bridgenet.model.message;

/**
 * Версия протокола обмена ядра и клиентов Bridgenet.
 * Ядро и плагины деплоятся lockstep: несовпадение версии
 * при рукопожатии означает рассинхрон поставки и приводит
 * к немедленному отказу в подключении.
 * CURRENT нужно бампать при любом изменении набора или порядка
 * message-классов (влияет на порядковые id, см. NetworkMessagesService),
 * а также при изменении формата полей самих сообщений.
 */
public final class ProtocolVersion {

    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}
