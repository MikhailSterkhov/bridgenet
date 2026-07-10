package me.moonways.bridgenet.test.services;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.friends.FriendsList;
import me.moonways.bridgenet.model.service.friends.FriendsServiceModel;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;


import static org.junit.Assert.assertEquals;

@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class FriendsServiceEndpointTest {

    @Inject
    private FriendsServiceModel friendsServiceModel;

    @Test
    @TestOrdered(1)
    public void test_friendAdd() {
        FriendsList friendsList = friendsServiceModel.getFriends(TestConst.Player.ID);
        friendsList.addFriend(TestConst.Friend.FRIEND_ID);

        assertEquals(1, friendsList.getFriendsIDs().size());
    }

    @Test
    @TestOrdered(2)
    public void test_friendRemove() {
        FriendsList friendsList = friendsServiceModel.getFriends(TestConst.Player.ID);
        friendsList.removeFriend(TestConst.Friend.FRIEND_ID);

        assertEquals(0, friendsList.getFriendsIDs().size());
    }
}
