package me.moonways.bridgenet.model.audience;

import me.moonways.bridgenet.model.event.AudienceSendEvent;
import me.moonways.bridgenet.model.service.language.Message;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Данный интерфейс реализуется всеми возможными
 * в системе отправителями команд.
 */
public interface EntityAudience {

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - сущность сообщения.
     */
    Optional<AudienceSendEvent> sendMessage(@NotNull Component message);

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - сущность сообщения.
     */
    Optional<AudienceSendEvent> sendMessage(@NotNull Message message);

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - текст сообщения.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    Optional<AudienceSendEvent> sendMessage(@Nullable String message);

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - сущность сообщения.
     * @param holders - переменные, меняющие текстовый образ сообщения.
     */
    Optional<AudienceSendEvent> sendMessage(@NotNull Component message, @NotNull ComponentHolders holders);

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - сущность сообщения.
     * @param holders - переменные, меняющие текстовый образ сообщения.
     */
    Optional<AudienceSendEvent> sendMessage(@NotNull Message message, @NotNull ComponentHolders holders);

    /**
     * Отправить отправителю сообщение.
     *
     * @param message - текст сообщения.
     * @param holders - переменные, меняющие текстовый образ сообщения.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    Optional<AudienceSendEvent> sendMessage(@Nullable String message, @NotNull ComponentHolders holders);

    /**
     * Проверка на наличие конкретного права у отправителя.
     *
     * @param permission - право доступа
     */
    boolean hasPermission(@NotNull Permission permission);

    /**
     * Проверка на наличие конкретного права у отправителя.
     *
     * @param permissionName - название права доступа
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    boolean hasPermission(@NotNull String permissionName);
}
