package me.moonways.bridgenet.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.games.GameStatus;
import me.moonways.bridgenet.mtp.message.persistence.ClientMessage;
import me.moonways.bridgenet.mtp.transfer.ByteTransfer;
import me.moonways.bridgenet.mtp.transfer.provider.ToEnumProvider;
import me.moonways.bridgenet.mtp.transfer.provider.ToUUIDProvider;

import java.util.UUID;

@Getter
@ToString
@ClientMessage
@NoArgsConstructor(onConstructor_ = @Inject)
@AllArgsConstructor
public class UpdateGame {

    @ByteTransfer(provider = ToUUIDProvider.class)
    private UUID gameId;

    @ByteTransfer(provider = ToUUIDProvider.class)
    private UUID activeId;

    @ByteTransfer(provider = ToEnumProvider.class)
    private GameStatus status;

    @ByteTransfer
    private int spectators;

    @ByteTransfer
    private int players;
}
