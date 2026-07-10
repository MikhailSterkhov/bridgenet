package me.moonways.bridgenet.model.message;

/**
 * Версия протокола обмена ядра и клиентов Bridgenet.
 * Ядро и плагины деплоятся lockstep: несовпадение версии
 * при рукопожатии означает рассинхрон поставки и приводит
 * к немедленному отказу в подключении.
 */
public final class ProtocolVersion {

    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}
