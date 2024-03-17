package me.moonways.endpoint.socials.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.model.socials.SocialProfile;
import me.moonways.bridgenet.model.socials.result.SocialBindingResult;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class SocialNetworkLinkageResult {

    private final SocialProfile profile;
    private final SocialBindingResult result;
}
