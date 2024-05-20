package me.moonways.bridgenet.test.data;

import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.CommandPermission;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.SubCommand;
import me.moonways.bridgenet.api.command.label.CommandLabelContext;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.util.minecraft.ChatColor;
import me.moonways.bridgenet.model.players.PlayersServiceModel;

import java.util.UUID;

@Command
public class ExampleCommand {

    @Inject
    private PlayersServiceModel playersServiceModel;

    @GeneralCommand({"example", "test"})
    public void defaultCommand(CommandExecutionContext session) {
        EntityCommandSender sender = session.getSender();

        sender.sendMessage("Список доступных команд:");
        session.printDefaultMessage("§e/test {0} §7- {1}");
    }

    @Alias("get")
    @Alias("player")
    @CommandPermission("test.info")
    @SubCommand("info")
    @ProducerUsageDescription("info <player-name>")
    @ProducerDescription("Get a player information by name")
    public void handleInfo(CommandExecutionContext session) {
        EntityCommandSender sender = session.getSender();
        CommandLabelContext.Arguments arguments = session.getArguments();

        if (arguments.has(1)) {
            UUID playerUuid = arguments.first(playersServiceModel::findPlayerId).orElse(null);
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
}
