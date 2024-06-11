package me.moonways.bridgenet.model.service.players.component;

import me.moonways.bridgenet.model.event.PlayerPostRedirectEvent;
import me.moonways.bridgenet.model.service.servers.EntityServer;
import me.moonways.bridgenet.mtp.message.ExportedMessage;
import org.jetbrains.annotations.NotNull;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Представляет собой удаленный интерфейс
 * для управления соединением игрока с сервером в сетевой игре.
 * Этот интерфейс предоставляет методы для подключения игрока
 * к серверу и получения информации о текущем сервере, к которому подключен игрок.
 */
public interface PlayerConnection extends Remote {

    /**
     * Подключить игрока к указанному серверу.
     *
     * @param server - Сервер, к которому нужно подключить игрока.
     * @return - Объект CompletableFuture, представляющий асинхронный результат операции подключения.
     */
    CompletableFuture<PlayerPostRedirectEvent> connect(@NotNull EntityServer server) throws RemoteException;

    /**
     * Подключить игрока к серверу с указанным идентификатором.
     *
     * @param serverID - Идентификатор сервера, к которому нужно подключить игрока.
     * @return - Объект CompletableFuture, представляющий асинхронный результат операции подключения.
     */
    CompletableFuture<PlayerPostRedirectEvent> connect(@NotNull UUID serverID) throws RemoteException;

    /**
     * Получить текущий сервер, к которому подключен игрок.
     *
     * @return - Опциональный объект EntityServer, представляющий текущий
     * сервер игрока, если он подключен, в противном случае пустое значение.
     */
    Optional<EntityServer> getServer() throws RemoteException;

    /**
     * Получить сервер, к которому игрок был подключен при входе.
     *
     * @return - Опциональный объект EntityServer, представляющий сервер,
     * к которому игрок был подключен при входе, если он задан, в противном случае пустое значение.
     */
    Optional<EntityServer> getServerOnJoined() throws RemoteException;

    /**
     * Отправить сообщение на подключенный канал.
     *
     * @param message - отправляемое сообщение.
     */
    void send(@NotNull Object message) throws RemoteException;

    /**
     * Отправить сообщение на подключенный канал.
     *
     * @param message - отправляемое сообщение.
     */
    void send(@NotNull ExportedMessage message) throws RemoteException;

    /**
     * Отправить сообщение на подключенный канал
     * с ожиданием ответа.
     *
     * @param responseType - тип ожидаемого ответа.
     * @param message      - отправляемое сообщение.
     */
    <R> CompletableFuture<R> sendAwait(@NotNull Class<R> responseType, @NotNull Object message) throws RemoteException;

    /**
     * Отправить сообщение на подключенный канал
     * с ожиданием ответа.
     *
     * @param timeout      - таймаут ожидания сообщения
     * @param responseType - тип ожидаемого ответа.
     * @param message      - отправляемое сообщение.
     */
    <R> CompletableFuture<R> sendAwait(int timeout, @NotNull Class<R> responseType, @NotNull Object message) throws RemoteException;
}
