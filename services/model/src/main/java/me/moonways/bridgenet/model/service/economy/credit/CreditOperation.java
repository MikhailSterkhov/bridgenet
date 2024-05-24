package me.moonways.bridgenet.model.service.economy.credit;

import lombok.*;
import me.moonways.bridgenet.model.service.economy.currency.bank.BankTransaction;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public class CreditOperation {

    public enum Result {

        SUCCESS_OPERATION,
        CANCELLED,
        CREDIT_NOT_FOUND,
        CREDIT_WAS_CLOSED,
        CREDIT_AMOUNT_LESS,
    }

    private final ActiveCredit credit;

    private final Result result;

    private final BankTransaction transaction;
}
