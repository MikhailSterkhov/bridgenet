package me.moonways.endpoint.socials.api.type.telegram;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.decorator.persistence.Async;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.dao.EntityAccessCondition;
import me.moonways.bridgenet.jdbc.dao.EntityDao;
import me.moonways.bridgenet.jdbc.provider.DatabaseProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class TelegramAccountsDbRepository {

    @Inject
    private DatabaseProvider databaseProvider;
    @Inject
    private DatabaseConnection connection;

    private EntityDao<TelegramAccount> telegramAccountDao;

    @PostConstruct
    private void initDao() {
        telegramAccountDao = databaseProvider.createDao(TelegramAccount.class, connection);
    }

    public void insertOrUpdate(@NotNull TelegramAccount account) {
        telegramAccountDao.insertMono(account);
    }

    public Optional<TelegramAccount> findByChat(@NotNull Long chatID) {
        return telegramAccountDao.findMono(EntityAccessCondition.createMono("chat_id", chatID));
    }

    public Optional<TelegramAccount> findById(@NotNull Long userID) {
        return telegramAccountDao.findMono(EntityAccessCondition.createMono("user_id", userID));
    }

    public Optional<TelegramAccount> findByUsername(@NotNull String username) {
        return telegramAccountDao.findMono(EntityAccessCondition.createMono("username", username));
    }

    @Async
    public List<TelegramAccount> findAll() {
        return telegramAccountDao.findAll();
    }
}
