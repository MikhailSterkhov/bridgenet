# BridgeNet / Services / Socials

Суть данного сервиса - обеспечивать возможность привязки аккаунтов из
<br>социальных сетей пользователям сети при помощи подтверждения аккаунта через
<br>бота или другие сервисы.

---

# MODEL USAGE

Для начала нам необходимо проинициализировать модель нашего сервиса
<br>при помощи `Injector API`:

```java
@Inject
private SocialsServiceModel model;
```

---

## `SocialsServiceModel.findLinkedProfile(UUID, Social)`

**Описание:**
<br>
<br>Найти привязанный профиль игрока в определенной
<br>указанной соц. сети.
<br>В случае, если указанная соц. сеть не привязана игроком,
<br>функция вернет пустой java.lang.Optional.
<br>

**Применение:**
```java
UUID playerId = player.getId();
Social social = Social.TELEGRAM;

Optional<SocialProfile> profile = model.findLinkedProfile(playerId, social);
```

---

## `SocialsServiceModel.findSocialsByInput(String)`

**Описание:**
<br>
<br>Воспроизвести автоматический поиск социальной сети,
<br>для которой были адресованы входящие параметры игроком
<br>для ее привязки.
<br>В случае если данные были введены некорректно или такой
<br>социальной сети просто нет в базе данных, то функция
<br>вернет пустой java.lang.Optional.
<br>

**Применение:**
```java
String playerInput = "@moonways";

// Result example: [TELEGRAM, VKONTAKTE]
Collection<Social> acceptedSocials = findSocialsByInput(playerInput);
```
```java
String playerInput = "http://vk.com/moonways";

// Result example: [VKONTAKTE]
Collection<Social> acceptedSocials = findSocialsByInput(playerInput);
```

--- 

## `SocialsServiceModel.tryLink(UUID, Social, String)`

**Описание:**
<br>
<br>Воспроизвести попытку привязки социальной сети
<br>к игроку с указанием входящих параметров для привязки
<br>от самого игрока.
<br>

**Применение:**

```java
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TelegramAccountLink {
    
    @Inject
    private SocialsServiceModel socials;

    public void link(UUID playerId, String input) {
        socials.tryLink(playerId, Social.TELEGRAM, input)
                .thenAccept((result) -> postLink(playerId, result));
    }

    private void postLink(UUID playerId, SocialBindingResult result) {
        switch (result) {
            case SUCCESS: {
                log.info("Пользователь {} подтвердил привязку своего аккаунта", playerId);
                log.info("Аккаунт успешно привязан и сохранен в базу данных!");
                break;
            }
            case FAILURE__NOT_BELONG: {
                log.warn("Указанный аккаунт уже принадлежит другому человеку");
                break;
            }
            default: {
                log.warn("Возникла другая ошибка при попытке привязать аккаунт: {}", result);
                break;
            }
        }
    }
}
```

---

## `SocialsServiceModel.tryUnlink(UUID, Social, String)`

**Описание:**
<br>
<br>Воспроизвести попытку отвязки социальной сети
<br>от игрока.
<br>В случае если социальная сеть не была привязана
<br>к указанному игроку, функция вернет ошибку NOT_LINKED.
<br>

**Применение:**

```java
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TelegramAccountUnlink {
    
    @Inject
    private SocialsServiceModel socials;

    public void unlink(UUID playerId) {
        socials.tryUnlink(playerId, Social.TELEGRAM)
                .thenAccept((result) -> postUnlink(playerId, result));
    }

    private void postUnlink(UUID playerId, SocialBindingResult result) {
        switch (result) {
            case SUCCESS: {
                log.info("Аккаунт успешно отвязан и удален из базы данных!");
                break;
            }
            case FAILURE__NOT_LINKED: {
                log.warn("Указанный аккаунт не привязан к данному пользователю");
                break;
            }
            default: {
                log.warn("Возникла другая ошибка при попытке отвязать аккаунт: {}", result);
                break;
            }
        }
    }
}
```