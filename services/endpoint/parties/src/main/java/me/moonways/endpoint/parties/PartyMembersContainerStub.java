package me.moonways.endpoint.parties;

import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.PartyPlayerJoinEvent;
import me.moonways.bridgenet.model.event.PartyPlayerQuitEvent;
import me.moonways.bridgenet.model.service.parties.PartyMember;
import me.moonways.bridgenet.model.service.parties.PartyMembersContainer;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

@RequiredArgsConstructor
public class PartyMembersContainerStub extends ArrayList<PartyMember> implements PartyMembersContainer {

    private static final long serialVersionUID = 5483527348123762769L;

    private final PartyStub partyStub;

    @Inject
    private EventService eventService;
    @Inject
    private PlayersServiceModel playersServiceModel;

    private void validateNull(String playerName) {
        if (playerName == null) {
            throw new NullPointerException("player name");
        }
    }

    @Nullable
    @Override
    public PartyMember getMemberByName(@NotNull String name) {
        validateNull(name);
        return stream()
                .filter(partyMember -> partyMember.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @NotNull
    @Override
    public PartyMember addMember(@NotNull String name) {
        validateNull(name);
        PartyMember partyMember = new PartyMember(name, partyStub);

        super.add(partyMember);

        eventService.fireEvent(
                PartyPlayerJoinEvent.builder()
                        .party(partyStub)
                        .joinedPlayer(playersServiceModel.store().get(name).orElse(null))
                        .build());
        return partyMember;
    }

    @Nullable
    @Override
    public PartyMember removeMember(@NotNull String name) {
        validateNull(name);
        PartyMember memberByName = getMemberByName(name);

        if (memberByName == null) {
            return null;
        }

        super.remove(memberByName);

        eventService.fireEvent(
                PartyPlayerQuitEvent.builder()
                        .party(partyStub)
                        .leavedPlayer(playersServiceModel.store().get(name).orElse(null))
                        .build());

        return memberByName;
    }

    @Override
    public boolean hasMemberByName(@NotNull String name) {
        validateNull(name);
        return getMemberByName(name) == null;
    }
}
