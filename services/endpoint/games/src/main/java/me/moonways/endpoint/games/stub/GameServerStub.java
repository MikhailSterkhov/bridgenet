package me.moonways.endpoint.games.stub;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.model.service.games.ActiveGame;
import me.moonways.bridgenet.model.service.games.GameServer;
import me.moonways.bridgenet.model.service.servers.ServerInfo;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
public class GameServerStub implements GameServer, Serializable {

    @Getter
    private final ServerInfo serverInfo;

    private final List<ActiveGame> activeGames = new CopyOnWriteArrayList<>();

    public void addActiveGame(@NotNull ActiveGame activeGame) {
        activeGames.add(activeGame);
    }

    public void removeActiveGame(@NotNull ActiveGame activeGame) {
        activeGames.remove(activeGame);
    }

    @Override
    public Optional<ActiveGame> getBetterGameForJoin() {
        return Optional.empty(); // TODO
    }

    @Override
    public List<ActiveGame> getActiveGames() {
        return Collections.unmodifiableList(activeGames);
    }
}
