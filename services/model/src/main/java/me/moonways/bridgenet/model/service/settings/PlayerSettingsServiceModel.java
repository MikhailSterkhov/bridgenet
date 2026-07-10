package me.moonways.bridgenet.model.service.settings;

import java.util.Collection;
import java.util.UUID;

public interface PlayerSettingsServiceModel {

    /**
     * Получить список идентификаторов всех возможных
     * пользовательских настроек
     */
    Collection<SettingID<?>> getTotalSettings();

    /**
     * Получить параметры пользовательской настройки по
     * уникальному идентификатору пользователя.
     *
     * @param playerId - уникальный идентификатор пользователя.
     * @param id       - идентификатор пользовательской настройки.
     */
    <T> Setting<T> getSetting(UUID playerId, SettingID<T> id);

    /**
     * Получить параметры пользовательской настройки по
     * имени пользователя.
     *
     * @param playerName - имя пользователя.
     * @param id         - идентификатор пользовательской настройки.
     */
    <T> Setting<T> getSetting(String playerName, SettingID<T> id);
}
