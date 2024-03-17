package me.moonways.endpoint.socials.api;

import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.model.socials.Social;
import me.moonways.bridgenet.model.socials.result.SocialBindingResult;
import me.moonways.endpoint.socials.api.data.SocialNetworkDataDescriptor;
import me.moonways.endpoint.socials.api.data.SocialNetworkDataParser;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public abstract class AbstractSocialNetworkLinkageApi implements SocialNetworkLinkageApi {

    private final Social social;

    private SocialNetworkBot bot;

    @PostConstruct
    private void postConstruct() {
        SocialNetworkDataDescriptor descriptor = SocialNetworkDataParser.parseDescriptor(social);
        bot = start(descriptor);
    }

    protected abstract SocialNetworkBot start(SocialNetworkDataDescriptor descriptor);

    protected abstract SocialNetworkAccount findAccount(UUID playerId, String username);

    protected abstract String simplify(String playerInput);

    @Override
    public final CompletableFuture<SocialNetworkLinkageResult> link(UUID playerId, String input) {
        String username = simplify(input);
        return this.dispatchLink(playerId, username);
    }

    @Override
    public final CompletableFuture<SocialNetworkLinkageResult> unlink(UUID playerId, String input) {
        String username = simplify(input);
        return this.dispatchUnlink(playerId, username);
    }

    private CompletableFuture<SocialNetworkLinkageResult> dispatchLink(UUID playerId, String username) {
        CompletableFuture<SocialNetworkLinkageResult> future = new CompletableFuture<>();

        SocialNetworkAccount account = findAccount(playerId, username);
        if (account == null) {
            return CompletableFuture.completedFuture(
                    new SocialNetworkLinkageResult(null, SocialBindingResult.FAILURE_NEEDS_DATA)
            );
        }

        bot.requestAccountLink(account, future::complete);
        return future;
    }

    private CompletableFuture<SocialNetworkLinkageResult> dispatchUnlink(UUID playerId, String username) {
        CompletableFuture<SocialNetworkLinkageResult> future = new CompletableFuture<>();

        SocialNetworkAccount account = findAccount(playerId, username);
        if (account == null) {
            return CompletableFuture.completedFuture(
                    new SocialNetworkLinkageResult(null, SocialBindingResult.FAILURE_NEEDS_DATA)
            );
        }

        bot.requestAccountUnlink(account, future::complete);
        return future;
    }
}
