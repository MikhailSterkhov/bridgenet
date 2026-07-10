package me.moonways.bridgenet.client.api;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.moonways.bridgenet.client.api.data.*;
import me.moonways.bridgenet.model.message.*;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BridgenetServerSync {

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private BridgenetNetworkChannel channel;
    @Getter
    private UUID currentClientId;

    /**
     * Выполнить обмен данными с единым сервером Bridgenet при
     * помощи рукопожатия и получить в результате уникальный идентификатор
     * текущего устройства.
     *
     * @param description - описание подключаемого устройства.
     */
    public synchronized Handshake.Result exportClientHandshake(ClientDto description) {
        CompletableFuture<Handshake.Result> future = channel.sendAwait(Handshake.Result.class,
                new Handshake(Handshake.Type.SERVER, description.toProperties(), ProtocolVersion.CURRENT));

        Handshake.Result result = future.join();
        result.onSuccess(() -> currentClientId = result.getKey());

        return result;
    }

    /**
     * Выполнить обмен данными для создания игры с
     * единым сервером Bridgenet.
     *
     * @param description - описание подключаемой игры.
     */
    public synchronized CreateGame.Result exportGameCreate(GameDto description) {
        CompletableFuture<CreateGame.Result> future = channel.sendAwait(CreateGame.Result.class,
                new CreateGame(
                        description.getName(),
                        description.getMap(),
                        description.getMaxPlayers(),
                        description.getPlayersInTeam()));
        return future.join();
    }

    /**
     * Экспортировать обновление данных одной из активных игр.
     *
     * @param description - описание подключаемой игры.
     */
    public synchronized void exportGameUpdate(GameStateDto description) {
        ActiveGameDto activeGame = description.getActiveGame();
        channel.send(new UpdateGame(activeGame.getGameId(), activeGame.getActiveId(),
                description.getStatus(),
                description.getSpectators(),
                description.getPlayers()));
    }

    /**
     * Экспортировать исполнение команды на единый
     * сервер Bridgenet и делегировать данную ответственность
     * на него.
     *
     * @param description - описание пользователя.
     * @param label       - введенная пользователем строка команды.
     * @return - возвращает TRUE если команда была успешно исполнена.
     */
    public synchronized boolean exportCommandSend(UserDto description, String label) {
        CompletableFuture<SendCommand.Result> future = channel.sendAwait(SendCommand.Result.class,
                new SendCommand(description.getUniqueId(), label));

        SendCommand.Result commandSendResult = future.join();
        return (commandSendResult instanceof SendCommand.Success);
    }

    /**
     * Экспортировать удаление активной игры из
     * сети единого сервера Bridgenet.
     *
     * @param description - описание подключенной и активной игры.
     */
    public synchronized void exportGameDelete(ActiveGameDto description) {
        channel.send(new DeleteGame(description.getGameId(), description.getActiveId()));
    }

    /**
     * Отправить сообщение об отсоединении текущего устройства
     */
    public synchronized void exportClientDisconnect() {
        if (currentClientId != null) {
            channel.send(new Disconnect(currentClientId, Disconnect.Type.SERVER));
        }
    }

    /**
     * Отправить сообщение об отсоединении пользователя.
     *
     * @param description - описание подключаемого пользователя.
     */
    public synchronized void exportUserDisconnect(UserDto description) {
        channel.send(new Disconnect(description.getUniqueId(), Disconnect.Type.PLAYER));
    }

    /**
     * Выполнить обмен данными подключаемого
     * пользователя к единому серверу Bridgenet
     * при помощи рукопожатия и получить в результате
     * уникальный идентификатор пользователя
     * в качестве подтверждения его инициализации.
     *
     * @param description - описание подключаемого пользователя.
     * @return - возвращает TRUE если вернувшийся идентификатор совпадает с пользовательским.
     */
    public synchronized boolean exportUserHandshake(UserDto description) {
        CompletableFuture<Handshake.Result> future = channel.sendAwait(Handshake.Result.class,
                new Handshake(Handshake.Type.PLAYER, description.toProperties(), ProtocolVersion.CURRENT));

        Handshake.Result result = future.join();

        return (result instanceof Handshake.Success)
                && result.getKey() != null
                && result.getKey().equals(description.getUniqueId());
    }

    /**
     * Экспортировать отправку текстового сообщения пользователю
     * через сеть единого сервера Bridgenet.
     *
     * @param chatType - тип чата, в котором отрисовать сообщение пользователю
     * @param playerId - идентификатор активного пользователя.
     * @param message  - текстовое сообщение
     */
    public synchronized void exportUserMessageSend(SendMessage.ChatType chatType, UUID playerId, String message) {
        channel.send(new SendMessage(playerId, message, chatType));
    }

    /**
     * Экспортировать процесс переподключения игрока на
     * другой подключенный сервер на сеть единого сервера Bridgenet.
     *
     * @param playerId - идентификатор переподключаемого игрока.
     * @param serverId - идентификатор устройства, на который переподключать.
     * @return - возвращает TRUE в случае удачной попытки переподключения.
     */
    public synchronized boolean exportUserRedirectWithResult(UUID playerId, UUID serverId) {
        CompletableFuture<Redirect.Result> future = channel.sendAwait(Redirect.Result.class,
                new Redirect(playerId, serverId));

        Redirect.Result result = future.join();
        return (result instanceof Handshake.Success);
    }

    /**
     * Экспортировать процесс переподключения игрока на
     * другой подключенный сервер на сеть единого сервера Bridgenet.
     *
     * @param playerId - идентификатор переподключаемого игрока.
     * @param serverId - идентификатор устройства, на который переподключать.
     */
    public synchronized void exportUserRedirect(UUID playerId, UUID serverId) {
        channel.send(new Redirect(playerId, serverId));
    }

    /**
     * Экспортировать процесс переподключения игрока на
     * текущий подключенный сервер на сеть единого сервера Bridgenet.
     *
     * @param playerId - идентификатор переподключаемого игрока.
     * @return - возвращает TRUE в случае удачной попытки переподключения.
     */
    public synchronized boolean exportUserRedirectToHereWithResult(UUID playerId) {
        if (currentClientId != null) {
            return exportUserRedirectWithResult(playerId, currentClientId);
        }
        return false;
    }

    /**
     * Экспортировать процесс переподключения игрока на
     * текущий подключенный сервер на сеть единого сервера Bridgenet.
     *
     * @param playerId - идентификатор переподключаемого игрока.
     */
    public synchronized void exportUserRedirectToHere(UUID playerId) {
        if (currentClientId != null) {
            exportUserRedirect(playerId, currentClientId);
        }
    }

    /**
     * Экспортировать отправку текстового сообщения пользователю
     * на весь экран через сеть единого сервера Bridgenet.
     *
     * @param playerId    - идентификатор активного пользователя.
     * @param description - описание дополнительных параметров сообщения.
     */
    public synchronized void exportUserTitleSend(UUID playerId, TitleDto description) {
        channel.send(new SendTitle(playerId,
                description.getTitle(),
                description.getSubtitle(),
                description.getFadeIn(),
                description.getStay(),
                description.getFadeOut()));
    }

    /**
     * Запросить у единого сервера Bridgenet список команд,
     * зарегистрированных в нем для возможности исполнения на данном устройстве.
     */
    public synchronized List<String> lookupServerCommandsList() {
        CompletableFuture<GetCommands.Result> future
                = channel.sendAwait(GetCommands.Result.class, new GetCommands());

        GetCommands.Result result = future.join();
        return result.getList();
    }
}
