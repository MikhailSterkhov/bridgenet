package me.moonways.endpoint.mojang;

import me.moonways.bridgenet.model.service.mojang.MojangServiceModel;
import me.moonways.bridgenet.model.service.mojang.Skin;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;

import java.util.Optional;

public final class MojangServiceEndpoint extends EndpointServiceObject implements MojangServiceModel {
    private final MojangApi mojangApi = new MojangApi();

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.bind(mojangApi);
    }

    @Override
    public boolean isPirateNick(String nickname) {
        return !mojangApi.getId(nickname).isPresent();
    }

    @Override
    public boolean isPirateId(String id) {
        return !mojangApi.getNick(id).isPresent();
    }

    @Override
    public Optional<String> getNameWithOriginCase(String nickname) {
        Optional<String> minecraftIdOptional = getMinecraftId(nickname);
        if (minecraftIdOptional.isPresent()) {
            return getMinecraftNick(minecraftIdOptional.get());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getMinecraftId(String nickname) {
        return mojangApi.getId(nickname);
    }

    @Override
    public Optional<String> getMinecraftNick(String id) {
        return mojangApi.getNick(id);
    }

    @Override
    public Optional<Skin> getMinecraftSkinByNick(String nickname) {
        Optional<String> minecraftIdOptional = getMinecraftId(nickname);
        if (minecraftIdOptional.isPresent()) {
            return getMinecraftSkinById(minecraftIdOptional.get());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Skin> getMinecraftSkinById(String id) {
        return mojangApi.getProfile(id).map(profile ->
                Skin.builder()
                        .id(profile.getId())
                        .nickname(profile.getName())
                        .texture(profile.getProperties()[0].getValue())
                        .signature(profile.getProperties()[0].getSignature())
                        .build());
    }
}
