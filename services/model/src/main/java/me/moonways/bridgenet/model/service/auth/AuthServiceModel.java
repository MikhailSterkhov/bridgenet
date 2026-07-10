package me.moonways.bridgenet.model.service.auth;

import java.util.Optional;
import java.util.UUID;

public interface AuthServiceModel {

    /**
     * Найти зарегистрированный аккаунт по идентификатору пользователя.
     * Если пользователя нет в базе данных зарегистрированных, то
     * функция вернет пустой java.util.Optional
     *
     * @param playerId - идентификатор пользователя, по которому ищем аккаунт.
     */
    Optional<Account> findAccountById(UUID playerId);

    /**
     * Данная функция воспроизводит попытку авторизации
     * уже существующего пользователя, и в случае если
     * во время этого процесса была обнаружена какая-то
     * ошибка входных данных, мы узнаем о ней конкретнее
     * из описания me.moonways.bridgenet.model.auth.AuthorizationResult
     *
     * @param playerId      - идентификатор пользователя.
     * @param inputPassword - пароль от аккаунта, который ввел пользователь в систему.
     */
    AuthorizationResult tryLogin(UUID playerId, String inputPassword);

    /**
     * Данная функция воспроизводит попытку регистрации
     * нового пользователя в системе, и в случае если
     * во время этого процесса была обнаружена какая-то
     * ошибка входных данных, мы узнаем о ней конкретнее
     * из описания me.moonways.bridgenet.model.auth.AuthorizationResult
     *
     * @param playerId      - идентификатор пользователя.
     * @param inputPassword - новый пароль аккаунта, который ввел пользователь в систему.
     */
    AuthorizationResult tryRegistration(UUID playerId, String inputPassword);

    /**
     * Данная функция воспроизводит попытку смены пароля
     * пользователя в системе, и в случае если
     * во время этого процесса была обнаружена какая-то
     * ошибка входных данных, мы узнаем о ней конкретнее
     * из описания me.moonways.bridgenet.model.auth.AuthorizationResult
     *
     * @param playerId       - идентификатор пользователя.
     * @param actualPassword - актуальный (текущий) пароль пользователя, введенный пользователем.
     * @param newPassword    - новый (изменяемый) пароль пользователя, введенный пользователем.
     */
    AuthorizationResult tryPasswordChange(UUID playerId, String actualPassword, String newPassword);

    /**
     * Данная функция воспроизводит попытку выхода из аккаунта
     * пользователя в системе, и в случае если
     * во время этого процесса была обнаружена какая-то
     * ошибка входных данных, мы узнаем о ней конкретнее
     * из описания me.moonways.bridgenet.model.auth.AuthorizationResult
     *
     * @param playerId - идентификатор пользователя.
     */
    AuthorizationResult tryLogOut(UUID playerId);

    /**
     * Данная функция воспроизводит попытку удаления аккаунта
     * пользователя в системе, и в случае если
     * во время этого процесса была обнаружена какая-то
     * ошибка входных данных, мы узнаем о ней конкретнее
     * из описания me.moonways.bridgenet.model.auth.AuthorizationResult
     *
     * @param playerId - идентификатор пользователя.
     */
    AuthorizationResult tryAccountDelete(UUID playerId);
}
