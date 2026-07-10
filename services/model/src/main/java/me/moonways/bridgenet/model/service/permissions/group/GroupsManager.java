package me.moonways.bridgenet.model.service.permissions.group;

import me.moonways.bridgenet.model.event.PlayerGroupUpdateEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Интерфейс представляет собой удаленный интерфейс
 * для управления группами прав игроков.
 * Этот интерфейс позволяет получать информацию о различных
 * типах групп, а также устанавливать и проверять группы
 * для конкретных игроков.
 */
public interface GroupsManager {

    /**
     * @return Список всех групп разрешений.
     */
    List<PermissionGroup> getTotalGroups();

    /**
     * @return Список групп, связанных с пожертвованиями.
     */
    List<PermissionGroup> getDonateGroups();

    /**
     * @return Список временных групп разрешений.
     */
    List<PermissionGroup> getTemporalGroups();

    /**
     * @return Список коммерческих групп разрешений.
     */
    List<PermissionGroup> getCommercialGroups();

    /**
     * @return Список персональных групп разрешений.
     */
    List<PermissionGroup> getPersonalGroups();

    /**
     * @return Список технических персональных групп разрешений.
     */
    List<PermissionGroup> getTechPersonalGroups();

    /**
     * @return Список групп владельцев.
     */
    List<PermissionGroup> getOwnerGroups();

    /**
     * Получить группу по идентификатору.
     *
     * @param groupId - идентификатор группы.
     * @return - содержащий группу, если она найдена, иначе пусто.
     */
    Optional<PermissionGroup> getGroup(int groupId);

    /**
     * Получить группу по имени.
     *
     * @param groupName - имя группы.
     * @return - содержащий группу, если она найдена, иначе пусто.
     */
    Optional<PermissionGroup> getGroup(String groupName);

    /**
     * Получить группу игрока по его имени.
     *
     * @param playerName - имя игрока.
     * @return - содержащий группу игрока, если она найдена, иначе пусто.
     */
    Optional<PermissionGroup> getPlayerGroup(String playerName);

    /**
     * Получить группу игрока по его идентификатору.
     *
     * @param playerId - идентификатор игрока.
     * @return - содержащий группу игрока, если она найдена, иначе пусто.
     */
    Optional<PermissionGroup> getPlayerGroup(UUID playerId);

    /**
     * Установить группу для игрока по его имени.
     *
     * @param playerName - имя игрока.
     * @param group      - выдаваемая группа.
     * @return - содержащий объект события обновления группы игрока, если установка прошла успешно, иначе пусто.
     */
    Optional<PlayerGroupUpdateEvent> setPlayerGroup(String playerName, PermissionGroup group);

    /**
     * Установить группу для игрока по его идентификатору.
     *
     * @param playerId - идентификатор игрока.
     * @param group    - выдаваемая группа.
     * @return - содержащий объект события обновления группы игрока, если установка прошла успешно, иначе пусто.
     */
    Optional<PlayerGroupUpdateEvent> setPlayerGroup(UUID playerId, PermissionGroup group);

    /**
     * Получить группу по умолчанию.
     */
    PermissionGroup getDefault();

    /**
     * Проверить, является ли игрок группой по умолчанию.
     *
     * @param playerName - имя игрока.
     */
    boolean isDefault(String playerName);

    /**
     * Проверить, является ли игрок группой по умолчанию.
     *
     * @param playerId - идентификатор игрока.
     */
    boolean isDefault(UUID playerId);

    /**
     * Проверить, имеет ли игрок пожертвования.
     *
     * @param playerName - имя игрока.
     */
    boolean isDonated(String playerName);

    /**
     * Проверить, имеет ли игрок пожертвования.
     *
     * @param playerId - идентификатор игрока.
     */
    boolean isDonated(UUID playerId);

    /**
     * Проверить, является ли игрок персональной группой.
     *
     * @param playerName - имя игрока.
     */
    boolean isPersonal(String playerName);

    /**
     * Проверить, является ли игрок персональной группой.
     *
     * @param playerId - идентификатор игрока.
     */
    boolean isPersonal(UUID playerId);

    /**
     * Проверить, является ли игрок технической персональной группой.
     *
     * @param playerName - имя игрока.
     */
    boolean isTechPersonal(String playerName);

    /**
     * Проверить, является ли игрок технической персональной группой.
     *
     * @param playerId - идентификатор игрока.
     */
    boolean isTechPersonal(UUID playerId);

    /**
     * Проверить, является ли игрок группой владельцев.
     *
     * @param playerName - имя игрока.
     */
    boolean isOwner(String playerName);

    /**
     * Проверить, является ли игрок группой владельцев.
     *
     * @param playerId - идентификатор игрока.
     */
    boolean isOwner(UUID playerId);
}
