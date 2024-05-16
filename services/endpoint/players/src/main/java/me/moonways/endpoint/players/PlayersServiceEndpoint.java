package me.moonways.endpoint.players;

import lombok.Getter;
import me.moonways.bridgenet.model.players.PlayersServiceModel;
import me.moonways.bridgenet.model.players.connection.PlayerConnection;
import me.moonways.bridgenet.model.players.leveling.PlayerLeveling;
import me.moonways.bridgenet.model.players.offline.OfflineDao;
import me.moonways.bridgenet.model.players.offline.PlayerOfflineManager;
import me.moonways.bridgenet.model.permissions.PermissionsServiceModel;
import me.moonways.bridgenet.model.players.social.PlayerSocials;
import me.moonways.bridgenet.rsi.endpoint.persistance.EndpointRemoteObject;
import me.moonways.endpoint.players.connection.PlayerConnectionStub;
import me.moonways.endpoint.players.leveling.PlayerLevelingStub;
import me.moonways.endpoint.players.social.PlayerSocialsStub;
import org.jetbrains.annotations.NotNull;

import java.rmi.RemoteException;
import java.util.UUID;

@Getter
public final class PlayersServiceEndpoint extends EndpointRemoteObject implements PlayersServiceModel {

    private static final long serialVersionUID = 5074638195342022234L;

    private final PlayerConnection playerConnection = new PlayerConnectionStub();
    private final PlayerLeveling playerLeveling = new PlayerLevelingStub();
    private final PermissionsServiceModel permissionsServiceModel = null;
    private final PlayerSocials playerSocials = new PlayerSocialsStub();
    private final PlayerOfflineManager playerOfflineManager = null;

    public PlayersServiceEndpoint() throws RemoteException {
        super();
    }

    @Override
    public String findPlayerName(@NotNull UUID playerUUID) throws RemoteException {
        OfflineDao offlineDao = playerOfflineManager.readData(playerUUID);
        return offlineDao.getName();
    }

    @Override
    public UUID findPlayerId(@NotNull String playerName) throws RemoteException {
        OfflineDao offlineDao = playerOfflineManager.readData(playerName);
        return offlineDao.getUuid();
    }
}
