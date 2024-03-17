package me.moonways.endpoint.socials.api.type.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.endpoint.socials.api.SocialNetworkAccount;
import me.moonways.endpoint.socials.api.SocialNetworkBot;
import me.moonways.endpoint.socials.api.SocialNetworkLinkageResult;
import me.moonways.endpoint.socials.api.data.SocialNetworkDataDescriptor;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class TelegramBotWrapper extends TelegramLongPollingBot implements SocialNetworkBot {

    private final SocialNetworkDataDescriptor descriptor;
    private final TelegramAccountsDbRepository repository;

    private final Set<TelegramAccount> sessions = Collections.synchronizedSet(new HashSet<>());

    @Override
    public String getBotUsername() {
        return descriptor.getCredentials().getId();
    }

    @Override
    public String getBotToken() {
        return descriptor.getCredentials().getAccessToken();
    }

    void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);

        } catch (TelegramApiException exception) {
            log.error("", exception);
        }

        sessions.addAll(repository.findAll()); // todo - может оно и не надо? аккаунтов может быть дохуище
        sessions.forEach(telegramAccount ->
                dispatchSendMessage(telegramAccount, descriptor.getChat().getOnlineModeMessage()));

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                sessions.forEach(telegramAccount -> dispatchSendMessage(telegramAccount, descriptor.getChat().getOfflineModeMessage()))
        ));
    }

    @Override
    public void onUpdateReceived(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        TelegramAccount telegramAccount;

        if (callbackQuery != null) {
            telegramAccount = toTelegramAccount(callbackQuery.getMessage(), callbackQuery.getFrom());

            if (callbackQuery.getData().equals("link_accept")) {
                dispatchAccepted(telegramAccount);
            } else if (callbackQuery.getData().equals("link_cancel")) {
                dispatchSendMessage(telegramAccount, descriptor.getChat().getLinkageCancelMessage());
            }
        } else {

            // todo - убрать или сделать просто запись пользователя в бд для того чтобы он мог отправлять запросы из майна
            telegramAccount = toTelegramAccount(update.getMessage(), update.getMessage().getFrom());
            log.debug("(Telegram) [@{} -> @moonways_bot]: \"{}\"", telegramAccount.getUsername(), update.getMessage().getText().replace("\n", "\\n"));

            if (sessions.contains(telegramAccount)) {
                dispatchSendMessage(telegramAccount, descriptor.getChat().getUnknownCommandMessage());
            } else {
                dispatchSendMessage(telegramAccount, descriptor.getChat().getLinkageMessage(),
                        createInlineKeyboardMarkup());
            }
        }
    }

    private TelegramAccount toTelegramAccount(Message message, User user) {
        return TelegramAccount.builder()
                .chatID(message.getChatId())
                .username(user.getUserName())
                .firstName(user.getFirstName())
                .languageCode(user.getLanguageCode())
                .build();
    }

    public void dispatchSendMessage(TelegramAccount account, String message) {
        dispatchSendMessage(account, message, null);
    }

    public void dispatchSendMessage(TelegramAccount account, String message, ReplyKeyboard replyKeyboard) {
        message = message.replace("{username}", account.getUsername());

        SendMessage sendMessage = new SendMessage();

        sendMessage.setChatId(String.valueOf(account.getChatID()));
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(replyKeyboard);

        try {
            executeAsync(sendMessage);
            log.debug("(Telegram) [@moonways_bot -> @{}]: \"{}\"", account.getUsername(), message.replace("\n", "\\n"));
        } catch (TelegramApiException exception) {
            log.error("", exception);
        }
    }

    private void dispatchAccepted(TelegramAccount account) {
        if (sessions.contains(account)) {
            dispatchSendMessage(account, descriptor.getChat().getLinkageAlreadyAcceptedMessage());
            return;
        }

        log.debug("(Telegram) [@{} -> @moonways_bot]: LINKAGE ACCEPTED", account.getUsername());

        dispatchSendMessage(account, descriptor.getChat().getLinkageAcceptMessage());

        if (!sessions.contains(account)) {
            sessions.add(account);
            repository.insertOrUpdate(account);
        }
    }

    private InlineKeyboardMarkup createInlineKeyboardMarkup() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton linkAcceptButton = new InlineKeyboardButton();
        linkAcceptButton.setText(descriptor.getChat().getLinkageAcceptButtonText());
        linkAcceptButton.setCallbackData("link_accept");

        InlineKeyboardButton linkCancelButton = new InlineKeyboardButton();
        linkCancelButton.setText(descriptor.getChat().getLinkageCancelButtonText());
        linkCancelButton.setCallbackData("link_cancel");

        markup.setKeyboard(Collections.singletonList(Arrays.asList(linkAcceptButton, linkCancelButton)));
        return markup;
    }

    @Override
    public void requestAccountLink(SocialNetworkAccount account, Consumer<SocialNetworkLinkageResult> consumer) {
        dispatchSendMessage((TelegramAccount) account, descriptor.getChat().getLinkageMessage(),
                createInlineKeyboardMarkup());
    }

    @Override
    public void requestAccountUnlink(SocialNetworkAccount account, Consumer<SocialNetworkLinkageResult> consumer) {
        // todo
    }

    @Override
    public void dispatchChat(SocialNetworkAccount account, String message) {
        dispatchSendMessage((TelegramAccount) account, message);
    }
}
