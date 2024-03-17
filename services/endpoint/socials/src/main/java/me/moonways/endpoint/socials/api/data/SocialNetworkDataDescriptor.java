package me.moonways.endpoint.socials.api.data;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public final class SocialNetworkDataDescriptor {

    private final SocialNetworkChat chat;
    private final SocialNetworkCredentials credentials;
}
