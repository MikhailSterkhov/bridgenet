package me.moonways.endpoint.players;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.model.event.PlayerDisconnectEvent;
import me.moonways.bridgenet.model.event.PlayerHandshakeEvent;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.service.bus.HandshakePropertiesConst;
import me.moonways.bridgenet.model.service.players.OfflinePlayer;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.component.PlayerStore;
import me.moonways.bridgenet.model.service.players.component.statistic.ActivityStatistics;
import me.moonways.bridgenet.model.service.players.component.statistic.Statistic;
import me.moonways.bridgenet.model.util.PlayerIdMap;
import me.moonways.endpoint.players.database.EntityNamespace;
import me.moonways.endpoint.players.database.EntityPlayer;
import me.moonways.endpoint.players.database.PlayerDescription;
import me.moonways.endpoint.players.database.PlayersRepository;
import me.moonways.endpoint.players.player.OfflinePlayerStub;
import me.moonways.endpoint.players.player.PlayerStub;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@RequiredArgsConstructor
public final class PlayerStoreStub implements PlayerStore {

    private final Cache<UUID, PlayerDescription> nameByIdsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.HOURS)
            .build();
    private final Cache<String, PlayerDescription> idByNamesCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.HOURS)
            .build();

    private final Cache<UUID, OfflinePlayer> offlinePlayersCache = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Getter
    private final PlayerIdMap<Player> onlinePlayersMap = new PlayerIdMap<>();

    @Inject
    private PlayersRepository playersRepository;
    @Inject
    private BeansService beansService;
    @Inject
    private EventService eventService;

    public Player addOnlinePlayer(Handshake handshake) {
        PlayerStub player = createPlayer(handshake.getProperties());

        if (player == null) {
            return null;
        }

        ActivityStatistics statistics = player.getStatistics();
        statistics.setLong(Statistic.JOINED_TIME_AT, System.currentTimeMillis());

        beansService.inject(player);
        beansService.inject(player.getConnection());

        idByNamesCache.put(player.getName().toLowerCase(), player.getDescription());
        nameByIdsCache.put(player.getId(), player.getDescription());
        onlinePlayersMap.put(player.getId(), player);

        eventService.fireEvent(
                PlayerHandshakeEvent.builder()
                        .handshake(handshake)
                        .player(player)
                        .build());

        return player;
    }

    public Player removeOnlinePlayer(UUID id) {
        Player removed = onlinePlayersMap.remove(id);

        if (removed == null) {
            return null;
        }

        nameByIdsCache.cleanUp();
        idByNamesCache.cleanUp();
        offlinePlayersCache.cleanUp();

        eventService.fireEvent(
                PlayerDisconnectEvent.builder()
                        .player(removed)
                        .build());

        return removed;
    }

    private PlayerStub createPlayer(Properties properties) {
        UUID uuid = UUID.fromString(properties.getProperty(HandshakePropertiesConst.USER_UUID));
        String username = properties.getProperty(HandshakePropertiesConst.USER_NAME);

        UUID proxyId = UUID.fromString(properties.getProperty(HandshakePropertiesConst.USER_PROXY_ID));

        if (get(uuid).isPresent()) {
            return null;
        }

        PlayerDescription description;
        if (idByName(username) == null) {
            EntityNamespace entityNamespace = EntityNamespace.builder()
                    .uuid(uuid)
                    .name(username.toLowerCase())
                    .build();
            playersRepository.insert(
                    EntityPlayer.builder()
                            .namespace(entityNamespace)
                            .description(description = PlayerDescription.builder()
                                    .lastLoggedProxyId(proxyId)
                                    .lastLoggedTime(System.currentTimeMillis())
                                    .totalExperience(0)
                                    .namespace(entityNamespace)
                                    .build())
                            .build());
        } else {
            description = idByNamesCache.getIfPresent(username.toLowerCase());
        }
        return new PlayerStub(uuid, username, description, this);
    }

    private OfflinePlayer createOfflinePlayer(UUID id) {
        String name = nameById(id);
        if (name == null) {
            return null;
        }

        PlayerDescription description = nameByIdsCache.getIfPresent(id);
        if (description == null) {
            return null;
        }

        EntityNamespace namespace = description.getNamespace();
        OfflinePlayer offlinePlayer = new OfflinePlayerStub(namespace.getUuid(), namespace.getName(), description);

        beansService.inject(offlinePlayer);

        return offlinePlayer;
    }

    @Override
    public OfflinePlayer getOffline(UUID id) {
        if (id == null) {
            return null;
        }

        Optional<OfflinePlayer> offlinePlayerOptional = get(id).map(player -> player);
        if (offlinePlayerOptional.isPresent()) {
            return offlinePlayerOptional.get();
        }

        offlinePlayersCache.cleanUp();

        OfflinePlayer offlinePlayer = offlinePlayersCache.getIfPresent(id);
        if (offlinePlayer == null) {
            offlinePlayer = createOfflinePlayer(id);

            if (offlinePlayer != null) {
                offlinePlayersCache.put(id, offlinePlayer);
            }
        }

        return offlinePlayer;
    }

    @Override
    public OfflinePlayer getOffline(String name) {
        if (name == null) {
            return null;
        }
        return getOffline(idByName(name));
    }

    @Override
    public Optional<Player> get(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return onlinePlayersMap.get(id);
    }

    @Override
    public Optional<Player> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return get(idByName(name));
    }

    @Override
    public String nameById(UUID id) {
        nameByIdsCache.cleanUp();

        AtomicReference<PlayerDescription> cachedDescription =
                new AtomicReference<>(nameByIdsCache.getIfPresent(id));

        if (cachedDescription.get() == null) {
            playersRepository.get(id)
                    .map(EntityPlayer::getDescription)
                    .blockAndSubscribe(playerDescription -> {

                        cachedDescription.set(playerDescription);
                        nameByIdsCache.put(id, playerDescription);
                    });
        }

        return Optional.ofNullable(cachedDescription.get())
                .map(PlayerDescription::getNamespace)
                .map(EntityNamespace::getName)
                .orElse(null);
    }

    @Override
    public UUID idByName(String name) {
        idByNamesCache.cleanUp();

        AtomicReference<PlayerDescription> cachedDescription =
                new AtomicReference<>(idByNamesCache.getIfPresent(name.toLowerCase()));

        if (cachedDescription.get() == null) {
            playersRepository.get(name)
                    .map(EntityPlayer::getDescription)
                    .blockAndSubscribe(playerDescription -> {

                        cachedDescription.set(playerDescription);
                        idByNamesCache.put(name.toLowerCase(), playerDescription);
                    });
        }

        return Optional.ofNullable(cachedDescription.get())
                .map(PlayerDescription::getNamespace)
                .map(EntityNamespace::getUuid)
                .orElse(null);
    }
}
