# Bridgenet / Test-Engine

Test-Engine - Вложенный модуль системы Bridgenet, реализующий собственный
<br>тестовый фреймворк на основе JUnit 4 для написания интеграционных юнит-тестов.
<br>Движок поднимает систему Bridgenet прямо внутри тестового процесса, подгружая
<br>только необходимые тесту модули системы, и предоставляет инжекцию бинов,
<br>подготовительные шаги и управление порядком выполнения тестов.

Maven-модуль `testing` состоит из трех сабмодулей:

- `testing/engine` (артефакт `test-engine`) - сам движок: раннер, аннотации, модули и шаги;
- `testing/data` (артефакт `test-data`) - модельные данные для тестов: константы `me.moonways.bridgenet.test.data.TestConst`,
  <br>примерные сущности и утилиты ассертов (`me.moonways.bridgenet.test.data.junit.assertion.DataAssert`,
  <br>`me.moonways.bridgenet.test.data.junit.assertion.ServicesAssert`);
- `testing/units` (артефакт `test-units`) - сами интеграционные юниты, исполняемые движком.

---

## BUILD

Модуль `testing` подключается как maven-модуль в корневом `pom.xml` проекта
<br>(группа `me.moonways.bridgenet`) и имеет упаковку `pom`. Версия артефактов
<br>его сабмодулей задается property корневого `pom.xml`:

```xml

<testing.version>2.0</testing.version>
```

Общие зависимости объявлены в `testing/pom.xml`: помимо `junit` версии `4.13.2`,
<br>это внутренние модули системы - `bootstrap`, `api`, `mtp`, `jdbc-provider`, `rmi`,
<br>`model`, `rest-server`, `rest-client` и `client-api-root`.
<br>Внутри самого модуля `test-engine` зависит от `test-data`,
<br>а `test-units` - от `test-engine`.

В директорию сборки '.build' артефакты тестирования не попадают - в скрипте
<br>`.scripts/project_build.sh` модуль `testing` собирается последним в очереди модулей
<br>командой `mvn clean install -Dmaven.test.skip` и устанавливается только
<br>в локальный maven-репозиторий.
<br>
<br>Запуск самих интеграционных юнитов производится отдельной командой
<br>скрипта `./bridgenet` - `./bridgenet test` (или `./bridgenet -t`), которая через
<br>`.scripts/project_testing.sh` выполняет `mvn test` в директории `testing/units`.

---

## API

Основой движка является кастомный JUnit-раннер
<br>`me.moonways.bridgenet.test.engine.ModernTestEngineRunner`, расширяющий стандартный
<br>`BlockJUnit4ClassRunner`. Тестовый класс подключает его аннотацией `@RunWith`,
<br>после чего в поля класса можно инжектить бины системы через аннотацию
<br>`me.moonways.bridgenet.api.inject.Inject`:

```java

@RunWith(ModernTestEngineRunner.class)
@TestModules(DatabasesModule.class)
public class EntityRepositoryInsertEntityTest {

    @Inject
    private EntityRepositoryFactory entityRepositoryFactory;
}
```

Аннотация `@TestModules` (пакет `me.moonways.bridgenet.test.engine.persistance`)
<br>объявляет, какие модули системы движок должен поднять перед выполнением юнита.
<br>Готовые реализации лежат в пакете `me.moonways.bridgenet.test.engine.component.module.impl`:
<br>`AllModules`, `AutorunnersModule`, `ClientsModule`, `CommandsModule`, `DatabasesModule`,
<br>`EventsModule`, `MtpModule`, `RestModule`, `RmiServicesModule`, `SchedulersModule`.

Для тестирования удаленных сервисов мы можем использовать модуль `RmiServicesModule`
<br>и инжектить модельный интерфейс нужного сервиса напрямую:

```java

@RunWith(ModernTestEngineRunner.class)
@TestModules(RmiServicesModule.class)
public class MojangServiceEndpointTest {

    @Inject
    private MojangServiceModel subj;

    @Test
    public void test_checkPiratesNicknames() throws RemoteException {
        assertFalse(subj.isPirateNick(TestConst.Mojang.LICENSED_NICK));
        assertTrue(subj.isPirateNick(TestConst.Mojang.PIRATE_NICK));
    }
}
```

Для подготовки состояния системы перед запуском юнита мы можем использовать
<br>шаги - реализации интерфейса `me.moonways.bridgenet.test.engine.component.step.Step`,
<br>подключаемые аннотацией `@BeforeSteps`. Готовые шаги лежат в пакете
<br>`me.moonways.bridgenet.test.engine.component.step.impl` - например, `JoinPlayerStep`
<br>(эмулирует подключение игрока) и `AddGameStep` (регистрирует тестовую игру):

```java

@RunWith(ModernTestEngineRunner.class)
@TestModules({RmiServicesModule.class, ClientsModule.class})
@BeforeSteps(JoinPlayerStep.class)
public class PlayersServiceEndpointTest {

    @Inject
    private PlayersServiceModel serviceModel;

    @Test
    @TestOrdered(2)
    public void test_onlinePlayer() throws RemoteException {
        PlayerStore store = serviceModel.store();
        Optional<Player> player = store.get(TestConst.Player.NICKNAME);

        assertTrue(player.isPresent());
    }
}
```

Для управления жизненным циклом и порядком выполнения юнита движок
<br>предоставляет набор аннотаций из пакета `me.moonways.bridgenet.test.engine.persistance`:

- `@BeforeAll` / `@AfterAll` - методы, вызываемые до и после выполнения всех тестов юнита;
- `@TestOrdered` - порядковый номер тестового метода (по умолчанию `0`);
- `@TestSleeping` - пауза в миллисекундах до тестового метода
  <br>(либо после него при `acceptType = ExternalAcceptationType.POST_UNIT`);
- `@TestExternal` - поле с типом другого тестового класса: движок выполнит его
  <br>как внешний юнит и положит его инстанс в это поле; момент запуска регулируется
  <br>параметром `acceptType` (`BEFORE_UNIT`, `POST_UNIT`, `PARALLEL`).

```java

@RunWith(ModernTestEngineRunner.class)
public class ModernTestEngineTest {

    @Inject
    private DatabaseProvider databaseProvider;

    @TestExternal
    private EntityRepositoryInsertEntityTest insertEntityTest;

    @BeforeAll
    public void setUp() {
        log.debug("Setting up!");
    }

    @Test
    @TestOrdered(2)
    @TestSleeping(3000)
    public void test_sleepingTimeout() {
        log.debug("Executing test function with 3000ms timeout!");
    }
}
```

Собственные модули и шаги создаются наследованием от
<br>`me.moonways.bridgenet.test.engine.component.module.ModuleAdapter` и
<br>`me.moonways.bridgenet.test.engine.component.step.StepAdapter`: конфигурация
<br>описывается билдерами `ModuleConfig` (сканируемые пакеты `packagesToScanning`,
<br>зависимости от других модулей `dependencies`) и `StepConfig` (`beforeSteps`,
<br>`afterSteps`, `modulesDependencies`, `beansDependencies`).

Внутри раннер исполняет юнит как конвейер узлов
<br>`me.moonways.bridgenet.test.engine.flow.TestFlowNode` (пакет
<br>`me.moonways.bridgenet.test.engine.flow.nodes`), порядок которых задает
<br>`me.moonways.bridgenet.test.engine.flow.ParentFlowProcessor`: бутстрап системы,
<br>применение модулей и шагов, подготовка и запуск юнита, выполнение внешних
<br>юнитов и завершение. Общее состояние конвейера хранится в
<br>`me.moonways.bridgenet.test.engine.flow.TestFlowContext` - через типизированные
<br>ключи `BOOTSTRAP`, `BEANS`, `LOADED_MODULES` и `BEFORE_STEPS` из него можно
<br>получить, например, инстанс `BeansService` методом `getInstance(KeyRegistry)`.

---
