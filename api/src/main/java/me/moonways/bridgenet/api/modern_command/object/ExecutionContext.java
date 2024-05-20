package me.moonways.bridgenet.api.modern_command.object;

import lombok.Builder;
import lombok.Getter;
import me.moonways.bridgenet.api.modern_command.object.label.Label;

import java.util.Optional;

@Getter
@Builder
public class ExecutionContext {

    private final WrappedEntityCommand entityCommand;
    private final Label label;

    public String getBaseCommandName() {
        return entityCommand.getBaseCommand().getConfiguration().getName();
    }

    public Optional<String> getSubCommandName() {
        if (!entityCommand.getSubCommand().isPresent()) {
            return Optional.empty();
        }

        return Optional.of(entityCommand.getSubCommand()
                .get()
                .getConfiguration()
                .getName());
    }

    public Optional<EntityCommand> getSubCommand() {
        return entityCommand.getSubCommand();
    }

    public EntityCommand getBaseCommand() {
        return entityCommand.getBaseCommand();
    }
}
