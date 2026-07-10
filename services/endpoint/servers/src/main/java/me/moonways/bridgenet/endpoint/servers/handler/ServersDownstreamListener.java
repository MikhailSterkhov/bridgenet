package me.moonways.bridgenet.endpoint.servers.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.event.SubscribeEvent;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.endpoint.servers.ServersContainer;
import me.moonways.bridgenet.model.event.ServerDisconnectEvent;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.mtp.event.ChannelDownstreamEvent;

import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public class ServersDownstreamListener {

    private final ServersContainer container;

    @Inject
    private EventService eventService;

    private void callServerDisconnectEvent(EntityServer entityServer) {
        eventService.fireEvent(
                ServerDisconnectEvent.builder()
                        .downstreamType(ServerDisconnectEvent.DownstreamType.DOWNSTREAM)
                        .server(entityServer)
                        .build());
    }

    @SubscribeEvent
    public void handle(ChannelDownstreamEvent event) {
        BridgenetNetworkChannel channel = event.getChannel();

        Optional<EntityServer> serverProperty = channel.getProperty(EntityServer.CHANNEL_PROPERTY);

        if (serverProperty.isPresent()) {
            EntityServer entityServer = serverProperty.get();

            log.info("Server §c\"{}\" {{}} §rhas disconnected: DOWNSTREAM", entityServer.getName(), entityServer.getUniqueId());

            callServerDisconnectEvent(entityServer);
            container.unregisterServer(entityServer.getUniqueId());
        }
    }
}
