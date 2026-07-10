# BridgeNet / Services / Auth

Auth - Внутренний сервис, отвечающий за регистрацию и авторизацию
пользовательских аккаунтов в системе: вход и выход из аккаунта, смену
пароля, удаление аккаунта, а также за данные сессий авторизации
и подтверждение входа через системы дополнительной защиты (2FA).

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.auth.AuthServiceModel`:

```java

@Inject
private AuthServiceModel serviceModel;
```

Данный интерфейс предоставляет функционал управления аккаунтами
<br>зарегистрированных пользователей и процессами их авторизации.
<br>Приведем примеры.

Для поиска **зарегистрированного аккаунта** по идентификатору
<br>пользователя мы можем использовать следующий функционал:

```java
Optional<Account> accountOptional = serviceModel.findAccountById(playerId);

if (!accountOptional.isPresent()) {
    // account is not registered.
}
```

Для **регистрации** нового пользователя или **входа** в уже существующий
<br>аккаунт мы можем использовать следующий функционал:

```java
AuthorizationResult result = serviceModel.tryRegistration(playerId, inputPassword);

if (result == AuthorizationResult.FAILURE__ALREADY_REGISTERED) {
    // account is already registered.
}
```

```java
AuthorizationResult result = serviceModel.tryLogin(playerId, inputPassword);

if (result == AuthorizationResult.SUCCESS) {
    // player has been authorized.
}
```

Каждая операция авторизации возвращает `AuthorizationResult` - перечисление,
<br>по которому можно узнать конкретную причину ошибки входных данных
<br>(например `FAILURE__UNCORRECTED_PASSWORD`, `FAILURE__ALREADY_LOGGED`,
<br>`FAILURE__ACCOUNT_NOT_FOUND`, и т.д.).

Для **смены пароля**, **выхода из аккаунта** или его **удаления**
<br>мы можем использовать следующий функционал:

```java
AuthorizationResult result = serviceModel.tryPasswordChange(playerId, actualPassword, newPassword);
```

```java
AuthorizationResult result = serviceModel.tryLogOut(playerId);
```

```java
AuthorizationResult result = serviceModel.tryAccountDelete(playerId);
```

Для получения информации о **сессии авторизации** аккаунта
<br>мы можем использовать следующий функционал:

```java
Account account = accountOptional.get();

if (account.hasActiveSession()) {
    AuthenticationSession session = account.getSession();

    Timestamp lastAuthenticationDate = session.getLastAuthenticationDate();
    String lastAuthenticationIp = session.getLastAuthenticationIp();
}
```

Для запроса **подтверждения входа** через подключенную к аккаунту
<br>систему **дополнительной защиты** (2FA) мы можем использовать
<br>следующий функционал:

```java
Account2FA additionalSecurity = account.getAdditionalSecurity();

if (account.hasAdditionalSecurity()) {
    CompletableFuture<SecurityConfirmResult> future
            = additionalSecurity.requestConfirmation(Security2FA.AUTHENTICATOR_APP);

    future.thenAccept(result -> {
        if (result == SecurityConfirmResult.SUCCESS) {
            // authentication has been confirmed.
        }
    });
}
```

или же запросить подтверждение из **основной** системы дополнительной
<br>защиты, выбранной самим пользователем:

```java
CompletableFuture<SecurityConfirmResult> future
        = additionalSecurity.requestConfirmationMaintained();
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7000</bindPort>
    <!-- Service direction name -->
    <name>auth</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.auth.AuthServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/auth`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.auth.AuthServiceEndpoint`;
- Реализация эндпоинта работает с базой данных через внутренний JDBC-модуль
  <br>системы Bridgenet - в эндпоинт инжектируются `me.moonways.bridgenet.jdbc.core.DatabaseConnection`
  <br>и `me.moonways.bridgenet.jdbc.provider.DatabaseProvider`;
- На текущий момент бизнес-логика эндпоинта находится в разработке:
  <br>методы имплементации помечены `//todo` и возвращают значения-заглушки
  <br>(`Optional.empty()`, `AuthorizationResult.FAILURE`).
