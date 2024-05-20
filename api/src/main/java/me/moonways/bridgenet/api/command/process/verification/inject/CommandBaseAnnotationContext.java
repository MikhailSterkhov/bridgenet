package me.moonways.bridgenet.api.command.process.verification.inject;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.CommandInfo;

import java.lang.annotation.Annotation;

@Getter
@RequiredArgsConstructor
public class CommandBaseAnnotationContext<T extends Annotation> {

    private final T annotation;
    private final CommandInfo info;

    public static <T extends Annotation> CommandBaseAnnotationContext<T> create(T annotation, CommandInfo commandInfo) {
        return new CommandBaseAnnotationContext<>(annotation, commandInfo);
    }
}
