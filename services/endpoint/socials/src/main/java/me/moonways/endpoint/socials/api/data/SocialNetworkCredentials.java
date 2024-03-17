package me.moonways.endpoint.socials.api.data;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class SocialNetworkCredentials {

    private final String id;
    private final String accessToken;
}
