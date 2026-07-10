package me.moonways.endpoint.games.handler;

import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.event.SubscribeEvent;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.ServerDisconnectEvent;
import me.moonways.bridgenet.model.message.DeleteGame;
import me.moonways.bridgenet.model.service.games.ActiveGame;
import me.moonways.bridgenet.model.service.games.Game;
import me.moonways.bridgenet.model.service.games.GameServer;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServerInfo;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.endpoint.games.GamesContainer;


@RequiredArgsConstructor
public class GamesServersDownstreamListener {

    private final GamesContainer container;

    @Inject
    private BridgenetNetworkChannel channel;

    @SubscribeEvent
    public void handle(ServerDisconnectEvent event) {
        EntityServer server = event.getServer();

        for (Game game : container.getCollectedGames()) {
            for (GameServer gameServer : game.getLoadedServers()) {
                ServerInfo serverInfo = gameServer.getServerInfo();

                if (serverInfo.equals(server.getServerInfo())) {

                    for (ActiveGame activeGame : gameServer.getActiveGames()) {
                        channel.pull(new DeleteGame(game.getUniqueId(), activeGame.getUniqueId()));
                    }
                }
            }
        }
    }
}
