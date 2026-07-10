package me.moonways.endpoint.games.stub;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.moonways.bridgenet.model.service.games.ActiveGame;
import me.moonways.bridgenet.model.service.games.Game;
import me.moonways.bridgenet.model.service.games.GameState;

import java.io.Serializable;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ActiveGameStub implements ActiveGame, Serializable {

    private final UUID uniqueId;
    private final Game parent;

    @Setter
    private GameState state;

    @Override
    public String getMap() {
        return state.getMap();
    }
}
