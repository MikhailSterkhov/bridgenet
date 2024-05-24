package me.moonways.endpoint.economy.currency;

import lombok.Getter;
import me.moonways.bridgenet.model.service.economy.currency.CurrencyOperationHistory;
import me.moonways.bridgenet.model.service.economy.currency.EconomyCurrencyManager;
import me.moonways.bridgenet.model.service.economy.currency.bank.BankTransaction;
import me.moonways.bridgenet.model.service.economy.currency.bank.CurrencyBank;

import java.rmi.RemoteException;
import java.util.UUID;

// todo - оптимизировать, добавив кеширование данных
public class CurrencyManagerStub implements EconomyCurrencyManager {

    private final UUID playerId;
    private final CurrencyBank bank;

    @Getter
    private final CurrencyOperationHistory history;

    public CurrencyManagerStub(UUID playerId, CurrencyBank bank) throws RemoteException {
        super();

        this.playerId = playerId;
        this.bank = bank;

        this.history = new CurrencyHistoryStub(playerId);
    }

    @Override
    public BankTransaction executeEcho() throws RemoteException {
        return bank.echo(playerId);
    }

    @Override
    public BankTransaction executePay(int sum) throws RemoteException {
        return bank.pay(playerId, sum);
    }

    @Override
    public BankTransaction executeCharge(int sum) throws RemoteException {
        return bank.charge(playerId, sum);
    }

    @Override
    public BankTransaction executeTransfer(UUID receiverId, int sum) throws RemoteException {
        return bank.transfer(playerId, receiverId, sum);
    }
}
