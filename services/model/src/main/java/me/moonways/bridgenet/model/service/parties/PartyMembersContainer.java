package me.moonways.bridgenet.model.service.parties;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PartyMembersContainer extends List<PartyMember> {

    PartyMember getMemberByName(@NotNull String name);

    PartyMember addMember(@NotNull String name);

    PartyMember removeMember(@NotNull String name);

    boolean hasMemberByName(@NotNull String name);
}
