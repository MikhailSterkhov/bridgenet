package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.bootstrap.AppBootstrap;

@Command
public class ShutdownCommand {

    @Inject
    private AppBootstrap bootstrap;

    @GeneralCommand({"stop", "shutdown", "exit", "close", "fail", "sd"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        bootstrap.shutdown();
    }
}
