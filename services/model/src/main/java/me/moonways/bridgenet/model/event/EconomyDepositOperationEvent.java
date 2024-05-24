package me.moonways.bridgenet.model.event;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.economy.credit.CreditOperation;
import me.moonways.bridgenet.model.service.economy.deposit.DepositOperation;
import me.moonways.bridgenet.model.service.players.Player;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public class EconomyDepositOperationEvent {

    private final Player player;
    private final DepositOperation operation;
}
