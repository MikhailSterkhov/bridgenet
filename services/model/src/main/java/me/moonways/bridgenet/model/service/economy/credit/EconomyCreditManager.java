package me.moonways.bridgenet.model.service.economy.credit;

import me.moonways.bridgenet.model.service.economy.credit.rating.CreditRatingManager;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.UUID;

public interface EconomyCreditManager extends Remote {

    CreditRatingManager getRatingManager() throws RemoteException;

    Collection<ActiveCredit> getCredits(UUID playerId) throws RemoteException;

    CreditOperation executeCreditGet(UUID playerId, CreditSource source, int amount) throws RemoteException;

    CreditOperation executeCreditPayment(UUID creditId, int amount) throws RemoteException;
}
