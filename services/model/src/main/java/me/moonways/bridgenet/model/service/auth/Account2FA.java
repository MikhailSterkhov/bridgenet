package me.moonways.bridgenet.model.service.auth;

import java.util.concurrent.CompletableFuture;

public interface Account2FA {

    /**
     * Запросить подтверждение о входе пользователя
     * из дополнительной системы защиты аккаунта, к которой
     * сам пользователь привязывал свой аккаунт.
     *
     * @param security - система дополнительной защиты, к которой обращаемся за подтверждением.
     */
    CompletableFuture<SecurityConfirmResult> requestConfirmation(Security2FA security);

    /**
     * Запросить подтверждение о входе пользователя
     * из дополнительной системы защиты аккаунта, к которой
     * сам пользователь привязывал свой аккаунт, и она является
     * основной для самого пользователя.
     * "Основная" система дополнительной защиты может быть единственной
     * подключенной системой дополнительной защиты, или специально
     * выбранной пользователем в настройках аккаунта.
     */
    CompletableFuture<SecurityConfirmResult> requestConfirmationMaintained();
}
