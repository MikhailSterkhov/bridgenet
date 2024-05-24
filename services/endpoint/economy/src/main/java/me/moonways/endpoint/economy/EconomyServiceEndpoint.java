package me.moonways.endpoint.economy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.economy.credit.EconomyCreditManager;
import me.moonways.bridgenet.model.service.economy.currency.Currency;
import me.moonways.bridgenet.model.service.economy.currency.EconomyCurrencyManager;
import me.moonways.bridgenet.model.service.economy.EconomyServiceModel;
import me.moonways.bridgenet.model.service.economy.currency.bank.CurrencyBank;
import me.moonways.bridgenet.model.service.economy.deposit.EconomyDepositManager;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteObject;
import me.moonways.endpoint.economy.currency.CurrencyBankStub;
import me.moonways.endpoint.economy.currency.CurrencyManagerStub;
import me.moonways.endpoint.economy.db.PlayersCurrencyRepository;

import java.rmi.RemoteException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class EconomyServiceEndpoint extends EndpointRemoteObject implements EconomyServiceModel {
    private static final long serialVersionUID = 2765291138630551133L;

    private final Cache<UUID, EconomyCurrencyManager> currencyManagersCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(1, TimeUnit.HOURS)
                    .build();

    private final PlayersCurrencyRepository dbRepository = new PlayersCurrencyRepository();

    @Inject
    private PlayersServiceModel playersServiceModel;

    public EconomyServiceEndpoint() throws RemoteException {
        super();
    }

    @Override
    public CurrencyBank createBank(Currency currency) throws RemoteException {
        return new CurrencyBankStub(currency, dbRepository);
    }

    @Override
    public EconomyCurrencyManager getCurrencyManager(UUID playerId, Currency currency) throws RemoteException {
        currencyManagersCache.cleanUp();
        EconomyCurrencyManager current = currencyManagersCache.asMap().get(playerId);

        if (current == null) {
            current = new CurrencyManagerStub(playerId, createBank(currency));
            currencyManagersCache.put(playerId, current);
        }

        return current;
    }

    @Override
    public EconomyCurrencyManager getCurrencyManager(String playerName, Currency currency) throws RemoteException {
        UUID playerId = playersServiceModel.store().idByName(playerName);
        return getCurrencyManager(playerId, currency);
    }

    @Override
    public EconomyDepositManager getDepositManager(UUID playerId, Currency currency) throws RemoteException {
        return null;
    }

    @Override
    public EconomyDepositManager getDepositManager(String playerName, Currency currency) throws RemoteException {
        return null;
    }

    @Override
    public EconomyCreditManager getCreditManager(UUID playerId, Currency currency) throws RemoteException {
        return null;
    }

    @Override
    public EconomyCreditManager getCreditManager(String playerName, Currency currency) throws RemoteException {
        return null;
    }
}
