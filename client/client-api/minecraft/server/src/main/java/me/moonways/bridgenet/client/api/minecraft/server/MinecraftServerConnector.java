package me.moonways.bridgenet.client.api.minecraft.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.client.api.BridgenetClient;
import me.moonways.bridgenet.client.api.cloudnet.CloudNetDistributor;
import me.moonways.bridgenet.client.api.cloudnet.CloudNetWrapper;
import me.moonways.bridgenet.client.api.data.ClientDto;
import me.moonways.bridgenet.model.message.Handshake;

@Log4j2
@RequiredArgsConstructor
public final class MinecraftServerConnector extends BridgenetClient {

    @Inject
    private BeansService beansService;
    @Inject
    private CloudNetDistributor cloudNetDistributor;

    private final Object plugin;

    @Override
    protected ClientDto createClientInfo() {
        CloudNetWrapper cloudNetWrapper = cloudNetDistributor.getInstance();
        return ClientDto.builder()
                .name(cloudNetWrapper.getCurrentFullName())
                .host(cloudNetWrapper.getCurrentSnapshotHost())
                .port(cloudNetWrapper.getCurrentSnapshotPort())
                .build();
    }

    @Override
    public void onHandshake(Handshake.Result result) {
        result.onSuccess(() -> beansService.bind(plugin));
        result.onFailure(() -> log.info("§4Handshake failed: Server has already registered by {}", result.getKey()));
    }
}
