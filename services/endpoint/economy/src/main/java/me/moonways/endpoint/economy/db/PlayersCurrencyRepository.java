package me.moonways.endpoint.economy.db;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.jdbc.entity.EntityRepository;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;
import me.moonways.bridgenet.model.service.economy.currency.Currency;

import java.util.UUID;

public class PlayersCurrencyRepository {

    private EntityRepository<EntityBankPlayer> repository;

    @Inject
    private EntityRepositoryFactory entityRepositoryFactory;

    @PostConstruct
    private void initDao() {
        repository = entityRepositoryFactory.fromEntityType(EntityBankPlayer.class);
    }

    public void updateEntity(UUID playerId, Currency currency, int value) {
        repository.insert(
                EntityBankPlayer.builder()
                        .playerId(playerId)
                        .currencyName(currency.name())
                        .sum(value)
                        .build());
    }

    public int getValue(UUID playerId, Currency currency) {
        return repository.searchIf(
                repository.newSearchMarker()
                    .withGet(EntityBankPlayer::getPlayerId, playerId)
                    .withGet(EntityBankPlayer::getCurrencyName, currency.name()))
                .map(EntityBankPlayer::getSum)
                .orElse(-1);
    }
}
