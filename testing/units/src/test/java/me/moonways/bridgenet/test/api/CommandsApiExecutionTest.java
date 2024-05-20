package me.moonways.bridgenet.test.api;

import me.moonways.bridgenet.api.command.process.CommandService;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.ConsoleCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.module.impl.CommandsModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(ModernTestEngineRunner.class)
@TestModules(CommandsModule.class)
public class CommandsApiExecutionTest {

    @Inject
    private CommandService commandService;
    @Inject
    private ConsoleCommandSender consoleCommandSender;

    @Test
    public void test_executeMentor() {
        Optional<CommandExecutionContext> executionContextOptional =
                commandService.dispatch(consoleCommandSender, TestConst.Commands.LABEL);

        CommandExecutionContext commandExecutionContext = executionContextOptional.get();

        assertEquals(commandExecutionContext.getLabel().getLabel(), TestConst.Commands.LABEL);
        // todo ...
    }
}
