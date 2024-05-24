package me.moonways.endpoint.economy.db;

import lombok.*;
import me.moonways.bridgenet.jdbc.core.compose.ParameterAddon;
import me.moonways.bridgenet.jdbc.entity.persistence.Entity;
import me.moonways.bridgenet.jdbc.entity.persistence.EntityParameter;

import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@Entity(name = "players_currency")
public class EntityBankPlayer {

    @Getter(onMethod_ = @EntityParameter(id = "player_id", indexes = ParameterAddon.PRIMARY))
    private final UUID playerId;

    @Getter(onMethod_ = @EntityParameter(order = 1, id = "currency"))
    private final String currencyName;

    @Getter(onMethod_ = @EntityParameter(order = 2))
    private final int sum;
}
