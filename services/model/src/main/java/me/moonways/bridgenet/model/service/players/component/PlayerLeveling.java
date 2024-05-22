package me.moonways.bridgenet.model.service.players.component;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Представляет собой удаленный интерфейс для управления уровнем и опытом игрока в сетевой игре.
 * Этот интерфейс определяет методы для вычисления уровня игрока, общего опыта, опыта до следующего уровня и т. д.
 */
public interface PlayerLeveling extends Remote {

    /**
     * Вычислить уровень игрока на основе общего опыта.
     *
     * @param totalExperience Общий опыт игрока.
     * @return Уровень игрока.
     */
    int toLevel(int totalExperience) throws RemoteException;

    /**
     * Вычислить общий опыт игрока на основе его уровня.
     *
     * @param level - Уровень игрока.
     * @return - Общий опыт игрока.
     */
    int totalExperience(int level) throws RemoteException;

    /**
     * Вычислить количество опыта, необходимого для перехода на следующий уровень.
     *
     * @param level - Уровень игрока.
     * @return - Количество опыта до следующего уровня.
     */
    int experienceToNextLevel(int level) throws RemoteException;

    /**
     * Вычислить процент опыта, который игрок получил для достижения следующего уровня.
     *
     * @param totalExperience - Общий опыт игрока.
     * @return - Процент опыта до следующего уровня.
     */
    int experiencePercentToNextLevel(int totalExperience) throws RemoteException;

    /**
     * Вычислить общий опыт игрока на основе его уровня и опыта.
     *
     * @param level      - Уровень игрока.
     * @param experience - Опыт игрока.
     * @return - Общий опыт игрока.
     */
    int subtotal(int level, int experience) throws RemoteException;
}
