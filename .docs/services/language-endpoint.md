# BridgeNet / Services / Language

Language - Внутриигровой сервис удаленного доступа, имеющий в себе модель
<br>и реализацию индивидуальных типов мировых языков относительно игроков.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.language.LanguageServiceModel`:

```java

@Inject
private LanguageServiceModel serviceModel;
```

С помощью данного интерфейса можно управлять как индивидуальными
<br>типами мировых языков относительно пользователей, так и использовать
<br>уже зарегистрированные ранее в системе:

```java
Language language = LanguageTypes.ITALY;

Component message = serviceModel.message(language, MessageTypes.GREETING_ON_JOIN);
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7012</bindPort>
    <!-- Service direction name -->
    <name>language</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.language.LanguageServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/language`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.language.LanguageServiceEndpoint`;

Реализация использует фреймворк базы данных от Bridgenet.
<br>Наименование используемых таблиц и сущностей

<br>

Examples: `player_languages` (`me.moonways.endpoint.language.EntityLanguage`)

| player_id                            | lang_id                              |
|--------------------------------------|--------------------------------------|
| 756d7e30-d173-4c85-8827-67aad3c258aa | 9cfefed8-fb94-37ba-a5cd-519d7d2bb5d7 |
| 04d15ffd-066e-4332-96ad-64670b7c56dd | a78c5bf6-9b40-3464-b954-ef76815c6fa0 |
