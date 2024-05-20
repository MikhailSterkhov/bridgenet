package me.moonways.bridgenet.api.command.process.verification.impl.cooldown.dao;

import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.CooldownInfo;
import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.CooldownInfoImpl;
import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.ExpirationCooldownInfoImpl;
import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.sql.CooldownRepository;
import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.CooldownContainer;
import me.moonways.bridgenet.api.inject.Inject;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CooldownDaoImpl implements CooldownDao {

    @Inject
    private CooldownContainer container;
    @Inject
    private CooldownRepository repository;

    @Override
    public CooldownInfo get(@NotNull String commandName, @NotNull String entityName) {
        return null;
    }

    @Override
    public Set<CooldownInfo> get(@NotNull String commandName) {
        return null;
    }

    @Override
    public void remove(@NotNull String commandName, @NotNull String entityName) {
        container.remove(commandName, entityName);
    }

    @Override
    public void set(@NotNull String commandName, @NotNull String entityName, long duration, @NotNull TimeUnit time) {
        ExpirationCooldownInfoImpl expirationCooldownInfo = new ExpirationCooldownInfoImpl(entityName, duration, time);

        container.add(commandName, expirationCooldownInfo);
    }

    @Override
    public boolean isExpired(@NotNull String commandName, @NotNull String entityName) {
        CooldownInfoImpl cooldownInfo = container.get(commandName, entityName);

        long duration = cooldownInfo.getDuration();
        TimeUnit time = cooldownInfo.getTime();

        return System.currentTimeMillis() + time.toMillis(duration) < System.currentTimeMillis();
    }

    @Override
    public void removeAll() {
        container.removeAll();
    }

    @Override
    public void removeAll(@NotNull String commandName) {
        container.remove(commandName);
    }
}
