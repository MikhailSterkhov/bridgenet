package me.moonways.bridgenet.endpoint.servers.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.endpoint.servers.ConnectedServerStub;
import me.moonways.bridgenet.endpoint.servers.ServersContainer;
import me.moonways.bridgenet.endpoint.servers.players.PlayersOnServersConnectionService;
import me.moonways.bridgenet.model.event.ServerDisconnectEvent;
import me.moonways.bridgenet.model.event.ServerHandshakeEvent;
import me.moonways.bridgenet.model.message.Disconnect;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.message.ProtocolVersion;
import me.moonways.bridgenet.model.message.Redirect;
import me.moonways.bridgenet.model.service.bus.HandshakePropertiesConst;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServerFlag;
import me.moonways.bridgenet.model.service.servers.ServerInfo;
import me.moonways.bridgenet.mtp.message.InboundMessageContext;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
@InboundMessageListener
public class ServersInputMessagesListener {

    private final ServersContainer container;

    @Inject
    private BeansService beansService;
    @Inject
    private EventService eventService;
    @Inject
    private PlayersOnServersConnectionService playersOnServersConnectionService;

    private void callServerDisconnectEvent(EntityServer entityServer) {
        eventService.fireEvent(
                ServerDisconnectEvent.builder()
                        .server(entityServer)
                        .downstreamType(ServerDisconnectEvent.DownstreamType.DISCONNECT_REQUEST)
                        .build());
    }

    private void callServerHandshakeEvent(Handshake handshake, EntityServer entityServer) {
        eventService.fireEvent(
                ServerHandshakeEvent.builder()
                        .handshake(handshake)
                        .server(entityServer)
                        .build());
    }

    @SubscribeMessage
    public void handleHandshake(InboundMessageContext<Handshake> input) {
        Handshake handshake = input.getMessage();

        if (handshake.getType() == Handshake.Type.SERVER) {

            if (handshake.getProtocolVersion() != ProtocolVersion.CURRENT) {
                log.error("§4Handshake rejected: protocol version mismatch (server={}, client={})",
                        ProtocolVersion.CURRENT, handshake.getProtocolVersion());
                input.callback(new Handshake.Failure());
                return;
            }

            ServerInfo serverInfo = toServerInfo(handshake.getProperties());
            registerServer(input, serverInfo);
        }
    }

    @SubscribeMessage
    public void handleRedirection(InboundMessageContext<Redirect> input) {
        Redirect redirect = input.getMessage();

        UUID playerUUID = redirect.getPlayerUUID();
        UUID serverId = redirect.getServerKey();

        ConnectedServerStub server = container.getConnectedServerExact(serverId);

        if (server == null) {
            log.debug("§4Server by key '{}' is not connected", serverId);
            return;
        }

        UUID currentServerId = playersOnServersConnectionService.getPlayerCurrentServerKey(playerUUID)
                .orElse(null);

        if (!serverId.equals(currentServerId)) {
            playersOnServersConnectionService.insert(playerUUID, serverId);
            input.callback(new Redirect.Success(playerUUID, serverId));
        } else {
            input.callback(new Redirect.Failure(playerUUID, serverId));
        }
    }

    @SubscribeMessage
    public void handle(Disconnect disconnect) {
        if (disconnect.getType() == Disconnect.Type.SERVER) {
            doServerDisconnect(disconnect);
        }
    }

    private void registerServer(InboundMessageContext<Handshake> input, ServerInfo serverInfo) {
        UUID serverId = container.getExactServerKey(serverInfo.getName());

        if (serverId == null) {
            ConnectedServerStub server = createServer(input, serverInfo);
            beansService.inject(server);

            serverId = container.registerServer(server);

            doSuccessRegister(serverId, server);
            callServerHandshakeEvent(input.getMessage(), server);

            log.info("Server §2{} §rwas registered now by key: §2{}", serverInfo.getName(), serverId);
            input.callback(new Handshake.Success(serverId));

        } else {
            log.debug("§4Server {} has already registered by key: {}", serverInfo.getName(), serverId);
            input.callback(new Handshake.Failure(serverId));
        }
    }

    private void doSuccessRegister(UUID serverId, ConnectedServerStub server) {
        server.setUniqueId(serverId);
        server.getChannel().setProperty(EntityServer.CHANNEL_PROPERTY, server);
    }

    private ConnectedServerStub createServer(InboundMessageContext<Handshake> input, ServerInfo serverInfo) {
        return ConnectedServerStub.builder()
                .serverInfo(serverInfo)
                .channel(input.getChannel())
                .build();
    }

    private List<ServerFlag> getServerFlags(Properties properties) {
        List<ServerFlag> resultList = new ArrayList<>();

        if (properties.contains("server.flags.fallback"))
            resultList.add(ServerFlag.FALLBACK_SERVER);
        if (properties.contains("server.flags.default"))
            resultList.add(ServerFlag.DEFAULT_SERVER);
        if (properties.contains("server.flags.lobby"))
            resultList.add(ServerFlag.LOBBY_SERVER);

        return resultList;
    }

    private ServerInfo toServerInfo(Properties properties) {
        InetSocketAddress address = new InetSocketAddress(
                properties.getProperty(HandshakePropertiesConst.SERVER_ADDRESS_HOST),
                Integer.parseInt(properties.getProperty(HandshakePropertiesConst.SERVER_ADDRESS_PORT)));

        return ServerInfo.builder()
                .address(address)
                .name(properties.getProperty(HandshakePropertiesConst.SERVER_NAME))
                .flags(getServerFlags(properties))
                .build();
    }

    private void doServerDisconnect(Disconnect disconnect) {
        UUID serverId = disconnect.getUuid();
        ConnectedServerStub server = container.getConnectedServerExact(serverId);

        if (server != null) {
            log.info("Server §c\"{}\" ({}) §rhas disconnected", server.getName(), server.getUniqueId());

            callServerDisconnectEvent(server);
            container.unregisterServer(serverId);
        }
    }
}
