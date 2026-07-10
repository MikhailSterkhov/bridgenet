package me.moonways.endpoint.language;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.assembly.ResourcesAssembly;
import me.moonways.bridgenet.assembly.ini.IniConfig;
import me.moonways.bridgenet.assembly.ini.type.IniProperty;
import me.moonways.bridgenet.model.event.PlayerLanguageUpdateEvent;
import me.moonways.bridgenet.model.service.language.*;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Log4j2
public final class LanguageServiceEndpoint extends EndpointServiceObject implements LanguageServiceModel {
    private static final Language DEFAULT_LANG = LanguageTypes.ENGLISH;

    private static final String LANGUAGES_CONFIGS_FORMAT = "lang/%s.ini";
    public static final String PLACEHOLDER_MSG_KEY = "${msg.key}";

    private final Map<UUID, RegisteredLanguage> registeredLanguagesMap
            = new TreeMap<>();

    private final PlayerLanguagesRepository playerLanguagesRepository
            = new PlayerLanguagesRepository();

    private final Cache<UUID, Language> playersLanguagesCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(5, TimeUnit.HOURS)
                    .build();

    @Inject
    private LanguageDisplayNameMapper languageDisplayNameMapper;

    @Inject
    private EventService eventService;
    @Inject
    private ResourcesAssembly assembly;
    @Inject
    private PlayersServiceModel playersServiceModel;

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.inject(playerLanguagesRepository);
        doRegisterDefaultTypes();
    }

    private void doRegisterDefaultTypes() {
        for (Language language : LanguageTypes.TYPES) {

            String resourceName = String.format(LANGUAGES_CONFIGS_FORMAT, language.getName());
            IniConfig iniConfig = assembly.readIniConfig(resourceName);

            if (iniConfig == null) {
                log.warn("§4Language resource /{} is undefined", resourceName);
                return;
            }

            RegisteredLanguage registeredLanguage =
                    RegisteredLanguage.builder()
                            .language(language)
                            .messagesConfig(iniConfig)
                            .build();

            registeredLanguagesMap.put(language.getId(), registeredLanguage);

            log.debug("Registered language type §6{} §7(short={}) §rfrom resource §6/{}",
                    languageDisplayNameMapper.getDisplayName(language),
                    language.getName().toUpperCase(), resourceName);
        }
    }

    private Optional<RegisteredLanguage> findLanguage(Language language) {
        return Optional.ofNullable(registeredLanguagesMap.get(language.getId()));
    }

    private Optional<String> findTranslatedMessage(Language language, Message message) {
        RegisteredLanguage registeredLanguage = findLanguage(language)
                .orElseGet(() -> findLanguage(DEFAULT_LANG).get());

        IniConfig messagesConfig = registeredLanguage.getMessagesConfig();
        String messageKey = message.getKey();

        if (messageKey == null) {
            return findTranslatedMessage(language, MessageTypes.UNKNOWN_MESSAGE_TYPE)
                    .map(s -> s.replace(PLACEHOLDER_MSG_KEY, "null"));
        }
        if (messageKey.isEmpty()) {
            return Optional.of(messageKey);
        }

        String[] split = messageKey.split("\\.");
        return Optional.of(
                messagesConfig.readProperty(split[0], split[1])
                        .map(IniProperty::getAsString)
                        .orElseGet(() -> findTranslatedMessage(language, MessageTypes.UNKNOWN_MESSAGE_TYPE)
                                .get()
                                .replace(PLACEHOLDER_MSG_KEY, messageKey)));
    }

    @Override
    public Optional<Language> getLang(UUID id) {
        return Optional.ofNullable(registeredLanguagesMap.get(id))
                .map(RegisteredLanguage::getLanguage);
    }

    @Override
    public Optional<Language> getLang(String name) {
        return findLanguage(Language.fromName(name))
                .map(RegisteredLanguage::getLanguage);
    }

    @Override
    public Optional<Language> getLang(Locale locale) {
        return findLanguage(Language.fromLocale(locale))
                .map(RegisteredLanguage::getLanguage);
    }

    @Override
    public Component message(Language language, String key) {
        return Component.text(messageText(language, key));
    }

    @Override
    public Component message(Language language, Message message) {
        return Component.text(messageText(language, message));
    }

    @Override
    public String messageText(Language language, String key) {
        return findTranslatedMessage(language, Message.keyed(key)).orElse(null);
    }

    @Override
    public String messageText(Language language, Message message) {
        return findTranslatedMessage(language, message).orElse(null);
    }

    @Override
    public Language getDefault() {
        return DEFAULT_LANG;
    }

    @Override
    public Language getPlayerLang(UUID playerId) {
        playersLanguagesCache.cleanUp();

        Language cached = playersLanguagesCache.getIfPresent(playerId);
        if (cached == null) {
            cached = playerLanguagesRepository.get(playerId)
                    .flatMap(entityLanguage -> getLang(entityLanguage.getLangId()))
                    .orElse(DEFAULT_LANG);

            playersLanguagesCache.put(playerId, cached);
        }

        return cached;
    }

    @Override
    public Language getPlayerLang(String playerName) {
        return getPlayerLang(playersServiceModel.store().idByName(playerName));
    }

    @Override
    public Optional<PlayerLanguageUpdateEvent> setPlayerLang(UUID playerId, Language language) {
        Language previous = getPlayerLang(playerId);

        if (previous.equals(language)) {
            return Optional.empty();
        }

        playerLanguagesRepository.update(new EntityLanguage(playerId, language.getId()));
        playersLanguagesCache.put(playerId, language);

        PlayerLanguageUpdateEvent event =
                PlayerLanguageUpdateEvent.builder()
                        .playerId(playerId)
                        .language(language)
                        .build();

        eventService.fireEvent(event);
        return Optional.of(event);
    }

    @Override
    public Optional<PlayerLanguageUpdateEvent> setPlayerLang(String playerName, Language language) {
        return setPlayerLang(playersServiceModel.store().idByName(playerName), language);
    }
}
