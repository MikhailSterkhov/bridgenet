package me.moonways.endpoint.parties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.parties.Party;
import me.moonways.bridgenet.model.service.parties.PartyMembersContainer;
import me.moonways.bridgenet.model.service.parties.PartyOwner;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@ToString
@EqualsAndHashCode(callSuper = false)
public class PartyStub extends EndpointServiceObject implements Party {

    private static final long serialVersionUID = 3205875496897748444L;

    @Getter
    private final PartyMembersContainerStub membersList = new PartyMembersContainerStub(this);

    @Getter
    private PartyOwner owner;

    private final long createdTimeMillis = System.currentTimeMillis();

    public PartyStub() {
        super();
    }

    private void validateNull(PartyOwner owner) {
        if (owner == null) {
            throw new NullPointerException("party owner");
        }
    }

    @Override
    public void setOwner(@NotNull PartyOwner newOwner) {
        validateNull(newOwner);

        PartyOwner previousOwner = this.owner;
        this.owner = newOwner;

        if (previousOwner == null) {
            return;
        }

        membersList.add(previousOwner);
        membersList.remove(newOwner);
    }

    @Override
    public long getTimeOfCreated(@NotNull TimeUnit unit) {
        return unit.convert(createdTimeMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public int getTotalMembersCount() {
        return membersList.size();
    }

    @Override
    public PartyMembersContainer getMembersContainer() {
        return membersList;
    }
}
