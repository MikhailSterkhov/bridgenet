package me.moonways.endpoint.friend;

import lombok.ToString;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.FriendAddEvent;
import me.moonways.bridgenet.model.event.FriendRemoveEvent;
import me.moonways.bridgenet.model.service.friends.FriendsList;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ToString(onlyExplicitlyIncluded = true)
public class FriendsListStub extends EndpointServiceObject implements FriendsList {

    private static final long serialVersionUID = 8227026529064476222L;

    @ToString.Include
    private final UUID playerUUID;
    @ToString.Include
    private final Set<UUID> uuids;

    private final FriendsDbRepository repository;

    @Inject
    private PlayersServiceModel playersModel;
    @Inject
    private EventService eventService;

    public FriendsListStub(UUID playerUUID, FriendsDbRepository repository, Set<UUID> uuids) {
        super();
        this.playerUUID = playerUUID;
        this.repository = repository;
        this.uuids = uuids;
    }

    @Override
    public boolean addFriend(UUID uuid) {
        boolean add = uuids.add(uuid);
        if (add) {
            repository.addFriend(EntityFriend.builder()
                    .playerID(playerUUID)
                    .friendID(uuid)
                    .build());

            eventService.fireEvent(
                    FriendAddEvent.builder()
                            .updatedFriendsList(this)
                            .player(playersModel.store().getOffline(playerUUID))
                            .friend(playersModel.store().getOffline(uuid))
                            .build());
        }
        return add;
    }

    @Override
    public boolean addFriend(String name) {
        return addFriend(playersModel.store().idByName(name));
    }

    @Override
    public boolean removeFriend(UUID uuid) {
        boolean add = uuids.remove(uuid);
        if (add) {
            repository.removeFriend(EntityFriend.builder()
                    .playerID(playerUUID)
                    .friendID(uuid)
                    .build());

            eventService.fireEvent(
                    FriendRemoveEvent.builder()
                            .updatedFriendsList(this)
                            .player(playersModel.store().getOffline(playerUUID))
                            .friend(playersModel.store().getOffline(uuid))
                            .build());
        }
        return add;
    }

    @Override
    public boolean removeFriend(String name) {
        return removeFriend(playersModel.store().idByName(name));
    }

    @Override
    public boolean hasFriend(UUID uuid) {
        return uuids.contains(uuid);
    }

    @Override
    public boolean hasFriend(String name) {
        return hasFriend(playersModel.store().idByName(name));
    }

    @Override
    public Set<UUID> getFriendsIDs() {
        return uuids;
    }

    @Override
    public Set<String> getFriendsNames() {
        return uuids.stream().map(uuid -> playersModel.store().nameById(uuid))
                .collect(Collectors.toSet());
    }
}
