# Bridgenet / JDBC

JDBC - Вложенный модуль системы Bridgenet, реализующий движок базы данных:
<br>управление соединениями, транзакции, наблюдение за событиями базы данных
<br>и объектно-реляционную работу с сущностями через репозитории.

---

## BUILD

Модуль подключается как maven-модуль `jdbc` в корневом `pom.xml` проекта
<br>и разделен на три вложенных сабмодуля:

- `jdbc/core` (артефакт `jdbc-core`) - ядро движка: соединения (`DatabaseConnection`),
  <br>транзакции, шаблонная генерация SQL-запросов (`DatabaseComposer`) и наблюдатели
  <br>событий базы данных;
- `jdbc/entities` (артефакт `jdbc-entities`) - объектно-реляционный слой: репозитории
  <br>сущностей, дескрипторы, критерии поиска и адаптеры типов;
- `jdbc/provider` (артефакт `jdbc-provider`) - провайдер, объединяющий оба сабмодуля
  <br>и отвечающий за централизованное открытие и закрытие соединений.

Версия всех артефактов модуля определяется свойством `jdbc.version`
<br>в properties корневого `pom.xml`:

```xml

<jdbc.version>1.2</jdbc.version>
```

Из других модулей проекта `jdbc` зависит только от модуля `api`
<br>(артефакт `me.moonways.bridgenet:api`), а драйвер базы данных по умолчанию
<br>(`com.h2database:h2`) наследуется из корневого `pom.xml`.

При полной сборке проекта (`./bridgenet build` или `./bridgenet jar`) скрипт
<br>`.scripts/project_build.sh` устанавливает модуль `jdbc` в общей очереди модулей
<br>(после `api`, перед `services` и `bootstrap`). Отдельного jar-файла в директории
<br>сборки '.build' модуль не имеет - его классы попадают туда в составе fat-jar
<br>`bridgenet-server.jar`, так как модуль `bootstrap` зависит от `jdbc-provider`
<br>и собирается при помощи `maven-shade-plugin`. Модули сервисов (`services`)
<br>используют `jdbc-provider` со scope `provided` - в runtime классы модуля
<br>предоставляются ядром сервера.

При старте сервера hook `me.moonways.bridgenet.bootstrap.hook.type.OpenJdbcConnectionHook`
<br>читает конфигурацию подключения из ресурса `jdbc.json` (модуль `assembly`,
<br>константа `ResourcesTypes.JDBC_JSON`):

```json
{
  "url": "jdbc:h2:mem:default;DB_CLOSE_ON_EXIT=FALSE",
  "username": "username",
  "password": "password"
}
```

после чего открывает соединение через `DatabaseProvider` и регистрирует в системе
<br>Dependency Injection бины `DatabaseComposer`, `DatabaseConnection`
<br>и `EntityRepositoryFactory`.

---

## API

Основной точкой входа для работы с базой данных является фабрика репозиториев
<br>`me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory`:

```java

@Inject
private EntityRepositoryFactory repositoryFactory;
```

Сущности описываются аннотациями из пакета `me.moonways.bridgenet.jdbc.entity.persistence`:
<br>класс помечается аннотацией `@Entity` с именем таблицы, а колонки - аннотациями
<br>`@EntityColumn` / `@EntityId` / `@EntityExternal` над геттерами (при использовании
<br>Lombok - через `onMethod_`). Реальный пример из сервиса друзей:

```java
@Builder
@ToString
@Entity(name = "friends")
public class EntityFriend {

    @Getter(onMethod_ = @EntityColumn(id = "player_id", order = 1))
    private UUID playerID;

    @Getter(onMethod_ = @EntityColumn(id = "friend_id", order = 2))
    private UUID friendID;
}
```

Для получения **репозитория сущности** мы можем использовать следующий функционал
<br>(необходимые таблицы при этом подготавливаются автоматически):

```java
EntityRepository<EntityFriend> repository = repositoryFactory.fromEntityType(EntityFriend.class);
```

Операции вставки и поиска возвращают асинхронные обертки `Mono<T>` (один результат)
<br>и `Multiple<T>` (множество результатов), построенные поверх `CompletableFuture`;
<br>операции обновления и удаления выполняются без обертки (`void`).
<br>Приведем примеры.

Для **вставки** новой сущности мы можем использовать следующий функционал:

```java
Mono<EntityID> insertMono = repository.insert(EntityFriend.builder()
        .playerID(playerID)
        .friendID(friendID)
        .build());

insertMono.subscribe(entityID -> System.out.println("Inserted entity ID: " + entityID.getId()));
```

Для **поиска** сущностей по условиям используется `SearchCriteria`,
<br>создаваемый методом `beginCriteria()`. Колонку можно указывать как ссылкой
<br>на геттер, так и строковым именем:

```java
List<UUID> friendsList = repository.search(repository.beginCriteria()
                .andEquals(EntityFriend::getPlayerID, playerID))
        .mapEach(EntityFriend::getFriendID)
        .blockAll();
```

```java
Optional<EntityFriend> friendOptional = repository.searchFirst(repository.beginCriteria()
                .andEquals("friend_id", friendID))
        .blockOptional();
```

Для **обновления** и **удаления** сущностей мы можем использовать
<br>следующий функционал:

```java
repository.update(entity, id);
```

```java
repository.delete(repository.beginCriteria()
        .andEquals(EntityFriend::getPlayerID, playerID));
```

Для низкоуровневой работы с базой данных можно проинжектить активное
<br>соединение `me.moonways.bridgenet.jdbc.core.DatabaseConnection` напрямую:

```java

@Inject
private DatabaseConnection connection;
```

Для выполнения **нативных SQL-запросов** мы можем использовать
<br>следующий функционал:

```java
Result<ResponseStream> result = connection.call("SELECT * FROM friends;");
result.whenCompleted(response -> response.forEach(System.out::println));
```

Для выполнения операций в рамках одной **транзакции** мы можем
<br>использовать следующий функционал:

```java
connection.ofTransactional(() -> {
    repository.insert(first);
    repository.insert(second);
});
```

Для **наблюдения за событиями** базы данных (выполнение и падение запросов,
<br>открытие и откат транзакций и т.д.) используется адаптер
<br>`me.moonways.bridgenet.jdbc.core.observer.ObserverAdapter`:

```java
connection.addObserver(new ObserverAdapter() {

    @Override
    protected void observe(DbRequestCompletedEvent event) {
        // request completed.
    }

    @Override
    protected void observe(DbTransactionRollbackEvent event) {
        // transaction rollback.
    }
});
```

---
