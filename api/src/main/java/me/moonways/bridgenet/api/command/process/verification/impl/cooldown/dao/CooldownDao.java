package me.moonways.bridgenet.api.command.process.verification.impl.cooldown.dao;

import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.CooldownInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Данный интерфейс предназначен для управления интервалами.
 */
public interface CooldownDao {

    /**
     * Получение информации о кд.
     *
     * @param commandName - имя команды.
     * @param entityName - имя сущности.
     */
    CooldownInfo get(@NotNull String commandName, @NotNull String entityName);

    /**
     * Получение списка всех информаций о кд сущностей.
     *
     * @param commandName - имя команды.
     */
    Set<CooldownInfo> get(@NotNull String commandName);

    /**
     * Удаление кд.
     *
     * @param commandName - имя команды.
     * @param entityName - имя сущности.
     */
    void remove(@NotNull String commandName, @NotNull String entityName);

    /**
     * Установка кд.
     *
     * @param commandName - имя команды.
     * @param entityName - имя сущности.
     * @param duration - число.
     * @param time - тип времени (миллисекунду/секунда/минута/час/день).
     */
    void set(@NotNull String commandName, @NotNull String entityName, long duration, @NotNull TimeUnit time);

    /**
     * Проверка на истечение кд.
     *
     * @param commandName - имя команды.
     * @param entityName - имя сущности.
     */
    boolean isExpired(@NotNull String commandName, @NotNull String entityName);

    /**
     * Удаление всех кд.
     */
    void removeAll();

    /**
     * Удаление всех кд.
     *
     * @param commandName - имя команды.
     */
    void removeAll(@NotNull String commandName);
}
