package me.moonways.endpoint.settings;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.model.service.settings.PlayerSettingsServiceModel;
import me.moonways.bridgenet.model.service.settings.Setting;
import me.moonways.bridgenet.model.service.settings.SettingID;
import me.moonways.bridgenet.model.util.PlayerIdMap;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;

import java.util.*;

public class PlayerSettingsServiceEndpoint extends EndpointServiceObject implements PlayerSettingsServiceModel {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private PlayerIdMap<PlayerSettings> playerSettingsCacheMap;

    @Inject
    private PlayersServiceModel playersServiceModel;
    @Inject
    private BeansService beansService;

    @Override
    protected void construct(EndpointRemoteContext context) {
        this.playerSettingsCacheMap = new PlayerIdMap<>();
    }

    private PlayerSettings createPlayerSettings(UUID playerId) {
        PlayerSettings playerSettings = new PlayerSettings(new HashMap<>(), playerId);

        beansService.inject(playerSettings);

        playerSettings.loadAll();
        return playerSettings;
    }

    @Override
    public Collection<SettingID<?>> getTotalSettings() {
        return Collections.unmodifiableCollection(Arrays.asList(SettingID.TYPES));
    }

    @Override
    public <T> Setting<T> getSetting(UUID playerId, SettingID<T> id) {
        PlayerSettings playerSettings = playerSettingsCacheMap.getOrPut(playerId, () -> createPlayerSettings(playerId));
        return playerSettings.get(id);
    }

    @Override
    public <T> Setting<T> getSetting(String playerName, SettingID<T> id) {
        return getSetting(playersServiceModel.store().idByName(playerName), id);
    }
}
