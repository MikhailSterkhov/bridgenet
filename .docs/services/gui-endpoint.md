# BridgeNet / Services / GUI

GUI - Внутриигровой сервис удаленного доступа, имеющий в себе модель
<br>и реализацию кастомных предметов, чарований и инвентарей с возможностью
<br>перехвата события о нажатии пользователем на любой слот открытого им инвентаря.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.gui.GuiServiceModel`:

```java
@Inject
private GuiServiceModel serviceModel;
```

Создание предметов может происходить как через `ItemStack`, так
<br>и напрямую через сам сервис путем получения под это отдельного
<br>сервиса, заточенного под управление предметами:

```java
ItemStack item = ItemStack.create()
        .name("It`s a diamond!")
        .material(Materials.DIAMOND);
```

```java
ItemStack item = serviceModel.getItems().named(Materials.DIAMOND, "It`s a diamond!");
```

Создание и управление инвентарями происходит только напрямую
<br>через вызов функций сервиса:

```java
GuiDescription description =
        GuiDescription.builder()
                .type(GuiType.CHEST)
                .size(GuiDescription.toSize(5, GuiType.CHEST))
                .title("It`s a chest inventory with 5 rows!")
                .build();

Gui gui = serviceModel.createGui(description);
gui.setItem(GuiSlot.center(description),
        ItemStack.create()
                .name("It`s a diamond!")
                .material(Materials.DIAMOND));
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7001</bindPort>
    <!-- Service direction name -->
    <name>gui</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.gui.GuiServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/gui`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.gui.GuiServiceEndpoint`;

Данные базовых материалов и чарований находятся в отдельной
<br>директории модуля `assembly` в формате _json_ - `/etc/minecraft_data`.
