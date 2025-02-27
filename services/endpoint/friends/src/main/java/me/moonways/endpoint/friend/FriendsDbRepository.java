package me.moonways.endpoint.friend;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;

import java.util.List;
import java.util.UUID;

public class FriendsDbRepository {

    @Inject
    private EntityRepositoryFactory repositoryFactory;

    private EntityRepository<EntityFriend> friendPairEntityDao;

    public EntityRepository<EntityFriend> getRepository() {
        if (friendPairEntityDao == null) {
            friendPairEntityDao = repositoryFactory.fromEntityType(EntityFriend.class);
        }
        return friendPairEntityDao;
    }

    public List<UUID> findFriendsList(UUID playerID) {
        EntityRepository<EntityFriend> friendsPairRepository = getRepository();
        return friendsPairRepository.search(friendsPairRepository.beginCriteria()
                        .andEquals(EntityFriend::getPlayerID, playerID))
                .mapEach(EntityFriend::getFriendID)
                .blockAll();
    }

    public void addFriend(EntityFriend pair) {
        EntityRepository<EntityFriend> friendsPairRepository = getRepository();
        friendsPairRepository.insert(pair, reverse(pair));
    }

    public void removeFriend(EntityFriend pair) {
        EntityRepository<EntityFriend> friendsPairRepository = getRepository();
        friendsPairRepository.delete(pair, reverse(pair));
    }

    private EntityFriend reverse(EntityFriend pair) {
        return EntityFriend.builder()
                .playerID(pair.getFriendID())
                .friendID(pair.getPlayerID())
                .build();
    }
}
