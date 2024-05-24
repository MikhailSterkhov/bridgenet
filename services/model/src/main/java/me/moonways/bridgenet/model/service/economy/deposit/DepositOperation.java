package me.moonways.bridgenet.model.service.economy.deposit;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.economy.currency.bank.BankTransaction;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public class DepositOperation {

    public enum Result {

        SUCCESS_OPERATION,
        CANCELLED,
        NOT_ENOUGH_FUNDS,
    }

    private final ActiveDeposit deposit;
    private final BankTransaction transaction;

    private final Result result;
}
