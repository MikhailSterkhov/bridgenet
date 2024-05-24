package me.moonways.bridgenet.model.service.economy.currency;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.economy.currency.bank.BankTransaction;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public class CurrencyOperationUnit {

    @Getter
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class Source {

        private final SourceType type;

        private final String title;
        private final String paymentDescription;
    }

    public enum OperationType {

        ECHO,
        PAY,
        CHARGE,
        TRANSFER,
    }

    public enum SourceType {

        INTERNAL,
        EXTERNAL,
    }

    private final BankTransaction transaction;

    private final Source source;
    private final OperationType type;

    private final int amount;
}
