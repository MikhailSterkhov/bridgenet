package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.rsi.endpoint.Endpoint;
import me.moonways.bridgenet.rsi.service.RemoteServicesManagement;
import me.moonways.bridgenet.rsi.service.ServiceInfo;

import java.util.List;
import java.util.Map;

@Command
public class EndpointsStatisticsCommand {

    @Inject
    private RemoteServicesManagement registry;

    @GeneralCommand({"endpoint", "ep", "endpoints"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        final EntityCommandSender entityCommandSender = executionContext.getSender();

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
