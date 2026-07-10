package me.moonways.bridgenet.test.data;

import me.moonways.bridgenet.api.command.CommandArguments;
import me.moonways.bridgenet.api.command.CommandSession;
import me.moonways.bridgenet.api.command.annotation.*;
import me.moonways.bridgenet.api.command.option.CommandParameterOnlyConsoleUse;
import me.moonways.bridgenet.api.command.sender.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.minecraft.ChatColor;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;

import java.util.UUID;

@Command("test")
@Permission("test.use")
@CommandParameter(CommandParameterOnlyConsoleUse.class)
public class ExampleCommand {

    @Inject
    private PlayersServiceModel playersServiceModel;

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        EntityCommandSender sender = session.getSender();

        sender.sendMessage("Список доступных команд:");
        session.printDefaultMessage("§e/test {0} §7- {1}");
    }

    @Alias("get")
    @Alias("player")
    @Permission("test.info")
    @ProducerExecutor("info")
    @ProducerUsageDescription("info <player-name>")
    @ProducerDescription("Get a player information by name")
    public void handleInfo(CommandSession session) {
        EntityCommandSender sender = session.getSender();
        CommandArguments arguments = session.arguments();

        if (arguments.has(1)) {
            UUID playerUuid = arguments.first(playersServiceModel.store()::idByName).orElse(null);
            String playerName = arguments.first().orElse(null);

            if (playerUuid == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '%s' не найден в базе данных системы", playerName);
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Игрок '%s' найден под идентификатором - %s", playerName, playerUuid);
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Not enough arguments - /%s", session.getDescriptor().getUsage());
        }
    }

    @MatcherExecutor(priority = 10)
    public boolean predicate_one(CommandSession session) {
        return true;
    }
}
