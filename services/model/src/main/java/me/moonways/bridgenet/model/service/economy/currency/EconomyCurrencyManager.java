package me.moonways.bridgenet.model.service.economy.currency;

import me.moonways.bridgenet.model.service.economy.currency.bank.BankTransaction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * Данный управленческий класс отвечает
 * за вызовы банковских транзакций для проведения
 * операций над валютами относительно пользователя,
 * который хранит данный менеджер, кешируя его данные
 * и тем самым оптимизируя дальнейшие операции.
 */
public interface EconomyCurrencyManager extends Remote {

    CurrencyOperationHistory getHistory();

    /**
     * Выполнить запрос "ЭХО", который прослушивает
     * актуальные данные пользователя относительно
     * управляемой валюты и выдает в результате
     * актуальную информацию о транзакциях пользователя.
     */
    BankTransaction executeEcho() throws RemoteException;

    /**
     * Выполнить запрос "ОПЛАТА", который снимает
     * определенное количество суммы со счета пользователя.
     *
     * @param sum - сумма, которую снимаем со счета.
     */
    BankTransaction executePay(int sum) throws RemoteException;

    /**
     * Выполнить запрос "ПОПОЛНЕНИЕ", который пополняет на
     * определенное количество суммы счет пользователя.
     *
     * @param sum - сумма, на которую пополняем счета.
     */
    BankTransaction executeCharge(int sum) throws RemoteException;

    /**
     * Выполнить запрос "ПЕРЕВОД", который снимает
     * определенное количество суммы со счета пользователя
     * и переводит эту сумму другому пользователю.
     *
     * @param receiverId - уникальный идентификатор пользователя-получателя.
     * @param sum - сумма, которую снимаем со счета.
     */
    BankTransaction executeTransfer(UUID receiverId, int sum) throws RemoteException;
}
