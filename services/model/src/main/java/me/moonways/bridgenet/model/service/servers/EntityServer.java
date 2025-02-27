package me.moonways.bridgenet.model.service.servers;

import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.mtp.message.ExportedMessage;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EntityServer extends Remote {
    String CHANNEL_PROPERTY = "entity.server.instance";

    /**
     * Получение уникального идентификатора сервера,
     * под которым он зарегистрирован в системе.
     */
    UUID getUniqueId() throws RemoteException;

    /**
     * Получить название сервера, под которым
     * он сам себя идентифицирует при рукопожатии.
     */
    String getName() throws RemoteException;

    /**
     * Получить информацию о сервере.
     */
    ServerInfo getServerInfo() throws RemoteException;

    /**
     * Получить канал сервера по протоколу MTP
     * для отправки и обработки сообщений.
     */
    BridgenetNetworkChannel getChannel() throws RemoteException;

    /**
     * Получение адреса сервера, который он сам для
     * себя идентифицировал при рукопожатии.
     */
    InetSocketAddress getInetAddress() throws RemoteException;

    /**
     * Подключить какого-то игрока к текущему серверу.
     *
     * @param player - игрок, которого подключаем.
     */
    CompletableFuture<Boolean> connectThat(@NotNull Player player) throws RemoteException;

    /**
     * Получить список подключенных игроков к серверу.
     */
    Collection<Player> getConnectedPlayers() throws RemoteException;

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

    /**
     * Получить общее число онлайна на сервере.
     */
    int getTotalOnline() throws RemoteException;
}
