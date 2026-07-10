package me.moonways.bridgenet.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.gui.click.ClickType;
import me.moonways.bridgenet.mtp.message.persistence.ClientMessage;
import me.moonways.bridgenet.mtp.transfer.ByteTransfer;
import me.moonways.bridgenet.mtp.transfer.provider.ToEnumProvider;
import me.moonways.bridgenet.mtp.transfer.provider.ToUUIDProvider;

import java.util.UUID;

/**
 * Клик по слоту удалённого GUI, отправляемый
 * клиентом (Spigot) на ядро Bridgenet.
 */
@Getter
@ToString
@ClientMessage
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @Inject)
public class FireClickAction {

    @ByteTransfer(provider = ToUUIDProvider.class)
    private UUID playerId;

    @ByteTransfer(provider = ToUUIDProvider.class)
    private UUID guiId;

    @ByteTransfer
    private int slot;

    @ByteTransfer(provider = ToEnumProvider.class)
    private ClickType clickType;
}
