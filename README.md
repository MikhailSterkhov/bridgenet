<div align="center">
  <img src=".assets/logo.png" alt="Логотип BridgeNet" width="500"/>
  <br>
  <img src="https://img.shields.io/badge/language-Java-gold?style=flat" />
  <img src="https://img.shields.io/badge/release-v1.3-gold?style=flat" />
  <img src="https://img.shields.io/badge/dev_branch-development-gold?style=flat" />
</div>

# Введение

BridgeNet — это мощная многопоточная протокольная система, разработанная для обеспечения надежного соединения и взаимодействия между внутренними серверами и игроками. Она включает в себя несколько API, которые позволяют выполнять динамическую коррекцию данных, манипулирование и маршрутизацию через заданные каналы и процессы.

# Обзор системы

## Что это такое?

BridgeNet предоставляет функциональность для создания и управления многопоточными соединениями между серверами и клиентами. Система спроектирована таким образом, чтобы обеспечить высокую производительность и масштабируемость, что делает её идеальным решением для крупных проектов, где требуется эффективное управление сетевыми взаимодействиями и обработка больших объемов данных в реальном времени.

## Основные функции

Многопоточность: Обеспечивает эффективное использование ресурсов процессора и улучшает производительность системы.
Модульность: Система разделена на модули, каждый из которых отвечает за выполнение определенных задач, что упрощает её поддержку и расширение.
API для разработчиков: Предоставляет множество API для реализации пользовательских команд, событий, задач и многого другого.
Модули и их API
Система BridgeNet состоит из нескольких модулей, каждый из которых выполняет определенные функции. Подробное описание каждого модуля и их API можно найти в документации по следующим ссылкам:

* [Bootstrap](/.docs/bootstrap.md)
* [Assembly](/.docs/assembly.md)
* [API Modules:](/.docs/api.md)
  * [API / Отложенные задачи (Delayed Runnables)](/.docs/api/autorun-api.md)
  * [API / Пользовательские команды (User Commands)](/.docs/api/commands-api.md)
  * [API / Подписка на события (Events Subscribing)](/.docs/api/events-api.md)
  * [API / Внедрение зависимостей (Dependency Injection)](/.docs/api/inject-api.md)
  * [API / Парсинг XML (JAXB)](/.docs/api/jaxb-api.md)
  * [API / Перехват методов (Method Intercepting)](/.docs/api/proxy-api.md)
  * [API / Планирование задач (Scheduling Tasks)](/.docs/api/scheduler-api.md)
* [Клиенты](/.docs/clients.md)
* [Тестовый движок](/.docs/test-engine.md)
* [Движок базы данных](/.docs/jdbc.md)
* [Профайлер](/.docs/profiler.md)
* [Основной протокол](/.docs/mtp.md)
* [Протокол RMI](/.docs/rmi.md)
* [REST API](/.docs/rest.md)
* [Сервисы и эндпоинты:](/.docs/services.md)
  * [СЕРВИС / Аутентификация (AUTH)](/.docs/services/auth-endpoint.md)
  * [СЕРВИС / Шина (BUS)](/.docs/services/bus-endpoint.md)
  * [СЕРВИС / Друзья (FRIENDS)](/.docs/services/friends-endpoint.md)
  * [СЕРВИС / Игровые сервера (GAMES)](/.docs/services/games-endpoint.md)
  * [СЕРВИС / Графический интерфейс (GUI)](/.docs/services/gui-endpoint.md)
  * [СЕРВИС / Гильдии (GUILDS)](/.docs/services/guilds-endpoint.md)
  * [СЕРВИС / Мультиязычность (LANGUAGE)](/.docs/services/language-endpoint.md)
  * [СЕРВИС / Mojang API (MOJANG)](/.docs/services/mojang-endpoint.md)
  * [СЕРВИС / Компании (PARTIES)](/.docs/services/parties-endpoint.md)
  * [СЕРВИС / Права доступа (PERMISSIONS)](/.docs/services/permissions-endpoint.md)
  * [СЕРВИС / Игроки (PLAYERS)](/.docs/services/players-endpoint.md)
  * [СЕРВИС / Жалобы (REPORTS)](/.docs/services/reports-endpoint.md)
  * [СЕРВИС / Сервера (SERVERS)](/.docs/services/servers-endpoint.md)
  * [СЕРВИС / Персонализация (SETTINGS)](/.docs/services/settings-endpoint.md)

# Руководство по использованию

Для работы с системой BridgeNet в корневой директории проекта находится скрипт под названием bridgenet, который необходимо запускать из терминала. Этот скрипт предоставляет список доступных команд и флагов, а также описание их процессов.

## Основные команды

```shell
$ ./bridgenet endpoints
```

Полная компиляция, конфигурация и последующая сборка всех сервисов и их эндпоинтов.
<br>Опционально принимает имя конкретного эндпоинта первым параметром.

```shell
$ ./bridgenet assemblyEndpoints
```

Конфигурация скомпилированных сервисов в сборке.

```shell
$ ./bridgenet jar
```

Последовательная Maven компиляция основных модулей проекта BridgeNet.

```shell
$ ./bridgenet build
```

Полная и последовательная компиляция всех модулей проекта BridgeNet, включая сервисы
<br>и их эндпоинты — объединяет команды `jar` и `endpoints`.

```shell
$ ./bridgenet test
```

Запуск интеграционных юнит-тестов проекта (модуль `testing/units`).

```shell
$ ./bridgenet help
```

Список всех доступных команд скрипта и их краткое описание.

---

## Требования и кроссплатформенность

Скрипт работает на **macOS, Linux и Windows (Git Bash)**. Требуется установленный Apache Maven и JDK **8–24**.

- **JDK подбирается автоматически**: если JDK по умолчанию не входит в поддерживаемый диапазон
  (например, JDK 25+ — на них ломается Lombok `val`/`var`), скрипт сам найдёт подходящий JDK в
  стандартных каталогах установки (в т.ч. `~/.jdks` от IntelliJ IDEA). Принудительно указать JDK:
  ```shell
  export BRIDGENET_JAVA_HOME=/path/to/jdk
  ```
- **Maven-настройки**: сборка идёт через нейтральный `.scripts/etc/maven-settings.xml`, чтобы
  глобальные зеркала/прокси из `~/.m2/settings.xml` не ломали резолв зависимостей проекта.
  Использовать свои настройки:
  ```shell
  export BRIDGENET_MAVEN_SETTINGS=/path/to/settings.xml
  ```
- **Оффлайн-зависимости**: каталог `.repo` — проектный Maven-репозиторий с артефактами CloudNet v3,
  которые больше не доступны в публичных репозиториях. Не удаляйте его.
- Если `sh bridgenet` запускается не из bash-совместимой оболочки (например, `dash` на Debian/Ubuntu),
  скрипт сам перезапустится через bash.

Возможные ошибки:
- `mvn: command not found`: установите Maven и добавьте его `bin` в `PATH`, либо добавьте в
  `.scripts/utils.sh` (до функции `run_mvn`) строчку:
  ```shell
  alias mvn="path/to/maven/bin/mvn"
  ```
- `permission denied: ./bridgenet` (macOS/Linux): выполните `chmod +x bridgenet` или запускайте
  как `sh bridgenet <команда>`.

---

## Сборка системы

После выполнения указанных выше скриптов и команд в локальном проекте должна появиться папка .build, содержащая все необходимые файлы для работы системы. Пример содержимого данной папки представлен ниже:

<img src=".assets/build_folder.png" alt="Содержимое папки сборки"/>

Эта папка представляет собой полноценную и готовую к использованию сборку системы BridgeNet.

# Запуск и тестирование

## Сборка и запуск в Docker

Требуется только Docker (Compose v2) — образ самодостаточный: multi-stage Dockerfile
собирает систему внутри контейнера тем же скриптом `bridgenet build` (JDK 21 + Maven 3.9),
а рантайм работает на Java 8 (Temurin JRE).

```shell
$ docker compose up -d --build
```

- Порты: `6791` — протокол MTP, `4590` — REST API (внутри контейнера бинды
  переведены на `0.0.0.0`, RMI остаётся на loopback — это внутрипроцессная связь).
- Логи: `docker logs -f bridgenet`; файлы логов — в named volume `bridgenet-logs`.
- Опции JVM: переменная `JAVA_OPTS` в `docker-compose.yml`.
- Healthcheck: TCP-проба MTP-порта, контейнер переходит в `healthy` после полного старта.

## Локальный запуск

Для запуска системы локально используется единственный класс, содержащий статический метод main(String[] args): me.moonways.bridgenet.bootstrap.AppStarter.

## Тестирование

Для тестирования отдельных систем и подсистем в проекте реализован модуль testing, который разделен на несколько частей:

* **Test-Data**: Сборка модельных компонентов и констант, помогающих в тестировании.
* **Test-Engine**: Кастомный фреймворк на основе JUnit, автоматизирующий процессы тестирования в системе BridgeNet.
* **Test-Units**: Юнит-тесты для системы.

---

<div align="center">
    © MoonWays BridgeNet - 2024
    <br>
    Никакие права не защищены :(
</div>