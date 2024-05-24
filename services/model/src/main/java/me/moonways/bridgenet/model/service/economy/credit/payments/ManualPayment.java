package me.moonways.bridgenet.model.service.economy.credit.payments;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;

@Getter
@Builder
@ToString
public class ManualPayment {

    public enum Type {

        EARLY_PAYMENT_OFF,
        STAKE_REDUCTION,
    }

    private final Date date;
    private final Type type;

    private final int amount;
}
