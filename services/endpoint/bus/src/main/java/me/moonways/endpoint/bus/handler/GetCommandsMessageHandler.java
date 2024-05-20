package me.moonways.endpoint.bus.handler;

import me.moonways.bridgenet.api.command.process.CommandService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.bus.message.GetCommands;
import me.moonways.bridgenet.mtp.message.InboundMessageContext;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;

@InboundMessageListener
public class GetCommandsMessageHandler {

    @Inject
    private CommandService commandService;

    @SubscribeMessage
    public void handle(InboundMessageContext<GetCommands> context) {
        GetCommands.Result commandsGetResult = new GetCommands.Result(
                commandService.getRegisteredCommands());

        context.callback(commandsGetResult);
    }
}
