package me.moonways.endpoint.permissions.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;
import me.moonways.bridgenet.model.event.PlayerGroupUpdateEvent;
import me.moonways.bridgenet.model.service.permissions.group.GroupTypes;
import me.moonways.bridgenet.model.service.permissions.group.GroupsManager;
import me.moonways.bridgenet.model.service.permissions.group.PermissionGroup;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.endpoint.permissions.PermissionsEndpointException;
import me.moonways.endpoint.permissions.entity.EntityGroup;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class GroupsManagerStub implements GroupsManager {

    private static List<PermissionGroup> filteredGroupsList(Predicate<PermissionGroup> predicate) {
        return Collections.unmodifiableList(
                GroupTypes.ORDERED_GROUPS_LIST.stream()
                        .filter(predicate)
                        .collect(Collectors.toList()));
    }

    @Inject
    private EventService eventService;
    @Inject
    private PlayersServiceModel playersServiceModel;
    @Inject
    private EntityRepositoryFactory repositoryFactory;

    private final Cache<UUID, PermissionGroup> playersGroupsCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(5, TimeUnit.HOURS)
                    .build();

    private Optional<PermissionGroup> findPlayerGroup(UUID playerId) {
        EntityRepository<EntityGroup> repository = repositoryFactory.fromEntityType(EntityGroup.class);
        return repository.searchFirst(
                        repository.beginCriteria()
                                .andEquals(EntityGroup::getPlayerId, playerId))
                .flatMap(entityGroup -> entityGroup.isExpired() ? getGroup(entityGroup.getGroupId()) : Optional.empty())
                .blockOptional();
    }

    private void updateGroup(UUID playerId, PermissionGroup group) {
        EntityRepository<EntityGroup> repository = repositoryFactory.fromEntityType(EntityGroup.class);

        EntityGroup entity = EntityGroup.fromGroup(playerId, group);

        repository.delete(entity);
        repository.insert(entity);
    }

    @Override
    public List<PermissionGroup> getTotalGroups() {
        return Collections.unmodifiableList(GroupTypes.ORDERED_GROUPS_LIST);
    }

    @Override
    public List<PermissionGroup> getDonateGroups() {
        return filteredGroupsList(PermissionGroup::isDonate);
    }

    @Override
    public List<PermissionGroup> getTemporalGroups() {
        return filteredGroupsList(permissionGroup -> permissionGroup.getTemporalState().isEnabled());
    }

    @Override
    public List<PermissionGroup> getCommercialGroups() {
        return filteredGroupsList(PermissionGroup::isCommercialPersonal);
    }

    @Override
    public List<PermissionGroup> getPersonalGroups() {
        return filteredGroupsList(PermissionGroup::isPersonal);
    }

    @Override
    public List<PermissionGroup> getTechPersonalGroups() {
        return filteredGroupsList(PermissionGroup::isTechPersonal);
    }

    @Override
    public List<PermissionGroup> getOwnerGroups() {
        return filteredGroupsList(PermissionGroup::isOwner);
    }

    @Override
    public Optional<PermissionGroup> getGroup(int groupId) {
        return filteredGroupsList(group -> group.getId() == groupId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PermissionGroup> getGroup(String groupName) {
        return filteredGroupsList(group -> group.getName().equalsIgnoreCase(groupName))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PermissionGroup> getPlayerGroup(String playerName) {
        return getPlayerGroup(playersServiceModel.store().idByName(playerName));
    }

    @Override
    public Optional<PermissionGroup> getPlayerGroup(UUID playerId) {
        playersGroupsCache.cleanUp();

        PermissionGroup cached = playersGroupsCache.getIfPresent(playerId);

        if (cached == null) {
            PermissionGroup playerGroup = findPlayerGroup(playerId).orElseGet(this::getDefault);
            playersGroupsCache.put(playerId, cached = playerGroup);
        }

        return Optional.of(cached);
    }

    @Override
    public Optional<PlayerGroupUpdateEvent> setPlayerGroup(String playerName, PermissionGroup group) {
        UUID playerId = playersServiceModel.store().idByName(playerName);
        return setPlayerGroup(playerId, group);
    }

    @Override
    public Optional<PlayerGroupUpdateEvent> setPlayerGroup(UUID playerId, PermissionGroup group) {
        PermissionGroup previous = getPlayerGroup(playerId)
                .orElseGet(this::getDefault);

        if (previous.equals(group)) {
            return Optional.empty();
        }

        updateGroup(playerId, group);
        playersGroupsCache.put(playerId, group);

        PlayerGroupUpdateEvent event =
                PlayerGroupUpdateEvent.builder()
                        .playerId(playerId)
                        .previousGroup(previous)
                        .newestGroup(group)
                        .build();

        eventService.fireEvent(event);
        return Optional.of(event);
    }

    @Override
    public PermissionGroup getDefault() {
        return filteredGroupsList(PermissionGroup::isDefault)
                .stream()
                .findFirst()
                .orElseThrow(() -> new PermissionsEndpointException("no default group"));
    }

    @Override
    public boolean isDefault(String playerName) {
        return getPlayerGroup(playerName).map(PermissionGroup::isDefault).orElse(false);
    }

    @Override
    public boolean isDefault(UUID playerId) {
        return getPlayerGroup(playerId).map(PermissionGroup::isDefault).orElse(false);
    }

    @Override
    public boolean isDonated(String playerName) {
        return getPlayerGroup(playerName).map(PermissionGroup::isDonate).orElse(false);
    }

    @Override
    public boolean isDonated(UUID playerId) {
        return getPlayerGroup(playerId).map(PermissionGroup::isDonate).orElse(false);
    }

    @Override
    public boolean isPersonal(String playerName) {
        return getPlayerGroup(playerName).map(PermissionGroup::isPersonal).orElse(false);
    }

    @Override
    public boolean isPersonal(UUID playerId) {
        return getPlayerGroup(playerId).map(PermissionGroup::isPersonal).orElse(false);
    }

    @Override
    public boolean isTechPersonal(String playerName) {
        return getPlayerGroup(playerName).map(PermissionGroup::isTechPersonal).orElse(false);
    }

    @Override
    public boolean isTechPersonal(UUID playerId) {
        return getPlayerGroup(playerId).map(PermissionGroup::isTechPersonal).orElse(false);
    }

    @Override
    public boolean isOwner(String playerName) {
        return getPlayerGroup(playerName).map(PermissionGroup::isOwner).orElse(false);
    }

    @Override
    public boolean isOwner(UUID playerId) {
        return getPlayerGroup(playerId).map(PermissionGroup::isOwner).orElse(false);
    }
}
