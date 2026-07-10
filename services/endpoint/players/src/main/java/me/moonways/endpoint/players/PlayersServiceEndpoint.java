package me.moonways.endpoint.players;

import lombok.Getter;
import lombok.experimental.Accessors;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import me.moonways.endpoint.players.database.PlayersRepository;
import me.moonways.endpoint.players.listener.InboundPlayerCommandListener;
import me.moonways.endpoint.players.listener.InboundPlayerConnectionListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Accessors(fluent = true)
public final class PlayersServiceEndpoint extends EndpointServiceObject implements PlayersServiceModel {

    private final PlayerLevelingStub leveling = new PlayerLevelingStub();
    private final PlayerStoreStub store = new PlayerStoreStub();

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.registerMessageListener(new InboundPlayerConnectionListener(store));
        context.registerMessageListener(new InboundPlayerCommandListener(store));

        context.bind(new PlayersRepository());

        context.inject(leveling);
        context.inject(store);
    }

    @Override
    public int getTotalOnline() {
        return getOnlinePlayers().size();
    }

    @Override
    public List<Player> getOnlinePlayers() {
        return Collections.unmodifiableList(new ArrayList<>(store.getOnlinePlayersMap().values()));
    }
}
