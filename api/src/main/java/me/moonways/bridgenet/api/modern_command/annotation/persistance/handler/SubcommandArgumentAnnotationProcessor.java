package me.moonways.bridgenet.api.modern_command.annotation.persistance.handler;

import me.moonways.bridgenet.api.modern_command.annotation.AutoregisterCommandAnnotation;
import me.moonways.bridgenet.api.modern_command.annotation.CommandAnnotationContext;
import me.moonways.bridgenet.api.modern_command.annotation.CommandAnnotationProcessor;
import me.moonways.bridgenet.api.modern_command.annotation.persistance.SubcommandArgument;

@AutoregisterCommandAnnotation
public class SubcommandArgumentAnnotationProcessor extends CommandAnnotationProcessor<SubcommandArgument> {

    public SubcommandArgumentAnnotationProcessor() {
        super(SubcommandArgument.class);
    }

    @Override
    protected void updateCommandInfo(CommandAnnotationContext<SubcommandArgument> context) {
    }

    @Override
    public boolean verify(CommandAnnotationContext<SubcommandArgument> context) {
        return true;
    }
}
