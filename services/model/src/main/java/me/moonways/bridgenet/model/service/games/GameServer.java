package me.moonways.bridgenet.model.service.games;

import me.moonways.bridgenet.model.service.servers.ServerInfo;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс описывает реализацию игрового сервера,
 * хранящий в себе информацию о проведенных актуальных игр
 * на нем на данный момент
 */
public interface GameServer {

    /**
     * Получить информацию о сервере на текущий момент.
     */
    ServerInfo getServerInfo();

    /**
     * Получить наилучшую выбранную активную игру
     * для входа на сервер.
     */
    Optional<ActiveGame> getBetterGameForJoin();

    /**
     * Получить список доступных активных игр
     */
    List<ActiveGame> getActiveGames();
}
