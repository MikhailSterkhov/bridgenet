package me.moonways.bridgenet.test.client;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.data.management.ExampleClientConnection;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.MtpModule;
import me.moonways.bridgenet.test.engine.component.module.impl.RmiServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

@RunWith(ModernTestEngineRunner.class)
@TestModules({MtpModule.class, RmiServicesModule.class/* for starting a bus mtp-server */})
public class ClientHandshakeTest {

    @Inject
    private ExampleClientConnection exampleClientConnection;

    @Test
    @TestOrdered(1)
    public void test_handshakeSuccess() {
        Handshake.Result result = awaitHandshake();
        assertNotNull(result.getKey());
    }

    @Test
    @TestOrdered(2)
    public void test_handshakeFailed() {
        Handshake.Result result = awaitHandshake();
        assertTrue(result instanceof Handshake.Failure);
    }

    private Handshake.Result awaitHandshake() {
        Handshake handshake = new Handshake(Handshake.Type.SERVER, TestConst.Server.DESC.toProperties());
        BridgenetNetworkChannel channel = exampleClientConnection.getChannel();

        CompletableFuture<Handshake.Result> handshakeResultFuture =
                channel.sendAwait(Handshake.Result.class, handshake);

        return handshakeResultFuture.join();
    }
}
