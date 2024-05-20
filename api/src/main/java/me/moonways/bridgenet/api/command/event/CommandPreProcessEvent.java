package me.moonways.bridgenet.api.command.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.CommandInfo;
import me.moonways.bridgenet.api.event.Event;
import me.moonways.bridgenet.api.event.cancellation.EventCancellationWrapper;

@Getter
@RequiredArgsConstructor
public class CommandPreProcessEvent extends EventCancellationWrapper implements Event{

    private final CommandExecutionContext commandExecutionContext;
    private final CommandInfo commandInfo;
}
