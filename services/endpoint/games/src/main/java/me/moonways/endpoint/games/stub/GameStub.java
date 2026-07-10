package me.moonways.endpoint.games.stub;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.model.service.games.ActiveGame;
import me.moonways.bridgenet.model.service.games.Game;
import me.moonways.bridgenet.model.service.games.GameServer;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
public class GameStub implements Game, Serializable {

    @Getter
    private final UUID uniqueId;
    @Getter
    private final String name;

    private final List<GameServerStub> loadedServers = new CopyOnWriteArrayList<>();

    public void addGameServer(GameServerStub gameServer) {
        loadedServers.add(gameServer);
    }

    public void removeGameServer(GameServerStub gameServer) {
        loadedServers.remove(gameServer);
    }

    @Override
    public Optional<ActiveGame> getActiveGame(UUID uniqueId) {
        for (ActiveGame activeGame : getActiveGames()) {
            if (activeGame.getUniqueId().equals(uniqueId)) {
                return Optional.of(activeGame);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<String> getLoadedMaps() {
        Set<String> loadedMaps = new HashSet<>();

        for (ActiveGame activeGame : getActiveGames()) {
            loadedMaps.add(activeGame.getMap());
        }

        return new ArrayList<>(loadedMaps);
    }

    @Override
    public List<GameServer> getLoadedServers() {
        return Collections.unmodifiableList(loadedServers);
    }

    @Override
    public List<ActiveGame> getActiveGames() {
        List<ActiveGame> activeGamesList = new ArrayList<>();

        for (GameServer gameServer : getLoadedServers()) {
            activeGamesList.addAll(gameServer.getActiveGames());
        }

        return activeGamesList;
    }

    @Override
    public List<ActiveGame> getActiveGamesByMap(@NotNull String map) {
        List<ActiveGame> gamesByMapList = new ArrayList<>();

        for (ActiveGame activeGame : getActiveGames()) {
            if (activeGame.getMap().equalsIgnoreCase(map)) {
                gamesByMapList.add(activeGame);
            }
        }

        return gamesByMapList;
    }
}
