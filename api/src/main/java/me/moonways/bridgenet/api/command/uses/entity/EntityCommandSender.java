package me.moonways.bridgenet.api.command.uses.entity;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Данный интерфейс реализуется всеми возможными
 * в системе отправителями команд.
 */
public interface EntityCommandSender {

    /**
     * Отправить отправителю сообщение.
     * @param message - сообщение.
     */
    void sendMessage(@Nullable String message);

    /**
     * Отправить отправителю форматированное сообщение.
     *
     * @param message - сообщение.
     * @param replacements - параметры для вызова String#format()
     */
    void sendMessage(String message, @Nullable Object... replacements);

    /**
     * Проверка на наличие конкретного права у отправителя.
     * @param permission - право
     */
    boolean hasPermission(String permission);

    /**
     * Получить тип сущности.
     */
    EntitySenderType getType();

    /**
     * Получить имя сущности.
     */
    String getName();

    /**
     * Проверить является ли сущность требуемой.
     *
     * @param entityType - тип сущности.
     * @return - true/false.
     */
    boolean isInstanceOf(EntitySenderType entityType);

    /**
     * Получить UUID сущности.
     */
    UUID getUuid();

}
