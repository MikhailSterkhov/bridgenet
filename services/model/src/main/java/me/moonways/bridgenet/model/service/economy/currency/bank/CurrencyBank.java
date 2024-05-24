package me.moonways.bridgenet.model.service.economy.currency.bank;

import me.moonways.bridgenet.model.service.economy.currency.Currency;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * Данный управленческий класс отвечает за запросы и
 * прямое взаимодействие с актуальными данными пользователей
 * относительно валюты, под которую он заточен.
 * Основной процесс поведения заточен под прямое исполнение запросов
 * через базы данных, и кешированием эти запросы не обложены.
 */
public interface CurrencyBank extends Remote {

    Currency getCurrency() throws RemoteException;

    /**
     * Выполнить запрос "ЭХО", который прослушивает
     * актуальные данные пользователя относительно
     * управляемой валюты и выдает в результате
     * актуальную информацию о транзакциях пользователя.
     *
     * @param playerId - уникальный идентификатор пользователя, с которым проводим операцию.
     */
    BankTransaction echo(UUID playerId) throws RemoteException;

    /**
     * Выполнить оплату, который снимает
     * определенное количество суммы со счета пользователя.
     *
     * @param playerId - уникальный идентификатор пользователя, с которым проводим операцию.
     * @param sum - сумма, которую снимаем со счета.
     */
    BankTransaction pay(UUID playerId, int sum) throws RemoteException;

    /**
     * Выполнить пополнение, который пополняет на
     * определенное количество суммы счет пользователя.
     *
     * @param playerId - уникальный идентификатор пользователя, с которым проводим операцию.
     * @param sum - сумма, на которую пополняем счета.
     */
    BankTransaction charge(UUID playerId, int sum) throws RemoteException;

    /**
     * Выполнить перевод, который снимает
     * определенное количество суммы со счета пользователя
     * и переводит эту сумму другому пользователю.
     *
     * @param senderId - уникальный идентификатор пользователя, с которым проводим операцию.
     * @param receiverId - уникальный идентификатор пользователя-получателя.
     * @param sum - сумма, которую снимаем со счета.
     */
    BankTransaction transfer(UUID senderId, UUID receiverId, int sum) throws RemoteException;
}
