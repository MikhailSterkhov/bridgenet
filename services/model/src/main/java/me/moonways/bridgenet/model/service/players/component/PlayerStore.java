package me.moonways.bridgenet.model.service.players.component;

import me.moonways.bridgenet.model.service.players.OfflinePlayer;
import me.moonways.bridgenet.model.service.players.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Интерфейс PlayerStore представляет собой удаленный интерфейс для
 * работы с хранилищем игроков в сетевой игре в Minecraft.
 * Этот интерфейс включает методы для получения офлайн игрока
 * по идентификатору или имени, получения игрока по идентификатору или имени,
 * а также для получения имени игрока по его идентификатору
 * и идентификатора игрока по его имени.
 */
public interface PlayerStore {

    /**
     * Получить офлайн игрока по его идентификатору.
     *
     * @param id Идентификатор офлайн игрока.
     * @return - Объект OfflinePlayer, представляющий офлайн игрока.
     */
    OfflinePlayer getOffline(UUID id);

    /**
     * Получить офлайн игрока по его имени.
     *
     * @param name Имя офлайн игрока.
     * @return - Объект OfflinePlayer, представляющий офлайн игрока.
     */
    OfflinePlayer getOffline(String name);

    /**
     * Получить онлайн игрока по его идентификатору.
     *
     * @param id Идентификатор онлайн игрока.
     * @return - Опциональный объект Player, представляющий онлайн игрока, если такой игрок онлайн, в противном случае пустое значение.
     */
    Optional<Player> get(UUID id);

    /**
     * Получить онлайн игрока по его имени.
     *
     * @param name Имя онлайн игрока.
     * @return - Опциональный объект Player, представляющий онлайн игрока, если такой игрок онлайн, в противном случае пустое значение.
     */
    Optional<Player> get(String name);

    /**
     * Получить имя игрока по его идентификатору.
     *
     * @param id Идентификатор игрока.
     * @return - Имя игрока.
     */
    String nameById(UUID id);

    /**
     * Получить идентификатор игрока по его имени.
     *
     * @param name - Имя игрока.
     * @return - Идентификатор игрока.
     */
    UUID idByName(String name);
}
