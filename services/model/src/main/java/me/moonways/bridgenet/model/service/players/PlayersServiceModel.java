package me.moonways.bridgenet.model.service.players;

import me.moonways.bridgenet.model.service.players.component.PlayerLeveling;
import me.moonways.bridgenet.model.service.players.component.PlayerStore;

import java.util.List;

/**
 * Интерфейс PlayersServiceModel — интерфейс сервиса ядра Bridgenet и
 * предоставляет методы для работы с игроками в сетевой игре в Minecraft.
 * Этот интерфейс включает методы для получения объекта PlayerLeveling,
 * объекта PlayerStore, общего количества игроков онлайн и списка онлайн игроков.
 */
public interface PlayersServiceModel {

    /**
     * Получить объект PlayerLeveling для работы с уровнем игроков.
     */
    PlayerLeveling leveling();

    /**
     * Получить объект PlayerStore для работы с хранилищем игроков.
     */
    PlayerStore store();

    /**
     * Получить общее количество игроков онлайн.
     */
    int getTotalOnline();

    /**
     * Получить список онлайн игроков.
     */
    List<Player> getOnlinePlayers();
}
