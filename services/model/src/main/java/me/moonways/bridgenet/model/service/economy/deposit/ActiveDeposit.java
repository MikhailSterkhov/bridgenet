package me.moonways.bridgenet.model.service.economy.deposit;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface ActiveDeposit extends Remote {
    int MIN_INVESTED_SUM = 45_000;

    UUID getPlayerId() throws RemoteException;

    UUID getDepositId() throws RemoteException;

    int getInvestedAmount() throws RemoteException;

    int getTotalAmount() throws RemoteException;

    double getStake() throws RemoteException;
}
