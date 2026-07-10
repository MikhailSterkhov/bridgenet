package me.moonways.bridgenet.test.client;

import me.moonways.bridgenet.api.command.CommandExecutor;
import me.moonways.bridgenet.api.command.exception.CommandExecutionException;
import me.moonways.bridgenet.api.command.sender.ConsoleCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.client.api.data.UserDto;
import me.moonways.bridgenet.model.message.Handshake;
import me.moonways.bridgenet.model.message.ProtocolVersion;
import me.moonways.bridgenet.model.message.SendCommand;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.test.data.ExampleClient;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ClientsModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RunWith(ModernTestEngineRunner.class)
@TestModules(ClientsModule.class)
public class ClientStressMessagesTest {

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    private static final int PACKETS_LENGTH = 1000;
    private static final int SLEEPING_TIMEOUT = PACKETS_LENGTH * 7;

    @Inject
    private ExampleClient subj;
    @Inject
    private CommandExecutor commandExecutor;
    @Inject
    private ConsoleCommandSender consoleCommandSender;

    @Test
    @TestOrdered(0)
    public void test_stressingPacketsRouting() throws InterruptedException {
        BridgenetNetworkChannel channel = subj.getChannel();

        for (int count = 0; count < PACKETS_LENGTH; count++) {
            pullGeneratedPlayer(channel);
        }

        Thread.sleep(SLEEPING_TIMEOUT);
    }

    @Test
    @TestOrdered(1)
    public void test_afterStressChecks() throws CommandExecutionException {
        commandExecutor.execute(consoleCommandSender, "memory");
        commandExecutor.execute(consoleCommandSender, "bridgenet");
    }

    private void pullGeneratedPlayer(BridgenetNetworkChannel channel) {
        UUID playerID = UUID.randomUUID();

        channel.sendAwait(Handshake.Success.class,
                new Handshake(Handshake.Type.PLAYER,
                        UserDto.builder()
                                .uniqueId(playerID)
                                .proxyId(UUID.randomUUID())
                                .name(randomString(1000))
                                .build().toProperties(),
                        ProtocolVersion.CURRENT)

        ).whenComplete((success, throwable) ->
                channel.send(new SendCommand(playerID, "/testCommand")));
    }


    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijtklmnopqrstuvwxyz1234567890";

    private static String randomString(int length) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(0, ALPHABET.length())));
        }

        return builder.toString();
    }
}
