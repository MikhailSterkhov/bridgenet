package me.moonways.endpoint.players.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.model.message.Disconnect;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;
import me.moonways.endpoint.players.PlayerStoreStub;

import java.rmi.RemoteException;

@Log4j2
@InboundMessageListener
@RequiredArgsConstructor
public final class PlayerMessageListener {

    private final PlayerStoreStub playerStoreStub;

    @SubscribeMessage
    public void handle(Handshake handshake) throws RemoteException {
        if (handshake.getType() == Handshake.Type.PLAYER) {

            Player player = playerStoreStub.addOnlinePlayer(handshake.getProperties());

            log.info("§6Player({}, '{}') connected to Bridgenet", player.getName(), player.getId());
        }
    }

    @SubscribeMessage
    public void handle(Disconnect disconnect) throws RemoteException {
        if (disconnect.getType() == Disconnect.Type.PLAYER) {

            Player player = playerStoreStub.removeOnlinePlayer(disconnect.getUuid());

            log.info("§4Player({}, '{}') disconnected", player.getName(), player.getId());
        }
    }
}
