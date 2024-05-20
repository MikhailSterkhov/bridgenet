package me.moonways.bridgenet.bootstrap.hook.type.console;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.command.uses.entity.ConsoleCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.process.CommandService;
import me.moonways.bridgenet.bootstrap.AppBootstrap;
import net.minecrell.terminalconsole.SimpleTerminalConsole;

import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
public class BridgenetConsole extends SimpleTerminalConsole {

    @Inject
    private ConsoleCommandSender consoleSender;
    @Inject
    private CommandService commandService;
    @Inject
    private AppBootstrap bootstrap;

    @Override
    protected boolean isRunning() {
        return bootstrap.isRunning();
    }

    @Override
    protected void shutdown() {
        bootstrap.shutdown();
    }

    @Override
    protected void runCommand(String commandLine) {
        if (commandLine.equalsIgnoreCase("exit")) {
            bootstrap.shutdown();
            return;
        }

        Optional<CommandExecutionContext> execution = commandService.dispatchConsole(commandLine);

        if (!execution.isPresent()) {
            log.warn("§6That command is not found: §e\"{}\"", commandLine);
        } else {
            log.debug("Command label §2\"{}\" §rwas success processed", commandLine);
        }
    }
}
