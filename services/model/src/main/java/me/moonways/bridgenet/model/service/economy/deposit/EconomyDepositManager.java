package me.moonways.bridgenet.model.service.economy.deposit;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.UUID;

public interface EconomyDepositManager extends Remote {

    Collection<ActiveDeposit> getActiveDeposits(UUID playerId) throws RemoteException;

    DepositOperation openDeposit(UUID playerId) throws RemoteException;

    DepositOperation closeDeposit(UUID depositId) throws RemoteException;

    DepositOperation invest(UUID depositId, int sum) throws RemoteException;

    DepositOperation withdraw(UUID depositId, int sum) throws RemoteException;
}
