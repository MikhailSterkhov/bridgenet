package me.moonways.bridgenet.mtp.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.mtp.message.persistence.ClientMessage;
import me.moonways.bridgenet.mtp.message.persistence.ServerMessage;
import me.moonways.bridgenet.mtp.transfer.ByteTransfer;
import me.moonways.bridgenet.mtp.transfer.provider.TransferJsonProvider;

@Getter
@ToString
@ServerMessage
@ClientMessage
@AllArgsConstructor
@NoArgsConstructor
public class ResponsibleMessage {

    @ByteTransfer
    private long sessionId;

    @ByteTransfer(provider = TransferJsonProvider.class)
    private ExportedMessage message;
}