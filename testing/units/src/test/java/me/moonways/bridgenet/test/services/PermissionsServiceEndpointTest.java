package me.moonways.bridgenet.test.services;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.event.subscribe.EventSubscribeBuilder;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.PlayerGroupUpdateEvent;
import me.moonways.bridgenet.model.event.PlayerPermissionAddEvent;
import me.moonways.bridgenet.model.event.PlayerPermissionRemoveEvent;
import me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel;
import me.moonways.bridgenet.model.service.permissions.group.GroupTypes;
import me.moonways.bridgenet.model.service.permissions.group.GroupsManager;
import me.moonways.bridgenet.model.service.permissions.group.PermissionGroup;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;
import me.moonways.bridgenet.model.service.permissions.permission.PermissionsManager;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

@Log4j2
@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class PermissionsServiceEndpointTest {

    @Inject
    private PermissionsServiceModel serviceModel;

    @Inject
    private EventService eventService;

    @Test
    @TestOrdered(1)
    public void test_addPermission() {
        Optional<PlayerPermissionAddEvent> eventOptional = processPermissionAdd(TestConst.Permissions.TEMP_PERMISSION);
        assertTrue(eventOptional.isPresent());
    }

    @Test
    @TestOrdered(2)
    public void test_addPermissionDuplicate() {
        Optional<PlayerPermissionAddEvent> eventOptional = processPermissionAdd(TestConst.Permissions.PERMISSION);
        assertFalse(eventOptional.isPresent());
    }

    @Test
    @TestOrdered(3)
    public void test_getPermissions() {
        PermissionsManager permissions = serviceModel.getPermissions();

        Set<Permission> activePermissions = permissions.getActivePermissions(TestConst.Player.ID);

        assertNotNull(activePermissions);
        assertFalse(activePermissions.isEmpty());
    }

    @Test
    @TestOrdered(3)
    public void test_checkPermissions() {
        PermissionsManager permissions = serviceModel.getPermissions();

        assertTrue(permissions.hasPermission(TestConst.Player.ID, TestConst.Permissions.PERMISSION_NAME));
        assertTrue(permissions.hasPermission(TestConst.Player.ID, TestConst.Permissions.PERMISSION));
        assertTrue(permissions.hasPermission(TestConst.Player.ID, TestConst.Permissions.TEMP_PERMISSION));
    }

    @Test
    @TestOrdered(4)
    public void test_removePermission() {
        Optional<PlayerPermissionRemoveEvent> eventOptional = processPermissionRemove(TestConst.Permissions.PERMISSION);
        assertTrue(eventOptional.isPresent());
    }

    @Test
    @TestOrdered(5)
    public void test_removePermissionDuplicate() {
        Optional<PlayerPermissionRemoveEvent> eventOptional = processPermissionRemove(TestConst.Permissions.TEMP_PERMISSION);
        assertFalse(eventOptional.isPresent());
    }

    private Optional<PlayerPermissionAddEvent> processPermissionAdd(Permission permission) {
        eventService.subscribe(
                EventSubscribeBuilder.newBuilder(PlayerPermissionAddEvent.class)
                        .follow(log::debug)
                        .build());

        PermissionsManager permissions = serviceModel.getPermissions();
        return permissions.addPermission(TestConst.Player.ID, permission);
    }

    private Optional<PlayerPermissionRemoveEvent> processPermissionRemove(Permission permission) {
        eventService.subscribe(
                EventSubscribeBuilder.newBuilder(PlayerPermissionRemoveEvent.class)
                        .follow(log::debug)
                        .build());

        PermissionsManager permissions = serviceModel.getPermissions();
        return permissions.removePermission(TestConst.Player.ID, permission);
    }

    @Test
    @TestOrdered(6)
    public void test_getGroup() {
        GroupsManager groups = serviceModel.getGroups();

        Optional<PermissionGroup> permissionGroupOptional = groups.getPlayerGroup(TestConst.Player.ID);
        assertTrue(permissionGroupOptional.isPresent());

        PermissionGroup permissionGroup = permissionGroupOptional.get();

        assertTrue(permissionGroup.isDefault());
        assertTrue(groups.isDefault(TestConst.Player.ID));
    }

    @Test
    @TestOrdered(7)
    public void test_updateGroup() {
        eventService.subscribe(
                EventSubscribeBuilder.newBuilder(PlayerGroupUpdateEvent.class)
                        .follow(log::debug)
                        .build());

        GroupsManager groups = serviceModel.getGroups();

        Optional<PlayerGroupUpdateEvent> eventOptional = groups.setPlayerGroup(TestConst.Player.ID, GroupTypes.DEVELOPER);

        assertTrue(groups.isTechPersonal(TestConst.Player.ID));
        assertTrue(groups.isPersonal(TestConst.Player.ID));
        assertFalse(groups.isDefault(TestConst.Player.ID));

        assertTrue(eventOptional.isPresent());
        assertTrue(eventOptional.get().getPreviousGroup().isDefault());
    }
}
