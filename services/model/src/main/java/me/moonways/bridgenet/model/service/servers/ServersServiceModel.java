package me.moonways.bridgenet.model.service.servers;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServersServiceModel {

    /**
     * Получить список всех зарегистрированных серверов.
     */
    List<EntityServer> getTotalServers();

    /**
     * Получить список стандартных серверов для входа
     * игроков или других возможных операций.
     */
    List<EntityServer> getDefaultServers();

    /**
     * Получить список серверов, которые отвечают за редирект
     * при падении текущих серверов.
     */
    List<EntityServer> getFallbackServers();

    /**
     * Получить список серверов, которые отвечают за редирект
     * при падении текущих серверов.
     */
    Optional<EntityServer> getServerExact(@NotNull String serverName);

    /**
     * Получить инициализированный и подключенный сервер
     * по его уникальному ключевому идентификатору.
     *
     * @param uuid - ключ сервера.
     */
    Optional<EntityServer> getServerExact(@NotNull UUID uuid);

    /**
     * Получить инициализированный и подключенный сервер
     * по его ПРИМЕРНОМУ само-идентифицированному названию.
     *
     * @param serverName - примерное название сервера.
     */
    Optional<EntityServer> getServer(@NotNull String serverName);

    /**
     * Проверить сервер, подключен ли он к системе по
     * его уникальному ключевому идентификатору.
     *
     * @param uuid - ключ сервера.
     */
    boolean hasServer(@NotNull UUID uuid);

    /**
     * Проверить сервер, подключен ли он к системе по
     * его само-идентифицированному названию.
     *
     * @param serverName - название сервера.
     */
    boolean hasServer(@NotNull String serverName);
}
