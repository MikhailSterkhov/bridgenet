package me.moonways.endpoint.economy.currency;

import me.moonways.bridgenet.model.service.economy.currency.CurrencyOperationHistory;
import me.moonways.bridgenet.model.service.economy.currency.CurrencyOperationUnit;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public class CurrencyHistoryStub implements CurrencyOperationHistory {

    private final UUID playerId;

    public CurrencyHistoryStub(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public Optional<CurrencyOperationUnit> getLast() throws RemoteException {
        return Optional.empty();
    }

    @Override
    public Collection<CurrencyOperationUnit> getUnitsOf(Date date) throws RemoteException {
        return null;
    }

    @Override
    public Optional<Long> getSumOf(Date date) throws RemoteException {
        return Optional.empty();
    }
}
