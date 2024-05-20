package me.moonways.bridgenet.api.command.uses.entity;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Autobind;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Log4j2
@Autobind
public final class ConsoleCommandSender implements EntityCommandSender {

    private static final String CONSOLE_NAME = "Bridgenet_Console";

    private final UUID uuid = UUID.fromString(CONSOLE_NAME);

    @Override
    public void sendMessage(String message) {
        log.info(message);
    }

    @Override
    public void sendMessage(String message, @Nullable Object... replacements) {
        sendMessage(String.format(message, replacements));
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public EntitySenderType getType() {
        return EntitySenderType.CONSOLE;
    }

    @Override
    public String getName() {
        return CONSOLE_NAME;
    }

    @Override
    public boolean isInstanceOf(EntitySenderType entityType) {
        return entityType.equals(EntitySenderType.CONSOLE);
    }
}
