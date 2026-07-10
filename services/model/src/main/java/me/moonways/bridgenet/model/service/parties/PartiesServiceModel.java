package me.moonways.bridgenet.model.service.parties;

import org.jetbrains.annotations.NotNull;


public interface PartiesServiceModel {

    void registerParty(@NotNull Party party);

    void unregisterParty(@NotNull Party party);

    Party createParty(@NotNull String ownerName);

    Party getRegisteredParty(@NotNull String memberName);

    Party createParty(@NotNull String ownerName, @NotNull String... firstMembersNames);

    boolean isMemberOf(@NotNull Party party, @NotNull String playerName);

    boolean hasParty(@NotNull String playerName);
}
