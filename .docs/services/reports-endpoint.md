# BridgeNet / Services / Reports

Reports - Внутренний сервис, отвечающий за прием, хранение и выдачу
жалоб (репортов) на игроков: создание жалобы с указанием причины,
<br>автора, нарушителя и сервера, а также получение общей статистики
по зарепорченным игрокам.

---

## MODEL

Для использования сервиса необходимо использовать модельный
<br>интерфейс `me.moonways.bridgenet.model.service.reports.ReportsServiceModel`:

```java

@Inject
private ReportsServiceModel serviceModel;
```

Данный интерфейс предоставляет возможность создавать жалобы на игроков
<br>и получать сводную информацию по уже созданным жалобам.
<br>Все методы модельного интерфейса объявляют `throws RemoteException`,
так как сервис работает поверх RMI-протокола.
<br>Приведем примеры.

Для **создания жалобы** на игрока с комментарием от автора
<br>мы можем использовать следующий функционал:

```java
Report report = serviceModel.createReport(
        ReportReason.CHEATING,   // причина жалобы
        "GitCoder",              // кто пожаловался
        "aboba538174",           // на кого пожаловались
        "Flying around the map", // комментарий (nullable)
        "BedWars-1");            // на каком сервере
```

Если комментарий не требуется, доступна укороченная перегрузка
<br>без параметра `comment`:

```java
Report report = serviceModel.createReport(
        ReportReason.CHAT_INSULTS, "GitCoder", "aboba538174", "BedWars-1");
```

Причина жалобы выбирается из перечисления
<br>`me.moonways.bridgenet.model.service.reports.ReportReason`:
`CHAT_INSULTS`, `CHAT_SABOTAGE`, `CHAT_PROVOCATION`, `TEAM_PROVOCATION`,
<br>`TEAM_SABOTAGE`, `CALL_FOR_ILLEGAL_ACTIONS`, `CHEATING`, `OTHER`.

Возвращаемый объект `Report` предоставляет полные данные созданной жалобы:

```java
ReportReason reason = report.getReason();
String reporter = report.getWhoReportedName();
String intruder = report.getIntruderName();
String comment = report.getComment();
String serverName = report.getServerName();
long createdTimeMillis = report.getCreatedTimeMillis();
```

Для получения **общего списка всех жалоб** и их количества
<br>мы можем использовать следующий функционал:

```java
List<Report> totalReports = serviceModel.getTotalReports();
int totalReportsCount = serviceModel.getTotalReportsCount();
```

Для получения **списка зарепорченных игроков** (жалобы сгруппированы
<br>по имени нарушителя в объекты `ReportedPlayer`) мы можем
использовать следующий функционал:

```java
List<ReportedPlayer> reportedPlayers = serviceModel.getTotalReportedPlayers();
int reportedPlayersCount = serviceModel.getTotalReportedPlayersCount();

for (ReportedPlayer reportedPlayer : reportedPlayers) {
    String intruderName = reportedPlayer.getName();
    int reportsOnPlayer = reportedPlayer.getTotalReportsCount();
    List<Report> reportsList = reportedPlayer.getTotalReports();
}
```

---

## ENDPOINT

Конфигурационные данные, на которых базируется запуск сервиса
<br>под имплементаций эндпоинта:

```xml

<service>
    <!-- RMI Protocol service bind port -->
    <bindPort>7009</bindPort>
    <!-- Service direction name -->
    <name>reports</name>
    <!-- Target service class type -->
    <modelPath>me.moonways.bridgenet.model.service.reports.ReportsServiceModel</modelPath>
</service>
```

- Реализация эндпоинта лежит в модуле `services/endpoint/reports`;
- Имплементацией основного модельного интерфейса сервиса
  <br>является `me.moonways.endpoint.reports.ReportsServiceEndpoint`;
- Жалобы хранятся в оперативной памяти эндпоинта: реализация ведет
  <br>внутренний список объектов `ReportedPlayer`, группируя жалобы
  <br>по имени нарушителя (без обращения к базе данных);
- Модельный интерфейс `Report` имплементирован удаленным объектом
  <br>`me.moonways.endpoint.reports.ReportStub`;
- При создании каждой новой жалобы реализация публикует событие
  <br>`me.moonways.bridgenet.model.event.ReportCreateEvent`
  <br>через внутреннюю шину событий `EventService`.
