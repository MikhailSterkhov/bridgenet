package me.moonways.bridgenet.model.service.players;

import me.moonways.bridgenet.model.audience.EntityAudience;
import me.moonways.bridgenet.model.service.language.Language;
import me.moonways.bridgenet.model.service.permissions.group.PermissionGroup;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;

import java.util.Set;
import java.util.UUID;

/**
 * Представляет собой удаленный интерфейс д
 * ля управления офлайн игроком в сетевой игре.
 * Этот интерфейс определяет методы для получения идентификатора
 * и имени офлайн игрока, его соединения с сервером,
 * списка разрешений, группы разрешений, уровня игрока и опыта.
 */
public interface OfflinePlayer extends EntityAudience {

    /**
     * Получить идентификатор офлайн игрока.
     */
    UUID getId();

    /**
     * Получить имя офлайн игрока.
     */
    String getName();

    /**
     * Получить список разрешений офлайн игрока.
     */
    Set<Permission> getPermissions();

    /**
     * Получить группу разрешений офлайн игрока.
     */
    PermissionGroup getGroup();

    /**
     * Получить выбранный пользователем тип мирового языка.
     */
    Language getLanguage();

    /**
     * Получить уровень офлайн игрока.
     */
    int getLevel();

    /**
     * Получить общий опыт офлайн игрока.
     */
    int getTotalExperience();

    /**
     * Получить текущий опыт офлайн игрока.
     */
    int getExperience();

    /**
     * Получить количество опыта, необходимое для достижения
     * следующего уровня офлайн игрока.
     */
    int getExperienceToNextLevel();

    /**
     * Проверить сущность игрока на то, актуален
     * ли для него сейчас статус "в сети".
     */
    boolean isOnline();
}
