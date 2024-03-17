package me.moonways.endpoint.socials.api.data;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class SocialNetworkChat {

    private final String unknownCommandMessage;
    private final String offlineModeMessage;
    private final String onlineModeMessage;
    private final String linkageAlreadyAcceptedMessage;
    private final String linkageAcceptMessage;
    private final String linkageCancelMessage;
    private final String linkageAcceptButtonText;
    private final String linkageCancelButtonText;
    private final String linkageMessage;
}
