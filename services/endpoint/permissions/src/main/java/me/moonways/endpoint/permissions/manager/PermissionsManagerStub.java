package me.moonways.endpoint.permissions.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;
import me.moonways.bridgenet.model.event.PlayerPermissionAddEvent;
import me.moonways.bridgenet.model.event.PlayerPermissionRemoveEvent;
import me.moonways.bridgenet.model.service.permissions.TemporalState;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;
import me.moonways.bridgenet.model.service.permissions.permission.PermissionsManager;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.endpoint.permissions.entity.EntityPermission;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class PermissionsManagerStub implements PermissionsManager {

    private static Permission fromEntityPermission(EntityPermission entityPermission) {
        TemporalState temporalState = entityPermission.getExpirationTimeMillis() != null ?
                TemporalState.enabled(Duration.ofMillis(entityPermission.getExpirationTimeMillis() - System.currentTimeMillis())) :
                TemporalState.infinity();
        return new Permission(temporalState, entityPermission.getPermission());
    }

    @Inject
    private EventService eventService;
    @Inject
    private PlayersServiceModel playersServiceModel;
    @Inject
    private EntityRepositoryFactory repositoryFactory;

    private final Cache<UUID, Set<Permission>> playersPermissionsCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(5, TimeUnit.HOURS)
                    .build();

    private Set<Permission> findPermissions(UUID playerId) {
        EntityRepository<EntityPermission> repository = repositoryFactory.fromEntityType(EntityPermission.class);
        return repository.search(
                        repository.beginCriteria()
                                .andEquals(EntityPermission::getPlayerId, playerId))
                .subscribeEach(entityPermission -> {
                    if (entityPermission.isExpired()) {
                        deletePermission(playerId, fromEntityPermission(entityPermission));
                    }
                })
                .filter(entityPermission -> !entityPermission.isExpired())
                .mapEach(PermissionsManagerStub::fromEntityPermission)
                .blockAll(HashSet::new);
    }

    private void insertPermission(UUID playerId, Permission permission) {
        EntityRepository<EntityPermission> repository = repositoryFactory.fromEntityType(EntityPermission.class);
        repository.insert(EntityPermission.fromPermission(playerId, permission));
    }

    private void deletePermission(UUID playerId, Permission permission) {
        EntityRepository<EntityPermission> repository = repositoryFactory.fromEntityType(EntityPermission.class);
        repository.delete(EntityPermission.fromPermission(playerId, permission));
    }

    private void deleteAllPermissions(UUID playerId) {
        EntityRepository<EntityPermission> repository = repositoryFactory.fromEntityType(EntityPermission.class);
        repository.delete(
                repository.beginCriteria()
                        .andEquals(EntityPermission::getPlayerId, playerId));
    }

    @Override
    public Set<Permission> getActivePermissions(String playerName) {
        return getActivePermissions(playersServiceModel.store().idByName(playerName));
    }

    @Override
    public Set<Permission> getActivePermissions(UUID playerId) {
        playersPermissionsCache.cleanUp();
        Set<Permission> cachedPermissionsSet = playersPermissionsCache.getIfPresent(playerId);

        if (cachedPermissionsSet == null) {
            cachedPermissionsSet = findPermissions(playerId);
            playersPermissionsCache.put(playerId, cachedPermissionsSet);
        }

        return cachedPermissionsSet;
    }

    @Override
    public Optional<PlayerPermissionAddEvent> addPermission(String playerName, Permission permission) {
        return addPermission(playersServiceModel.store().idByName(playerName), permission);
    }

    @Override
    public Optional<PlayerPermissionAddEvent> addPermission(UUID playerId, Permission permission) {
        if (hasPermission(playerId, permission)) {
            return Optional.empty();
        }

        getActivePermissions(playerId)
                .add(permission);

        insertPermission(playerId, permission);

        PlayerPermissionAddEvent event =
                PlayerPermissionAddEvent.builder()
                        .playerId(playerId)
                        .permission(permission)
                        .build();

        eventService.fireEvent(event);
        return Optional.of(event);
    }

    @Override
    public Optional<PlayerPermissionAddEvent> addPermission(String playerName, String permissionName) {
        return addPermission(playerName, Permission.named(permissionName));
    }

    @Override
    public Optional<PlayerPermissionAddEvent> addPermission(UUID playerId, String permissionName) {
        return addPermission(playerId, Permission.named(permissionName));
    }

    @Override
    public Optional<PlayerPermissionRemoveEvent> removePermission(String playerName, Permission permission) {
        return removePermission(playersServiceModel.store().idByName(playerName), permission);
    }

    @Override
    public Optional<PlayerPermissionRemoveEvent> removePermission(UUID playerId, Permission permission) {
        if (!hasPermission(playerId, permission)) {
            return Optional.empty();
        }

        getActivePermissions(playerId)
                .remove(permission);

        deletePermission(playerId, permission);

        PlayerPermissionRemoveEvent event =
                PlayerPermissionRemoveEvent.builder()
                        .playerId(playerId)
                        .permission(permission)
                        .build();

        eventService.fireEvent(event);
        return Optional.of(event);
    }

    @Override
    public Optional<PlayerPermissionRemoveEvent> removePermission(String playerName, String permissionName) {
        return removePermission(playerName, Permission.named(permissionName));
    }

    @Override
    public Optional<PlayerPermissionRemoveEvent> removePermission(UUID playerId, String permissionName) {
        return removePermission(playerId, Permission.named(permissionName));
    }

    @Override
    public void clearPermissions(String playerName) {
        clearPermissions(playersServiceModel.store().idByName(playerName));
    }

    @Override
    public void clearPermissions(UUID playerId) {
        playersPermissionsCache.invalidate(playerId);
        deleteAllPermissions(playerId);
    }

    @Override
    public boolean hasPermission(String playerName, Permission permission) {
        return getActivePermissions(playerName).contains(permission);
    }

    @Override
    public boolean hasPermission(UUID playerId, Permission permission) {
        return getActivePermissions(playerId).contains(permission);
    }

    @Override
    public boolean hasPermission(String playerName, String permissionName) {
        return hasPermission(playerName, Permission.named(permissionName));
    }

    @Override
    public boolean hasPermission(UUID playerId, String permissionName) {
        return hasPermission(playerId, Permission.named(permissionName));
    }
}
