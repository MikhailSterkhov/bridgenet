package me.moonways.bridgenet.model.service.economy.credit.rating;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface CreditRatingManager extends Remote {
    int DEFAULT_RATING = 500;
    int MIN_RATING = 1;
    int MAX_RATING = 999;

    int recalculate(UUID playerId) throws RemoteException;

    int lookup(UUID playerId) throws RemoteException;
}
