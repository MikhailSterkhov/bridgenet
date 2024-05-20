package me.moonways.bridgenet.endpoint.servers.command;

import me.moonways.bridgenet.api.command.*;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.util.minecraft.ChatColor;
import me.moonways.bridgenet.model.servers.EntityServer;
import me.moonways.bridgenet.model.servers.ServersServiceModel;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Optional;

@Command
public class ServersInfoCommand {

    @Inject
    private ServersServiceModel servers;

    @GeneralCommand({"server", "servers"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        EntityCommandSender sender = executionContext.getSender();

        sender.sendMessage("Список доступных команд:");
        //session.printDefaultMessage("§e/server {0} §7- {1}"); //fixme
    }

    @SubCommand({"get", "info"})
    @CommandStructure(usage = "info <server-name>", description = "Get a server information by name", value = @CommandArgument(position = 1))
    public void info(CommandExecutionContext executionContext) throws RemoteException {
        Optional<String> serverName = executionContext.getArguments().get(0);
        EntityCommandSender sender = executionContext.getSender();

        Optional<EntityServer> serverOptional = servers.getServer(serverName.get());
        if (!serverOptional.isPresent()) {
            sender.sendMessage(ChatColor.RED + "Указанный сервер под именем '%s' не зарегистрирован", serverName.get());
            return;
        }

        EntityServer entityServer = serverOptional.get();
        sendServerInfo(entityServer, sender);
    }

    @SubCommand({"list", "lists"})
    @CommandStructure(usage = "list", description = "Get a total servers list")
    public void list(CommandExecutionContext executionContext) throws RemoteException {
        List<EntityServer> totalServers = servers.getTotalServers();
        EntityCommandSender sender = executionContext.getSender();

        if (totalServers.isEmpty()) {
            sender.sendMessage("Servers not found");
            return;
        }
        for (EntityServer server : totalServers) {
            sendServerInfo(server, sender);
        }
    }

    private void sendServerInfo(EntityServer entityServer, EntityCommandSender sender) throws RemoteException {
        sender.sendMessage("{info=" + entityServer.getServerInfo() + ", online=" + entityServer.getTotalOnline() + "}");
    }
}
