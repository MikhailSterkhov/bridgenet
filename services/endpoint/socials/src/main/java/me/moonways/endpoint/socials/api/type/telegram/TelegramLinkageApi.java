package me.moonways.endpoint.socials.api.type.telegram;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.model.socials.Social;
import me.moonways.endpoint.socials.api.AbstractSocialNetworkLinkageApi;
import me.moonways.endpoint.socials.api.SocialNetworkAccount;
import me.moonways.endpoint.socials.api.SocialNetworkBot;
import me.moonways.endpoint.socials.api.data.SocialNetworkDataDescriptor;
import me.moonways.endpoint.socials.api.util.UsernameTagUtil;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.UUID;

@Log4j2
public class TelegramLinkageApi extends AbstractSocialNetworkLinkageApi {

    private static final String TELEGRAM_SITE_ADDRESS = "t.me";

    private final TelegramAccountsDbRepository accountsRepository = new TelegramAccountsDbRepository();

    public TelegramLinkageApi() {
        super(Social.TELEGRAM);
    }

    @Override
    public boolean verify(String input) {
        return UsernameTagUtil.verify(TELEGRAM_SITE_ADDRESS, input);
    }

    @Override
    protected String simplify(String playerInput) {
        return UsernameTagUtil.simplify(TELEGRAM_SITE_ADDRESS, playerInput);
    }

    @Override
    protected SocialNetworkBot start(SocialNetworkDataDescriptor descriptor) {
        TelegramBotWrapper wrapper = new TelegramBotWrapper(descriptor, accountsRepository);
        wrapper.init();

        return wrapper;
    }

    @Override
    protected SocialNetworkAccount findAccount(UUID playerId, String username) {
        return accountsRepository.findByUsername(username).orElse(null);
    }
}
