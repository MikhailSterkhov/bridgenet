package me.moonways.endpoint.friend;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.friends.FriendsList;
import me.moonways.bridgenet.model.service.friends.FriendsServiceModel;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import me.moonways.endpoint.friend.event.FriendActivityListener;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Getter
public final class FriendsServiceEndpoint extends EndpointServiceObject implements FriendsServiceModel {

    private final Cache<UUID, FriendsList> playerFriendsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final FriendsDbRepository repository = new FriendsDbRepository();

    @Inject
    private PlayersServiceModel playersServiceModel;

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.registerEventListener(new FriendActivityListener());
    }

    private FriendsList lookupPlayerFriends(UUID playerUUID) {
        List<UUID> friendsList = repository.findFriendsList(playerUUID);
        FriendsListStub friendsListStub = new FriendsListStub(
                playerUUID,
                repository,
                new HashSet<>(friendsList));

        getEndpointContext().inject(friendsListStub);

        return friendsListStub;
    }

    @Override
    public FriendsList getFriends(UUID playerUUID) {
        playerFriendsCache.cleanUp();
        ConcurrentMap<UUID, FriendsList> cacheMap = playerFriendsCache.asMap();

        if (cacheMap.containsKey(playerUUID)) {
            return cacheMap.get(playerUUID);
        }

        FriendsList friendsList = lookupPlayerFriends(playerUUID);
        playerFriendsCache.put(playerUUID, friendsList);

        return friendsList;
    }

    @Override
    public FriendsList getFriends(String playerName) {
        UUID playerId = playersServiceModel.store().idByName(playerName);
        return getFriends(playerId);
    }
}
