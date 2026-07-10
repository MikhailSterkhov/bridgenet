package me.moonways.endpoint.players.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.message.Disconnect;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.message.ProtocolVersion;
import me.moonways.bridgenet.model.message.Redirect;
import me.moonways.bridgenet.model.service.bus.HandshakePropertiesConst;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServersServiceModel;
import me.moonways.bridgenet.mtp.message.InboundMessageContext;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;
import me.moonways.endpoint.players.PlayerStoreStub;
import me.moonways.endpoint.players.player.PlayerConnectionStub;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Log4j2
@InboundMessageListener
@RequiredArgsConstructor
public final class InboundPlayerConnectionListener {

    private final PlayerStoreStub playerStoreStub;

    @Inject
    private ServersServiceModel serversServiceModel;

    @SubscribeMessage
    public void handle(InboundMessageContext<Handshake> context) {
        Handshake handshake = context.getMessage();

        if (handshake.getType() == Handshake.Type.PLAYER) {

            if (handshake.getProtocolVersion() != ProtocolVersion.CURRENT) {
                log.error("§4Handshake rejected: protocol version mismatch (server={}, client={})",
                        ProtocolVersion.CURRENT, handshake.getProtocolVersion());
                context.callback(new Handshake.Failure());
                return;
            }

            Properties properties = handshake.getProperties();
            String userUuidProperty = properties.getProperty(HandshakePropertiesConst.USER_UUID);

            if (userUuidProperty == null) {
                context.callback(new Handshake.Failure());
                return;
            }

            Player player = playerStoreStub.addOnlinePlayer(handshake);

            if (player != null) {
                context.callback(new Handshake.Success(UUID.fromString(userUuidProperty)));

                log.info("§6Player({}, '{}') connected to Bridgenet", player.getName(), player.getId());
            } else {
                context.callback(new Handshake.Failure(UUID.fromString(userUuidProperty)));
            }
        }
    }

    @SubscribeMessage
    public void handle(Redirect redirect) {
        Optional<Player> playerOptional = playerStoreStub.get(redirect.getPlayerUUID());
        Optional<EntityServer> serverOptional = serversServiceModel.getServerExact(redirect.getServerKey());

        if (playerOptional.isPresent() && serverOptional.isPresent()) {
            EntityServer entityServer = serverOptional.get();
            PlayerConnectionStub connection = (PlayerConnectionStub) playerOptional.get().getConnection();

            if (!connection.getServerOnJoined().isPresent()) {
                connection.setServerOnJoined(entityServer);
            }

            connection.setCurrentServer(entityServer);
        }
    }

    @SubscribeMessage
    public void handle(Disconnect disconnect) {
        if (disconnect.getType() == Disconnect.Type.PLAYER) {
            Player player = playerStoreStub.removeOnlinePlayer(disconnect.getUuid());

            if (player != null) {
                log.info("§4Player({}, '{}') disconnected", player.getName(), player.getId());
            }
        }
    }
}
