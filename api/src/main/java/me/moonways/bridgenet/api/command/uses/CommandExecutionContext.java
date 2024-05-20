package me.moonways.bridgenet.api.command.uses;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.command.label.CommandLabelContext;

/**
 * Контекст выполнения команды.
 */
@Getter
@RequiredArgsConstructor
public class CommandExecutionContext {

    /**
     * Создать контекст.
     *
     * @param sender - отправитель.
     * @param label - строка.
     */
    public static CommandExecutionContext create(EntityCommandSender sender, CommandLabelContext label) {
        return new CommandExecutionContext(sender, label);
    }

    private final EntityCommandSender sender;
    private final CommandLabelContext label;

    /**
     * Получить аргументы отправителя.
     */
    public CommandLabelContext.Arguments getArguments() {
        return label.getArguments();
    }
}
