package me.moonways.bridgenet.api.command.api.process.verification.listener;

import me.moonways.bridgenet.api.command.api.event.CommandPreProcessEvent;
import me.moonways.bridgenet.api.command.api.uses.CommandInfo;
import me.moonways.bridgenet.api.command.api.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.api.uses.entity.EntityCommandSender;

/**
 * Данный обработчик событий обрабатывает команды и проверяет
 * наличие прав на исполнение.
 */
public class CommandAccessKeyListener {

    /**
     * Обработка события когда команда только на стадии выполнения.
     * Проверка прав пользователя.
     *
     * @param event - событие, перед выполнением команды.
     */
    public void handle(CommandPreProcessEvent event) {
        CommandExecutionContext commandExecutionContext = event.getCommandExecutionContext();
        CommandInfo commandInfo = event.getCommandInfo();

        EntityCommandSender entityCommandSender = commandExecutionContext.getSender();

        if (!entityCommandSender.hasPermission(commandInfo.getAccessKey())) {
            entityCommandSender.sendMessage("You do not have permission to dispatch this command");
            event.makeCancelled();
        }
    }
}