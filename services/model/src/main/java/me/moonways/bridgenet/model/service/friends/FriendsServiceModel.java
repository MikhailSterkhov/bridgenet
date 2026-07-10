package me.moonways.bridgenet.model.service.friends;


import java.util.UUID;

public interface FriendsServiceModel {

    FriendsList getFriends(UUID playerUUID);

    FriendsList getFriends(String playerName);
}
