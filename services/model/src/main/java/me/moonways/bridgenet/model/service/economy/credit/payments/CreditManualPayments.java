package me.moonways.bridgenet.model.service.economy.credit.payments;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Optional;

public interface CreditManualPayments extends Remote {

    int getTotalAmount() throws RemoteException;

    Optional<ManualPayment> getFirst() throws RemoteException;

    Optional<ManualPayment> getLast() throws RemoteException;

    Collection<ManualPayment> getUnits() throws RemoteException;
}
