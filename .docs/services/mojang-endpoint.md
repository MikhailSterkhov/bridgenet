# BridgeNet / Services / Mojang

Mojang - Внутренний сервис, делегирующийся и обменивающийся запросами с 
production REST-эндпоинтами компании Mojang для получения информации
об игровых аккаунтах Minecraft.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.mojang.MojangServiceModel`:

```java

@Inject
private MojangServiceModel serviceModel;
```

Данный интерфейс предоставляет возможность получать актуальные
<br>и кешированные данные игровых аккаунтов Minecraft.
<br>Приведем примеры.

Для **верификации** пользователя как **пиратский/лицензионный**
<br>мы можем использовать следующий функционал:

```java
boolean isPirate = serviceModel.isPirateNick("Notch"); // false
```

```java
boolean isPirate = serviceModel.isPirateNick("aboba538174"); // true
```

Для получения актуального **идентификатора пользователя** по его
<br>ник-нейму мы можем использовать следующий функционал:

```java
Optional<String> idOptional = serviceModel.getMinecraftId("Notch");

if (!idOptional.isPresent()) {
    // user is not exists.
}
```

Для получения параметров **текстур установленного скина** на
<br>игровом аккаунте мы можем использовать следующий функционал:

```java
Optional<Skin> skinOptional = serviceModel.getMinecraftSkinByNick("Notch");

if (!skinOptional.isPresent()) {
    // user is not exists.
}
```

Для получения **оригинального никнейма** игрового аккаунта
<br>мы можем использовать следующий функционал:

```java
String id = "069a79f4....e38aaf5";
Optional<String> nicknameOptional = serviceModel.getMinecraftNick(id);

if (!nicknameOptional.isPresent()) {
    // user is not exists.
}
```

```java
String nickname = "nOtCh";
Optional<String> nicknameOptional = serviceModel.getNameWithOriginCase(nickname); // Notch

if (!nicknameOptional.isPresent()) {
    // user is not exists.
}
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7013</bindPort>
    <!-- Service direction name -->
    <name>mojang</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.mojang.MojangServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/mojang`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.mojang.MojangServiceEndpoint`;
- Реализация эндпоинта использует REST-запросы, результаты которых сразу кеширует
  <br>на определенное время, и при повторном запросе каких-либо данных преварительно
  <br>пытается найти их в кешированных.
