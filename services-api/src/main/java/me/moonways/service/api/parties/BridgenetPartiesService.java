package me.moonways.service.api.parties;

import me.moonways.service.api.parties.party.Party;
import me.moonways.bridgenet.rsi.service.RemoteService;
import org.jetbrains.annotations.NotNull;

import java.rmi.RemoteException;

public interface BridgenetPartiesService extends RemoteService {

    void registerParty(@NotNull Party party) throws RemoteException;

    void unregisterParty(@NotNull Party party) throws RemoteException;

    Party createParty(@NotNull String ownerName) throws RemoteException;

    Party getRegisteredParty(@NotNull String memberName) throws RemoteException;

    Party createParty(@NotNull String ownerName, @NotNull String... firstMembersNames) throws RemoteException;

    boolean isMemberOf(@NotNull Party party, @NotNull String playerName) throws RemoteException;

    boolean hasParty(@NotNull String playerName) throws RemoteException;
}
