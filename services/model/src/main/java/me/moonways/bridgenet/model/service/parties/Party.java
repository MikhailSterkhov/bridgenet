package me.moonways.bridgenet.model.service.parties;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface Party {

    PartyOwner getOwner();

    PartyMembersContainer getMembersContainer();

    void setOwner(@NotNull PartyOwner newOwner);

    long getTimeOfCreated(@NotNull TimeUnit unit);

    int getTotalMembersCount();
}
