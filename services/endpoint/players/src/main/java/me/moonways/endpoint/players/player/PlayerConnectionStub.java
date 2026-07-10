package me.moonways.endpoint.players.player;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.PlayerPostRedirectEvent;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.component.PlayerConnection;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServersServiceModel;
import me.moonways.bridgenet.mtp.message.ExportedMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public final class PlayerConnectionStub implements PlayerConnection {
    private final Player player;

    @Setter
    private EntityServer currentServer;
    @Setter
    private EntityServer serverOnJoined;

    @Inject
    private ServersServiceModel serversServiceModel;

    @Override
    public CompletableFuture<PlayerPostRedirectEvent> connect(@NotNull EntityServer server) {
        return server.connectThat(player).thenApply(isSuccess -> {

            if (isSuccess) {
                return PlayerPostRedirectEvent.builder()
                        .player(player)
                        .server(server)
                        .build();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<PlayerPostRedirectEvent> connect(@NotNull UUID serverID) {
        Optional<EntityServer> serverOptional = serversServiceModel.getServerExact(serverID);
        if (!serverOptional.isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        return connect(serverOptional.get());
    }

    @Override
    public Optional<EntityServer> getServer() {
        return Optional.ofNullable(currentServer);
    }

    @Override
    public Optional<EntityServer> getServerOnJoined() {
        return Optional.ofNullable(serverOnJoined);
    }

    @Override
    public void send(@NotNull Object message) {
        Optional<EntityServer> serverOptional = getServer();
        if (serverOptional.isPresent()) {
            serverOptional.get().send(message);
        }
    }

    @Override
    public void send(@NotNull ExportedMessage message) {
        Optional<EntityServer> serverOptional = getServer();
        if (serverOptional.isPresent()) {
            serverOptional.get().send(message);
        }
    }

    @Override
    public <R> CompletableFuture<R> sendAwait(@NotNull Class<R> responseType, @NotNull Object message) {
        Optional<EntityServer> serverOptional = getServer();
        if (serverOptional.isPresent()) {
            return serverOptional.get().sendAwait(responseType, message);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <R> CompletableFuture<R> sendAwait(int timeout, @NotNull Class<R> responseType, @NotNull Object message) {
        Optional<EntityServer> serverOptional = getServer();
        if (serverOptional.isPresent()) {
            return serverOptional.get().sendAwait(timeout, responseType, message);
        }
        return CompletableFuture.completedFuture(null);
    }
}
