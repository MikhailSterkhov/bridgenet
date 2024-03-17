package me.moonways.endpoint.socials.api;

import java.util.function.Consumer;

public interface SocialNetworkBot {

    void requestAccountLink(SocialNetworkAccount account, Consumer<SocialNetworkLinkageResult> consumer);

    void requestAccountUnlink(SocialNetworkAccount account, Consumer<SocialNetworkLinkageResult> consumer);

    void dispatchChat(SocialNetworkAccount account, String message);
}
