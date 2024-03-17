package me.moonways.bridgenet.model.socials;

import me.moonways.bridgenet.model.socials.result.SocialBindingResult;
import me.moonways.bridgenet.rsi.service.RemoteService;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SocialsServiceModel extends RemoteService {

    /**
     * Найти привязанный профиль игрока в определенной
     * указанной соц. сети.
     * В случае, если указанная соц. сеть не привязана игроком,
     * функция вернет пустой java.lang.Optional.
     *
     * @param playerId - идентификатор игрока, которого ищем
     * @param social - социальная сеть, которая привязана к игроку.
     */
    Optional<SocialProfile> findLinkedProfile(UUID playerId, Social social) throws RemoteException;

    /**
     * Воспроизвести автоматический поиск социальной сети,
     * для которой были адресованы входящие параметры игроком
     * для ее привязки.
     * В случае если данные были введены некорректно или такой
     * социальной сети просто нет в базе данных, то функция
     * вернет пустой java.lang.Optional.
     *
     * @param input = входящие параметры социальной сети от игрока.
     */
    Collection<Social> findSocialsByInput(String input) throws RemoteException;

    /**
     * Воспроизвести попытку привязки социальной сети
     * к игроку с указанием входящих параметров для привязки
     * от самого игрока.
     *
     * @param playerId - идентификатор игрока, к которому привязываем.
     * @param social - социальная сеть, которую пытаемся привязать.
     * @param input - входящие данные для попытки привязки игрока к аккаунту.
     *
     * @return - возвращает результат привязки после подтверждения привязки
     *           самим игроком.
     */
    CompletableFuture<SocialBindingResult> tryLink(UUID playerId, Social social, String input) throws RemoteException;

    /**
     * Воспроизвести попытку отвязки социальной сети
     * от игрока.
     * В случае если социальная сеть не была привязана
     * к указанному игроку, функция вернет ошибку NOT_LINKED.
     *
     * @param playerId - идентификатор игрока, к которому привязываем.
     * @param social - социальная сеть, которую пытаемся привязать.
     *
     * @return - возвращает результат привязки после подтверждения привязки
     *           самим игроком.
     */
    CompletableFuture<SocialBindingResult> tryUnlink(UUID playerId, Social social) throws RemoteException;
}
