package me.moonways.bridgenet.model.socials;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * Данный интерфейс описывает поведение привязанной
 * социальной сети к игроку, и хранит в себе базовые
 * геттеры для получения важной информации о профиле игрока
 * в определенной социальной сети для ее дальнейшей отрисовки где-либо.
 */
public interface SocialProfile extends Remote {

    /**
     * Идентификатор игрока, к которому относится данный профиль.
     */
    UUID getPlayerId() throws RemoteException;

    /**
     * Тип социальной сети, к которой относится данный профиль.
     */
    Social getSocialType() throws RemoteException;

    /**
     * Идентификатор пользователя из текущей социальной сети.
     */
    String getSocialId() throws RemoteException;

    /**
     * Ссылка на профиль пользователя из текущей социальной сети.
     */
    String getSocialLink() throws RemoteException;
}
