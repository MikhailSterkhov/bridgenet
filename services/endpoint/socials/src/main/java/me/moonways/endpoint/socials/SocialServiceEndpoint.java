package me.moonways.endpoint.socials;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import me.moonways.endpoint.socials.api.SocialNetworkLinkageApi;
import me.moonways.endpoint.socials.api.SocialNetworkLinkageResult;
import me.moonways.endpoint.socials.api.type.telegram.TelegramLinkageApi;
import me.moonways.endpoint.socials.api.type.vkontakte.VkontakteLinkageApi;
import me.moonways.endpoint.socials.database.PlayerSocialData;
import me.moonways.endpoint.socials.database.SocialDbRepository;
import me.moonways.bridgenet.model.socials.Social;
import me.moonways.bridgenet.model.socials.SocialProfile;
import me.moonways.bridgenet.model.socials.SocialsServiceModel;
import me.moonways.bridgenet.model.socials.result.SocialBindingResult;
import me.moonways.bridgenet.rsi.endpoint.persistance.EndpointRemoteContext;
import me.moonways.bridgenet.rsi.endpoint.persistance.EndpointRemoteObject;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SocialServiceEndpoint extends EndpointRemoteObject implements SocialsServiceModel {
    private static final long serialVersionUID = -3090645493989450037L;

    @EqualsAndHashCode
    @Builder
    private static class SocialPlayerID {

        private final UUID playerId;
        private final Social social;
    }

    private final Cache<SocialPlayerID, SocialProfile> socialProfileCache = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final SocialDbRepository socialDbRepository = new SocialDbRepository();
    private final Map<Social, SocialNetworkLinkageApi> socialsApisMap = Collections.synchronizedMap(new HashMap<>());

    public SocialServiceEndpoint() throws RemoteException {
        super();
    }

    private SocialPlayerID toSocialPlayerID(SocialProfile profile) throws RemoteException {
        return SocialPlayerID.builder()
                .social(profile.getSocialType())
                .playerId(profile.getPlayerId())
                .build();
    }

    @Override
    protected void construct(EndpointRemoteContext context) {
        socialsApisMap.put(Social.VKONTAKTE, new VkontakteLinkageApi());
        socialsApisMap.put(Social.TELEGRAM, new TelegramLinkageApi());
        // ...more
    }

    @Override
    public Optional<SocialProfile> findLinkedProfile(UUID playerId, Social social) throws RemoteException {
        SocialPlayerID socialPlayerID = SocialPlayerID.builder()
                .social(social)
                .playerId(playerId)
                .build();

        socialProfileCache.cleanUp();
        Map<SocialPlayerID, SocialProfile> cacheMap = socialProfileCache.asMap();

        if (cacheMap.containsKey(socialPlayerID)) {
            return Optional.of(cacheMap.get(socialPlayerID));
        }

        Optional<PlayerSocialData> socialOptional = socialDbRepository.findLinkedSocial(playerId, social);
        if (socialOptional.isPresent()) {
            PlayerSocialData descriptor = socialOptional.get();

            SocialProfileStub socialProfileStub = new SocialProfileStub(
                    descriptor.getPlayerId(),
                    Social.valueOf(descriptor.getSocialName()),
                    descriptor.getSocialId(),
                    descriptor.getSocialLink()
            );

            socialProfileCache.put(socialPlayerID, socialProfileStub);
            return Optional.of(socialProfileStub);
        }

        return Optional.empty();
    }

    @Override
    public Collection<Social> findSocialsByInput(String input) {
        List<Social> verified = new ArrayList<>();

        for (Map.Entry<Social, SocialNetworkLinkageApi> socialEntry : socialsApisMap.entrySet()) {
            if (socialEntry.getValue().verify(input)) {
                verified.add(socialEntry.getKey());
            }
        }

        return verified;
    }

    @Override
    public CompletableFuture<SocialBindingResult> tryLink(UUID playerId, Social social, String input) throws RemoteException {
        Optional<SocialProfile> profileOptional = findLinkedProfile(playerId, social);

        if (profileOptional.isPresent()) {
            return CompletableFuture.completedFuture(SocialBindingResult.FAILURE_ALREADY_LINKED);
        }

        SocialNetworkLinkageApi linkageApi = socialsApisMap.get(social);

        if (linkageApi == null) {
            return CompletableFuture.completedFuture(SocialBindingResult.FAILURE_SOCIAL_NOT_EXISTS);
        }
        if (!linkageApi.verify(input)) {
            return CompletableFuture.completedFuture(SocialBindingResult.FAILURE_UNCORRECTED_INPUT);
        }

        return linkageApi.link(playerId, input)
                .thenApply(this::postLink);
    }

    @Override
    public CompletableFuture<SocialBindingResult> tryUnlink(UUID playerId, Social social) throws RemoteException {
        Optional<SocialProfile> profileOptional = findLinkedProfile(playerId, social);

        if (!profileOptional.isPresent()) {
            return CompletableFuture.completedFuture(SocialBindingResult.FAILURE_NOT_LINKED);
        }

        SocialNetworkLinkageApi linkageApi = socialsApisMap.get(social);

        if (linkageApi == null) {
            return CompletableFuture.completedFuture(SocialBindingResult.FAILURE_SOCIAL_NOT_EXISTS);
        }

        String socialLink = profileOptional.get().getSocialLink();
        return linkageApi.unlink(playerId, socialLink)
                .thenApply(this::postUnlink);
    }

    private SocialBindingResult postLink(SocialNetworkLinkageResult result) {
        try {
            SocialBindingResult socialBindingResult = result.getResult();

            if (socialBindingResult == SocialBindingResult.SUCCESS) {
                SocialProfile profile = result.getProfile();

                if (socialDbRepository.findBySocialId(profile.getSocialId()).isPresent()) {
                    return SocialBindingResult.FAILURE_NOT_BELONG;
                }

                socialProfileCache.put(toSocialPlayerID(profile), profile);
                socialDbRepository.insertSocialLinkage(profile);
            }
            return socialBindingResult;
        }
        catch (RemoteException exception) {
            exception.printStackTrace();
            return SocialBindingResult.FAILURE_NO_CONNECTION;
        }
    }

    private SocialBindingResult postUnlink(SocialNetworkLinkageResult result) {
        try {
            SocialBindingResult socialBindingResult = result.getResult();

            if (socialBindingResult == SocialBindingResult.SUCCESS) {
                SocialProfile profile = result.getProfile();

                socialProfileCache.invalidate(toSocialPlayerID(profile));
                socialDbRepository.deleteSocialLinkage(profile);
            }
            return socialBindingResult;
        }
        catch (RemoteException exception) {
            exception.printStackTrace();
            return SocialBindingResult.FAILURE_NO_CONNECTION;
        }
    }
}
