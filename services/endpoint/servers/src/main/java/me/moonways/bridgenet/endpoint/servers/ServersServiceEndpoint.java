package me.moonways.bridgenet.endpoint.servers;

import me.moonways.bridgenet.endpoint.servers.command.ServersInfoCommand;
import me.moonways.bridgenet.endpoint.servers.handler.ServersDownstreamListener;
import me.moonways.bridgenet.endpoint.servers.handler.ServersInputMessagesListener;
import me.moonways.bridgenet.endpoint.servers.players.PlayerDisconnectListener;
import me.moonways.bridgenet.endpoint.servers.players.PlayersOnServersConnectionService;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServerFlag;
import me.moonways.bridgenet.model.service.servers.ServersServiceModel;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ServersServiceEndpoint extends EndpointServiceObject implements ServersServiceModel {

    private final ServersContainer serversContainer = new ServersContainer();

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.bind(new PlayersOnServersConnectionService());
        context.registerCommand(new ServersInfoCommand());
        context.registerEventListener(new ServersDownstreamListener(serversContainer));
        context.registerEventListener(new PlayerDisconnectListener());
        context.registerMessageListener(new ServersInputMessagesListener(serversContainer));
    }

    @Override
    public List<EntityServer> getTotalServers() {
        return serversContainer.getConnectedServers().collect(Collectors.toList());
    }

    @Override
    public List<EntityServer> getDefaultServers() {
        return serversContainer.getConnectedServersWithFlag(ServerFlag.DEFAULT_SERVER)
                .collect(Collectors.toList());
    }

    @Override
    public List<EntityServer> getFallbackServers() {
        return serversContainer.getConnectedServersWithFlag(ServerFlag.FALLBACK_SERVER)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<EntityServer> getServerExact(@NotNull String serverName) {
        return Optional.ofNullable(serversContainer.getConnectedServerExact(serverName));
    }

    @Override
    public Optional<EntityServer> getServerExact(@NotNull UUID uuid) {
        return Optional.ofNullable(serversContainer.getConnectedServerExact(uuid));
    }

    @Override
    public Optional<EntityServer> getServer(@NotNull String serverName) {
        return Optional.ofNullable(serversContainer.getConnectedServer(serverName));
    }

    @Override
    public boolean hasServer(@NotNull UUID uuid) {
        return getServerExact(uuid).isPresent();
    }

    @Override
    public boolean hasServer(@NotNull String serverName) {
        return getServerExact(serverName).isPresent();
    }
}
