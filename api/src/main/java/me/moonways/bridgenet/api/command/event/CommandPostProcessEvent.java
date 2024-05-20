package me.moonways.bridgenet.api.command.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.event.Event;
import me.moonways.bridgenet.api.command.process.execution.CommandExecuteResult;

@Getter
@RequiredArgsConstructor
public class CommandPostProcessEvent implements Event {

    private final CommandExecutionContext commandExecutionContext;
    private final CommandExecuteResult result;
}
