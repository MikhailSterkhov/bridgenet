# Bridgenet / API / Commands

Commands - Вложенный под-API модуля `api` системы Bridgenet (пакет `me.moonways.bridgenet.api.command`),
<br>реализующий декларативную систему пользовательских команд на основе аннотаций:
<br>объявление команд и подкоманд, автоматическая регистрация, разбор аргументов,
<br>проверка прав и предварительная верификация перед исполнением.

---

## API

Команда объявляется как обычный класс, размеченный аннотациями
<br>из пакета `me.moonways.bridgenet.api.command.annotation`:

- `@Command` - помечает класс как команду и задает ее имя;
- `@Alias` - дополнительный псевдоним команды или подкоманды (повторяемая аннотация);
- `@Permission` - право, необходимое для исполнения команды (на классе) или подкоманды (на методе);
- `@MentorExecutor` - метод-обработчик по умолчанию: вызывается, когда команда
  <br>исполнена без аргументов или подкоманда не найдена;
- `@ProducerExecutor` - метод-обработчик подкоманды, имя которой задается значением аннотации
  <br>и сверяется с первым аргументом команды;
- `@ProducerDescription` и `@ProducerUsageDescription` - описание и синтаксис подкоманды
  <br>для автоматически собираемого help-сообщения;
- `@MatcherExecutor` - метод-предикат, вызываемый перед исполнением: если он вернул `false`,
  <br>исполнение команды отменяется;
- `@CommandParameter` - подключает к команде реализацию интерфейса
  <br>`me.moonways.bridgenet.api.command.option.CommandParameterMatcher` - предварительную
  <br>верификацию сессии (например, `CommandParameterOnlyConsoleUse` ограничивает
  <br>исполнение команды только консолью).

Основные классы самого API:

- `CommandRegistry` - реестр команд: регистрация объектов команд и поиск обработчиков по имени/алиасу;
- `CommandExecutor` - исполнение команды по строке ввода от имени конкретного отправителя;
- `CommandSession` - сессия исполнения: отправитель, дескриптор команды, аргументы, печать help-сообщения;
- `CommandArguments` - обертка над аргументами команды с доступом через `Optional`;
- `EntityCommandSender` (пакет `me.moonways.bridgenet.api.command.sender`) - интерфейс отправителя команды:
  <br>имя, отправка сообщений, проверка прав. Готовая реализация для консоли - `ConsoleCommandSender`.

Каждый метод-обработчик (`@MentorExecutor`, `@ProducerExecutor`, `@MatcherExecutor`)
<br>принимает единственный параметр - `CommandSession`.

---

## USAGE

Приведем реальный пример объявления команды `/server` (с алиасом `/servers`)
<br>из эндпоинта `servers` (`me.moonways.bridgenet.endpoint.servers.command.ServersInfoCommand`):

```java

@Alias("servers")
@Command("server")
@CommandParameter(CommandParameterOnlyConsoleUse.class)
public class ServersInfoCommand {

    @Inject
    private ServersServiceModel servers;

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        EntityCommandSender sender = session.getSender();

        sender.sendMessage("Список доступных команд:");
        session.printDefaultMessage("§e/server {0} §7- {1}");
    }

    @Alias("get")
    @ProducerExecutor("info")
    @ProducerUsageDescription("info <server-name>")
    @ProducerDescription("Get a server information by name")
    public void info(CommandSession session) throws RemoteException {
        Optional<String> serverName = session.arguments().get(0);

        if (!serverName.isPresent()) {
            session.getSender().sendMessage(ChatColor.RED + "Требуется дополнительно указать наименование нужного сервера");
            return;
        }

        // ...
    }
}
```

В объект команды при регистрации автоматически инжектятся зависимости,
<br>поэтому внутри класса команды доступна аннотация `@Inject` (в примере выше -
<br>сервис `ServersServiceModel`).
<br>
<br>В help-сообщении, которое печатает `session.printDefaultMessage(...)`, плейсхолдер
<br>`{0}` заменяется на синтаксис подкоманды из `@ProducerUsageDescription`,
<br>а `{1}` - на ее описание из `@ProducerDescription`.

**Регистрация** команд происходит автоматически: аннотация `@Command` помечена
<br>процессором `@UseTypeAnnotationProcessor`, поэтому все классы команд обнаруживаются
<br>сканированием при старте системы, после чего `CommandExecutor` регистрирует их
<br>в реестре. Для ручной регистрации необходимо проинжектить реестр команд:

```java

@Inject
private CommandRegistry commandRegistry;
```

```java
commandRegistry.registerCommand(new ServersInfoCommand());
```

Для **исполнения** команды от имени консоли мы можем использовать
<br>следующий функционал (аналогично команды исполняет
<br>консоль `me.moonways.bridgenet.bootstrap.hook.type.console.BridgenetConsole`):

```java

@Inject
private CommandExecutor commandExecutor;
@Inject
private ConsoleCommandSender consoleSender;
```

```java
try {
    commandExecutor.execute(consoleSender, "server list");
} catch (CommandExecutionException exception) {
    // command by that label is not found.
}
```

Первое слово введенной строки - имя или алиас команды, второе - имя подкоманды
<br>(`@ProducerExecutor`), остальные слова попадают в `session.arguments()`.
<br>От имени игрока команды исполняются точно так же - собственной реализацией
<br>`EntityCommandSender` (см. `me.moonways.endpoint.players.listener.InboundPlayerCommandListener`
<br>в эндпоинте `players`).

Для чтения **аргументов** внутри обработчика мы можем использовать
<br>следующий функционал класса `CommandArguments`:

```java
CommandArguments arguments = session.arguments();

if (arguments.has(1)) {
    Optional<String> first = arguments.first();
    Optional<String> last = arguments.last();
}
```

а также конвертацию аргумента с обработкой ошибок через маппер-функцию
<br>(реальный пример из `me.moonways.bridgenet.test.data.ExampleCommand`):

```java
UUID playerUuid = arguments.first(playersServiceModel.store()::idByName).orElse(null);
```

Проверить работу собственной команды можно юнит-тестом на тестовом движке -
<br>готовый пример: `me.moonways.bridgenet.test.api.CommandsApiExecutionTest`
<br>(модуль `testing/units`, подключается `CommandsModule` тестового движка).
