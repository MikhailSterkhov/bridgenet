package me.moonways.bridgenet.endpoint.servers.command;

import me.moonways.bridgenet.api.command.CommandSession;
import me.moonways.bridgenet.api.command.annotation.*;
import me.moonways.bridgenet.api.command.option.CommandParameterOnlyConsoleUse;
import me.moonways.bridgenet.api.command.sender.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.minecraft.ChatColor;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.model.service.servers.ServersServiceModel;

import java.util.List;
import java.util.Optional;

@Alias("servers")
@Command("server")
@CommandParameter(CommandParameterOnlyConsoleUse.class)
public class ServersInfoCommand {

    @Inject
    private ServersServiceModel servers;

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        EntityCommandSender sender = session.getSender();

        sender.sendMessage("Список доступных команд:");
        session.printDefaultMessage("§e/server {0} §7- {1}");
    }

    @Alias("get")
    @ProducerExecutor("info")
    @ProducerUsageDescription("info <server-name>")
    @ProducerDescription("Get a server information by name")
    public void info(CommandSession session) {
        Optional<String> serverName = session.arguments().get(0);

        if (!serverName.isPresent()) {
            session.getSender().sendMessage(ChatColor.RED + "Требуется дополнительно указать наименование нужного сервера");
            return;
        }

        Optional<EntityServer> serverOptional = servers.getServer(serverName.get());
        if (!serverOptional.isPresent()) {
            session.getSender().sendMessage(ChatColor.RED + "Указанный сервер под именем '%s' не зарегистрирован", serverName.get());
            return;
        }

        EntityServer entityServer = serverOptional.get();

        sendServerInfo(entityServer, session.getSender());
    }

    @ProducerExecutor("list")
    @ProducerDescription("Get a total servers list")
    public void list(CommandSession session) {
        List<EntityServer> totalServers = servers.getTotalServers();
        if (totalServers.isEmpty()) {
            session.getSender().sendMessage("Servers not found");
            return;
        }
        for (EntityServer server : totalServers) {
            sendServerInfo(server, session.getSender());
        }
    }

    private void sendServerInfo(EntityServer entityServer, EntityCommandSender sender) {
        sender.sendMessage("{info=" + entityServer.getServerInfo() + ", online=" + entityServer.getTotalOnline() + "}");
    }
}
