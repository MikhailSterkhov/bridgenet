package me.moonways.endpoint.socials;

import lombok.Getter;
import me.moonways.bridgenet.model.socials.Social;
import me.moonways.bridgenet.model.socials.SocialProfile;
import me.moonways.bridgenet.rsi.endpoint.persistance.EndpointRemoteObject;

import java.rmi.RemoteException;
import java.util.UUID;

@Getter
public class SocialProfileStub extends EndpointRemoteObject implements SocialProfile {

    private static final long serialVersionUID = -3765545652331893850L;

    private final UUID playerId;
    private final Social socialType;
    private final String socialId;
    private final String socialLink;

    public SocialProfileStub(
            UUID playerId,
            Social socialType,
            String socialId,
            String socialLink
    ) throws RemoteException {
        super();

        this.playerId = playerId;
        this.socialType = socialType;
        this.socialId = socialId;
        this.socialLink = socialLink;
    }
}
