package me.moonways.bridgenet.endpoint.servers;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.endpoint.servers.players.PlayersOnServersConnectionService;
import me.moonways.bridgenet.model.message.Redirect;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.model.service.players.component.PlayerStore;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServerInfo;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.mtp.message.ExportedMessage;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Builder
@Getter
@ToString
public class ConnectedServerStub implements EntityServer {

    @Setter
    private UUID uniqueId;

    private final ServerInfo serverInfo;
    private final BridgenetNetworkChannel channel;

    @Inject
    private PlayersServiceModel playersServiceModel;

    @Inject
    private PlayersOnServersConnectionService playersOnServersConnectionService;

    @Override
    public String getName() {
        return serverInfo.getName();
    }

    @Override
    public InetSocketAddress getInetAddress() {
        return serverInfo.getAddress();
    }

    @Override
    public CompletableFuture<Boolean> connectThat(@NotNull Player player) {
        Redirect message = new Redirect(player.getId(), uniqueId);

        CompletableFuture<Redirect.Result> resultFuture
                = channel.sendAwait(Redirect.Result.class, message);

        return resultFuture.thenApply(result -> result instanceof Redirect.Success);
    }

    @Override
    public Collection<Player> getConnectedPlayers() {
        PlayerStore store = playersServiceModel.store();
        return playersOnServersConnectionService.getPlayersOnServerByKey(uniqueId)
                .stream()
                .map(uuid -> store.get(uuid).get())
                .collect(Collectors.toList());
    }

    @Override
    public void send(@NotNull Object message) {
        channel.send(message);
    }

    @Override
    public void send(@NotNull ExportedMessage message) {
        channel.send(message);
    }

    @Override
    public <R> CompletableFuture<R> sendAwait(@NotNull Class<R> responseType, @NotNull Object message) {
        return channel.sendAwait(responseType, message);
    }

    @Override
    public <R> CompletableFuture<R> sendAwait(int timeout, @NotNull Class<R> responseType, @NotNull Object message) {
        return channel.sendAwait(timeout, responseType, message);
    }

    @Override
    public int getTotalOnline() {
        return playersOnServersConnectionService.getPlayersOnServerByKey(uniqueId).size();
    }
}
