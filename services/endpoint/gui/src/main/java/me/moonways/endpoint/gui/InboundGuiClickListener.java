package me.moonways.endpoint.gui;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.model.message.FireClickAction;
import me.moonways.bridgenet.model.service.gui.GuiSlot;
import me.moonways.bridgenet.model.service.gui.click.ClickAction;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;


@Log4j2
@RequiredArgsConstructor
@InboundMessageListener
public final class InboundGuiClickListener {

    private final GuiServiceEndpoint guiService;

    @SubscribeMessage
    public void handle(FireClickAction message) {
        guiService.fireClickAction(
                ClickAction.builder()
                        .playerId(message.getPlayerId())
                        .guiId(message.getGuiId())
                        .slot(GuiSlot.at(message.getSlot()))
                        .clickType(message.getClickType())
                        .build());
    }
}
