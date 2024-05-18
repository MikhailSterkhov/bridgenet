package me.moonways.bridgenet.test.data.junit.assertion;

import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.model.bus.message.CreateGame;
import me.moonways.bridgenet.model.games.ActiveGame;
import me.moonways.bridgenet.model.games.Game;
import me.moonways.bridgenet.model.games.GameState;
import me.moonways.bridgenet.model.games.GameStatus;
import me.moonways.bridgenet.model.language.Language;
import me.moonways.bridgenet.test.data.TestConst;
import org.junit.Assert;

import java.rmi.RemoteException;

@UtilityClass
public class ServicesAssert {

    public void assertGame(Game game, CreateGame.Result subj, GameStatus gameStatus) throws RemoteException {
        Assert.assertNotNull(game);
        Assert.assertNotNull(subj);
        Assert.assertNotNull(gameStatus);

        ActiveGame activeGame = game.getActiveGame(subj.getActiveId()).get();
        GameState state = activeGame.getState();

        Assert.assertEquals(game.getName(), TestConst.Game.NAME);
        Assert.assertEquals(activeGame.getMap(), TestConst.Game.MAP);

        Assert.assertEquals(state.getMaxPlayers(), 2);
        Assert.assertEquals(state.getPlayersInTeam(), 1);
        Assert.assertEquals(state.getPlayers(), 0);
        Assert.assertEquals(state.getSpectators(), 0);
        Assert.assertEquals(state.getStatus(), gameStatus);
    }

    public void assertLanguage(Language expect, Language actual) {
        Assert.assertNotNull(expect);
        Assert.assertNotNull(actual);
        Assert.assertEquals(expect.getId(), actual.getId());
        Assert.assertEquals(expect.getName(), actual.getName());
    }
}
