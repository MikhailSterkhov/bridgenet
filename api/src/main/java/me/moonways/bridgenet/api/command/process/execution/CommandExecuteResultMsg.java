package me.moonways.bridgenet.api.command.process.execution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum CommandExecuteResultMsg {

    DO_NOT_HAVE_PERMISSION("You do not have permission"),
    ONLY_CONSOLE("Only console sender"),
    ONLY_USER("Only user sender"),
    NOT_FOUND("Command not found"),
    COOLDOWN_IS_NOT_EXPIRED("Cooldown is not expired yet, remained %s"),
    BAD_PATTERN("Bad pattern, example %s"),
    SUCCESSFUL_DISPATCH("Successful dispatch command");

    private final String value;

    }

