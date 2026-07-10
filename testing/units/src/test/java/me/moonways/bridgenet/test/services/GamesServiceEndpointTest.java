package me.moonways.bridgenet.test.services;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.message.CreateGame;
import me.moonways.bridgenet.model.message.DeleteGame;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.message.ProtocolVersion;
import me.moonways.bridgenet.model.message.UpdateGame;
import me.moonways.bridgenet.model.service.games.Game;
import me.moonways.bridgenet.model.service.games.GameStatus;
import me.moonways.bridgenet.model.service.games.GamesServiceModel;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.data.junit.assertion.ServicesAssert;
import me.moonways.bridgenet.test.data.management.ExampleClientConnection;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertNull;

@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class GamesServiceEndpointTest {

    @Inject
    private ExampleClientConnection exampleClientConnection;
    @Inject
    private GamesServiceModel gamesServiceModel;

    private CreateGame.Result subj;

    @Test
    @TestOrdered(1)
    public void test_createGameSuccess() {
        BridgenetNetworkChannel channel = exampleClientConnection.getChannel();
        sendHandshakeMessage();

        CompletableFuture<CreateGame.Result> future = channel.sendAwait(CreateGame.Result.class,
                new CreateGame(
                        TestConst.Game.NAME,
                        TestConst.Game.MAP, 2, 1));

        subj = future.join();

        Game game = gamesServiceModel.getGame(subj.getGameId());
        ServicesAssert.assertGame(game, subj, GameStatus.IDLE);
    }

    @Test
    @TestOrdered(2)
    public void test_updateGameState() throws InterruptedException {
        BridgenetNetworkChannel channel = exampleClientConnection.getChannel();
        channel.send(
                new UpdateGame(subj.getGameId(), subj.getActiveId(),
                        GameStatus.PROCESSING, 0, 0));

        sleep(100);

        Game game = gamesServiceModel.getGame(subj.getGameId());
        ServicesAssert.assertGame(game, subj, GameStatus.PROCESSING);
    }

    @Test
    @TestOrdered(3)
    public void test_successGameDelete() throws InterruptedException {
        BridgenetNetworkChannel channel = exampleClientConnection.getChannel();
        channel.send(
                new DeleteGame(subj.getGameId(), subj.getActiveId()));

        sleep(100);

        assertNull(gamesServiceModel.getGame(subj.getGameId()));
    }

    private Handshake.Result sendHandshakeMessage() {
        BridgenetNetworkChannel channel = exampleClientConnection.getChannel();

        Handshake message = new Handshake(Handshake.Type.SERVER, TestConst.Game.SERVER_DTO.toProperties(), ProtocolVersion.CURRENT);
        CompletableFuture<Handshake.Result> future = channel.sendAwait(Handshake.Result.class, message);
        try {
            return future.join();
        } catch (CompletionException exception) {
            return new Handshake.Failure(UUID.randomUUID());
        }
    }
}
