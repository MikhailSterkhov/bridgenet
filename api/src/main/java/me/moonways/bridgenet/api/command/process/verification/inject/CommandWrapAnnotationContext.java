package me.moonways.bridgenet.api.command.process.verification.inject;

import lombok.Getter;
import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.command.label.CommandLabelContext;

@Getter
public class CommandWrapAnnotationContext extends CommandExecutionContext {

    private final Command command;

    public CommandWrapAnnotationContext(EntityCommandSender entity, CommandLabelContext label, Command command) {
        super(entity, label);

        this.command = command;
    }

    public static CommandWrapAnnotationContext create(EntityCommandSender entity, CommandLabelContext labelContext, Command command) {
        return new CommandWrapAnnotationContext(entity, labelContext, command);
    }

    public CommandExecutionContext toExecutionContext() {
        return this;
    }
}
