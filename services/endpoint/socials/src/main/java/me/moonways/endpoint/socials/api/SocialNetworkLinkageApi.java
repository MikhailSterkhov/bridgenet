package me.moonways.endpoint.socials.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SocialNetworkLinkageApi {

    boolean verify(String input);

    CompletableFuture<SocialNetworkLinkageResult> link(UUID playerId, String input);

    CompletableFuture<SocialNetworkLinkageResult> unlink(UUID playerId, String input);
}
