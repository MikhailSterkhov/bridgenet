package me.moonways.bridgenet.api.modern_command.object;

import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

@Builder
public class WrappedEntityCommand {

    @Getter
    private final EntityCommand baseCommand;
    private EntityCommand subCommand;

    public Optional<EntityCommand> getSubCommand() {
        return Optional.ofNullable(subCommand);
    }

    public String getName() {
        return isNullSubCommand() ? baseCommand.getConfiguration().getName() : subCommand.getConfiguration().getName();
    }

    private boolean isNullSubCommand() {
        return subCommand == null;
    }
}
