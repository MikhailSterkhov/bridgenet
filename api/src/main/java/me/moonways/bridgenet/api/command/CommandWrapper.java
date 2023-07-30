package me.moonways.bridgenet.api.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.children.CommandChild;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public final class CommandWrapper {

    private final String commandName;
    private final Object source;

    private final List<CommandChild> childrenList;

    @SuppressWarnings("unchecked")
    public <T extends CommandChild> Stream<T> find(@NotNull Class<? extends Annotation> annotationClass) {
        return childrenList
                .stream()
                .map(commandChild -> (T) commandChild)
                .filter(commandChild -> commandChild.getMethod().isAnnotationPresent(annotationClass));
    }
}