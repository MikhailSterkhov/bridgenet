package me.moonways.endpoint.permissions;

import me.moonways.bridgenet.model.service.permissions.PermissionsServiceModel;
import me.moonways.bridgenet.model.service.permissions.group.GroupsManager;
import me.moonways.bridgenet.model.service.permissions.permission.PermissionsManager;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import me.moonways.endpoint.permissions.manager.GroupsManagerStub;
import me.moonways.endpoint.permissions.manager.PermissionsManagerStub;

public final class PermissionsServiceEndpoint extends EndpointServiceObject implements PermissionsServiceModel {
    private final GroupsManager groupsManager = new GroupsManagerStub();
    private final PermissionsManager permissionsManager = new PermissionsManagerStub();

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.inject(groupsManager);
        context.inject(permissionsManager);
    }

    @Override
    public GroupsManager getGroups() {
        return groupsManager;
    }

    @Override
    public PermissionsManager getPermissions() {
        return permissionsManager;
    }
}
