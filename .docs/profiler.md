# Bridgenet / Metrics

Profiler - Вложенный модуль системы Bridgenet, отвечающий за профилирование
<br>и сбор метрик системы: потребление памяти, сетевой трафик и соединения,
<br>статистика SQL-запросов, - с последующей визуализацией собранных данных
<br>в виде графиков через QuickChart API.

---

## BUILD

Модуль объявлен как maven-модуль `profiler` в корневом `pom.xml` проекта,
<br>его версия задается property `profiler.version` в корневом `pom.xml`:

```xml
<profiler.version>1.0</profiler.version>
```

Из внутренних модулей системы profiler зависит только от модуля `assembly`,
<br>через который читает свою конфигурацию из ресурса `ResourcesTypes.PROFILER_ATTRIBUTES_JSON`
<br>(файл `profiler_attributes.json` в директории 'etc' модуля `assembly`).
<br>Из внешних зависимостей используется `org.apache.httpcomponents:httpclient`
<br>для исполнения HTTP-запросов к QuickChart API.

В процессе сборки проекта скриптом `.scripts/project_build.sh`
<br>(команды `./bridgenet jar` или `./bridgenet build`) модуль устанавливается
<br>в локальный maven-репозиторий одним из первых в очереди модулей,
<br>так как от него зависит модуль `api`, а через него - и остальная система.
<br>Отдельного jar-файла в директории сборки '.build' модуль не имеет:
<br>его классы попадают в общий исполняемый файл `bridgenet-server.jar`,
<br>а конфигурация `profiler_attributes.json` копируется в '.build/etc'
<br>вместе с остальными ресурсами модуля `assembly`.

Содержимое конфигурации `profiler_attributes.json`:

```json
{
  "imagesStore": {
    "folderName": "profiler-logs",
    "enabled": true
  },
  "dimensions": {
    "width": 1200,
    "height": 600
  }
}
```

Если параметр `imagesStore.enabled` включен, то сгенерированные иллюстрации
<br>метрик дополнительно сохраняются в формате PNG в директорию `profiler-logs`
<br>в корневой папке проекта - рядом с директорией `logs`, в которую log4j2
<br>пишет текстовые логи системы. Размеры изображений берутся из параметра `dimensions`.
<br>Модель этой конфигурации в коде - класс `me.moonways.bridgenet.profiler.ProfilerSettings`.

---

## API

Основной точкой входа модуля является класс
<br>`me.moonways.bridgenet.profiler.BridgenetDataLogger` - он автоматически
<br>регистрируется как bean в системе Dependency Injection, поэтому для его
<br>использования достаточно проинжектить его:

```java

@Inject
private BridgenetDataLogger bridgenetDataLogger;
```

При создании данный класс регистрирует по одной метрике на каждый тип из
<br>перечисления `me.moonways.bridgenet.profiler.ProfilerType`:
<br>`MTP_TRAFFIC`, `MTP_CONNECTIONS`, `JDBC_QUERIES`, `HTTP_REST`, `MEMORY`.
<br>Приведем примеры.

Для логирования актуальных **показателей памяти** виртуальной машины
<br>мы можем использовать следующий функционал:

```java
bridgenetDataLogger.logRuntimeMemoryFree();
bridgenetDataLogger.logRuntimeMemoryTotal();
bridgenetDataLogger.logRuntimeMemoryUsed();
```

Для логирования **сетевого трафика** (количества прочтенных и записанных байтов)
<br>мы можем использовать следующий функционал:

```java
bridgenetDataLogger.logReadsCount(ProfilerType.MTP_TRAFFIC, byteBuf.readableBytes());
bridgenetDataLogger.logWritesCount(ProfilerType.MTP_TRAFFIC, byteBuf.readableBytes());
```

Для логирования **открытия и закрытия соединений** в рамках определенной сети
<br>мы можем использовать следующий функционал:

```java
bridgenetDataLogger.logConnectionOpen(ProfilerType.MTP_CONNECTIONS);
bridgenetDataLogger.logConnectionClose(ProfilerType.MTP_CONNECTIONS);
```

Для логирования **статистики SQL-запросов и транзакций** базы данных
<br>мы можем использовать следующий функционал:

```java
bridgenetDataLogger.logJdbcQueryCompleted();
bridgenetDataLogger.logJdbcQueryFailed();
bridgenetDataLogger.logTransactionOpen();
bridgenetDataLogger.logTransactionRollback();
```

Для получения **ссылки на готовую иллюстрацию** графика метрики
<br>мы можем использовать следующий функционал:

```java
String renderedImageUrl = bridgenetDataLogger.renderProfilerChart(ProfilerType.MEMORY);
```

Тип графика для каждой метрики задается перечислением
<br>`me.moonways.bridgenet.profiler.chart.ChartType` (`LINE`, `SPARKLINE`, `POLAR_AREA` и т.д.)
<br>и берется из свойства `defaultChartType` соответствующего `ProfilerType`.
<br>Генерация иллюстрации исполняется через `me.moonways.bridgenet.profiler.quickchart.QuickChartApi`,
<br>который отправляет POST-запрос на эндпоинт `https://quickchart.io/chart/create`.

Также иллюстрации всех зарегистрированных метрик можно запросить из консоли
<br>сервера командой `profilers` (алиасы: `profiler`, `profiling`, `metric`, `metrics`).

Для создания **собственной метрики**, не привязанной к типам `ProfilerType`,
<br>можно использовать низкоуровневый провайдер `me.moonways.bridgenet.profiler.ProfilerProvider`:

```java
ProfilerProvider profilerProvider = new ProfilerProvider();

Profiler profiler = profilerProvider.createMetric("Online Players");

profiler.put("Lobby", 120);
profiler.add("Lobby", 5);
profiler.subtract("Lobby", 2);

Long actualValue = profiler.get("Lobby"); // 123

String illustrationUrl = profilerProvider.provideMetricIllustration(ChartType.LINE, profiler);
```

Объект `me.moonways.bridgenet.profiler.Profiler` хранит значения метрики
<br>с временными отметками (`me.moonways.bridgenet.profiler.TimedValue`)
<br>и автоматически очищает наиболее устаревшие из них при переполнении кеша.

---
