package me.moonways.bridgenet.api.command.process.verification.inject.validate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.CommandInfo;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandBaseAnnotationContext;

import java.lang.annotation.Annotation;

@Getter
@RequiredArgsConstructor
public class CommandAnnotationValidateRequest<T extends Annotation> {

    private final CommandBaseAnnotationContext<T> annotationContext;
    private final CommandExecutionContext executionContext;

    public static <T extends Annotation> CommandAnnotationValidateRequest<T> create(T annotation, CommandInfo commandInfo, CommandExecutionContext commandExecutionContext) {
        CommandBaseAnnotationContext<T> commandContext = new CommandBaseAnnotationContext<>(annotation, commandInfo);

        return new CommandAnnotationValidateRequest<>(commandContext, commandExecutionContext);
    }
}
