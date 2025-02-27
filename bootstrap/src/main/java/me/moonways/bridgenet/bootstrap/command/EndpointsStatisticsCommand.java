package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.CommandSession;
import me.moonways.bridgenet.api.command.annotation.Alias;
import me.moonways.bridgenet.api.command.annotation.Command;
import me.moonways.bridgenet.api.command.annotation.MentorExecutor;
import me.moonways.bridgenet.api.command.sender.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.rmi.endpoint.Endpoint;
import me.moonways.bridgenet.rmi.service.RemoteServicesManagement;
import me.moonways.bridgenet.rmi.service.ServiceInfo;

import java.util.List;
import java.util.Map;

@Alias("endpoint")
@Alias("ep")
@Command("endpoints")
public class EndpointsStatisticsCommand {

    @Inject
    private RemoteServicesManagement registry;

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        final EntityCommandSender entityCommandSender = session.getSender();

        printTotalServices(entityCommandSender);
        printTotalEndpoints(entityCommandSender);
    }

    private void printTotalServices(EntityCommandSender sender) {
        final Map<String, ServiceInfo> servicesInfos = registry.getServicesInfos();

        sender.sendMessage("Total services:");
        servicesInfos.forEach((name, serviceInfo) -> {

            String formattedMessage = String.format("- %s: %s", name, serviceInfo);
            sender.sendMessage(formattedMessage);
        });
    }

    private void printTotalEndpoints(EntityCommandSender sender) {
        final List<Endpoint> endpointsList = registry.getEndpointController().getEndpoints();

        sender.sendMessage("Total endpoints:");
        endpointsList.forEach((endpoint) -> {

            String formattedMessage = String.format("- %s: %s", endpoint.getServiceInfo().getName(), endpoint.getPath());
            sender.sendMessage(formattedMessage);
        });
    }
}
