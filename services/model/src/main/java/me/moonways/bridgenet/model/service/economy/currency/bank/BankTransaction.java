package me.moonways.bridgenet.model.service.economy.currency.bank;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.economy.currency.Currency;

import java.util.UUID;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public final class BankTransaction {

    public enum Result {

        SUCCESS_OPERATION,
        NOT_ENOUGH,
        NOT_FOUND,
    }

    @Getter
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class PostTransferState {

        private final Currency currency;

        private final int current;
        private final int spent;
        private final int received;
    }

    private final UUID transactionId;

    private final Result result;
    private final PostTransferState state;
}
