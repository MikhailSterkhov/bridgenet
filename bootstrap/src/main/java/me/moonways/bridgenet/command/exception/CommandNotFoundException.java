package me.moonways.bridgenet.command.exception;

import org.jetbrains.annotations.NotNull;

public class CommandNotFoundException extends RuntimeException {

    public CommandNotFoundException(@NotNull String message) {
        super(message);
    }
}
