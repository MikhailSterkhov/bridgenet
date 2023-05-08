package me.moonways.bridgenet.command.exception;

import org.jetbrains.annotations.NotNull;

public class CommandNotAnnotatedException extends RuntimeException {

    public CommandNotAnnotatedException(@NotNull String message) {
        super(message);
    }
}
