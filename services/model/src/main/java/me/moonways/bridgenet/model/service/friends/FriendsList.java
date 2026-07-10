package me.moonways.bridgenet.model.service.friends;

import java.util.Set;
import java.util.UUID;

public interface FriendsList {

    boolean addFriend(UUID uuid);

    boolean addFriend(String name);

    boolean removeFriend(UUID uuid);

    boolean removeFriend(String name);

    boolean hasFriend(UUID uuid);

    boolean hasFriend(String name);

    Set<UUID> getFriendsIDs();

    Set<String> getFriendsNames();
}
