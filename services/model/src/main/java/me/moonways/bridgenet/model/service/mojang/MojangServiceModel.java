package me.moonways.bridgenet.model.service.mojang;


import java.util.Optional;

/**
 * Интерфейс MojangServiceModel предоставляет методы для взаимодействия с сервисом Mojang.
 */
public interface MojangServiceModel {

    /**
     * Проверяет, является ли никнейм пиратским.
     *
     * @param nickname никнейм пользователя в Minecraft.
     * @return true, если никнейм пиратский, иначе false.
     */
    boolean isPirateNick(String nickname);

    /**
     * Проверяет, является ли идентификатор пиратским.
     *
     * @param id идентификатор пользователя в Minecraft.
     * @return true, если идентификатор пиратский, иначе false.
     */
    boolean isPirateId(String id);

    /**
     * Возвращает никнейм с оригинальным регистром символов.
     *
     * @param nickname никнейм пользователя в Minecraft.
     * @return Optional, содержащий никнейм с оригинальным регистром, если он найден.
     */
    Optional<String> getNameWithOriginCase(String nickname);

    /**
     * Возвращает идентификатор пользователя по его никнейму.
     *
     * @param nickname никнейм пользователя в Minecraft.
     * @return Optional, содержащий идентификатор пользователя, если он найден.
     */
    Optional<String> getMinecraftId(String nickname);

    /**
     * Возвращает никнейм пользователя по его идентификатору.
     *
     * @param id идентификатор пользователя в Minecraft.
     * @return Optional, содержащий никнейм пользователя, если он найден.
     */
    Optional<String> getMinecraftNick(String id);

    /**
     * Возвращает скин пользователя по его никнейму.
     *
     * @param nickname никнейм пользователя в Minecraft.
     * @return Optional, содержащий скин пользователя, если он найден.
     */
    Optional<Skin> getMinecraftSkinByNick(String nickname);

    /**
     * Возвращает скин пользователя по его идентификатору.
     *
     * @param id идентификатор пользователя в Minecraft.
     * @return Optional, содержащий скин пользователя, если он найден.
     */
    Optional<Skin> getMinecraftSkinById(String id);
}
