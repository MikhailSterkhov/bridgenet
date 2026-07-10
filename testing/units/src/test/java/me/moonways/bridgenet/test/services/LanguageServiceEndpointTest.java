package me.moonways.bridgenet.test.services;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.event.subscribe.EventSubscribeBuilder;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.event.PlayerLanguageUpdateEvent;
import me.moonways.bridgenet.model.service.language.*;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.data.junit.assertion.ServicesAssert;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import net.kyori.adventure.text.Component;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.*;

@Log4j2
@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class LanguageServiceEndpointTest {

    @Inject
    private EventService eventService;

    @Inject
    private LanguageServiceModel serviceModel;

    @Inject
    private LanguageDisplayNameMapper languageDisplayNameMapper;

    @Test
    @TestOrdered(1)
    public void test_languageDisplayName() {
        assertNotNull(languageDisplayNameMapper.getDisplayName(LanguageTypes.JAPAN));
        assertNotNull(languageDisplayNameMapper.getDisplayName(LanguageTypes.ENGLISH));
        assertNull(languageDisplayNameMapper.getDisplayName(TestConst.Languages.ANY_UNKNOWN_LANG));
    }

    @Test
    @TestOrdered(2)
    public void test_playerLanguageGet() {
        Language language = serviceModel.getPlayerLang(TestConst.Player.ID);

        log.debug(language);

        ServicesAssert.assertLanguage(language, Language.fromLocale(Locale.ENGLISH));
    }

    @Test
    @TestOrdered(3)
    public void test_playerLanguageSet() {
        eventService.subscribe(
                EventSubscribeBuilder.newBuilder(PlayerLanguageUpdateEvent.class)
                        .follow(log::debug)
                        .build());

        Optional<PlayerLanguageUpdateEvent> eventOptional = serviceModel.setPlayerLang(TestConst.Player.ID, LanguageTypes.JAPAN);
        Language currentPlayerLang = serviceModel.getPlayerLang(TestConst.Player.ID);

        log.debug(serviceModel.messageText(currentPlayerLang, MessageTypes.GREETING_ON_JOIN));

        assertTrue(eventOptional.isPresent());
        assertEquals(currentPlayerLang, LanguageTypes.JAPAN);
    }

    @Test
    @TestOrdered(4)
    public void test_defaultLanguage() {
        Language language = serviceModel.getDefault();

        log.debug(language);

        ServicesAssert.assertLanguage(language, Language.fromLocale(Locale.ENGLISH));
    }

    @Test
    @TestOrdered(5)
    public void test_messageGet() {
        Language language = serviceModel.getPlayerLang(TestConst.Player.ID);

        Component message = serviceModel.message(language, MessageTypes.GREETING_ON_JOIN);

        log.debug(message);
        assertNotNull(message);
    }

    @Test
    @TestOrdered(6)
    public void test_languageCreate() {
        Language language = TestConst.Languages.ANY_UNKNOWN_LANG;

        // automatically applying a default language - ENGLISH
        String messageText = serviceModel.messageText(language, MessageTypes.GREETING_ON_JOIN);
        String defaultMessageText = serviceModel.messageText(serviceModel.getDefault(), MessageTypes.GREETING_ON_JOIN);

        log.debug(messageText);

        assertNotNull(messageText);
        assertEquals(messageText, defaultMessageText);
    }

    @Test
    @TestOrdered(7)
    public void test_unknownMessage() {
        Language language = LanguageTypes.GERMANY;

        String messageText = serviceModel.messageText(language, TestConst.Languages.ANY_UNKNOWN_MESSAGE);

        String unknownMessageTypeText = serviceModel.messageText(language, MessageTypes.UNKNOWN_MESSAGE_TYPE)
                .replace("${msg.key}",
                        TestConst.Languages.ANY_UNKNOWN_MESSAGE.getKey());

        log.debug(messageText);

        assertNotNull(messageText);
        assertEquals(messageText, unknownMessageTypeText);
    }
}
