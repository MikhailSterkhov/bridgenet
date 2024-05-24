package me.moonways.bridgenet.model.service.economy.currency;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;

public interface CurrencyOperationHistory extends Remote {

    Optional<CurrencyOperationUnit> getLast() throws RemoteException;

    Collection<CurrencyOperationUnit> getUnitsOf(Date date) throws RemoteException;

    Optional<Long> getSumOf(Date date) throws RemoteException;
}
