package me.moonways.bridgenet.model.service.permissions;

import me.moonways.bridgenet.model.service.permissions.group.GroupsManager;
import me.moonways.bridgenet.model.service.permissions.permission.PermissionsManager;

public interface PermissionsServiceModel {

    /**
     * Получить менеджер управления группами прав
     * относительно пользователей, отдельного репозитория базы
     * данных и списка всевозможных зарегистрированных групп.
     */
    GroupsManager getGroups();

    /**
     * Получить менеджер управления индивидуальными
     * правами доступа относительно пользователей и
     * отдельного репозитория базы данных.
     */
    PermissionsManager getPermissions();
}
