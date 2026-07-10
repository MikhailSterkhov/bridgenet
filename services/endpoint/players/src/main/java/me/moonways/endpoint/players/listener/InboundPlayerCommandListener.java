package me.moonways.endpoint.players.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.command.CommandExecutor;
import me.moonways.bridgenet.api.command.exception.CommandExecutionException;
import me.moonways.bridgenet.api.command.sender.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.util.thread.Threads;
import me.moonways.bridgenet.model.message.SendCommand;
import me.moonways.bridgenet.model.service.permissions.permission.Permission;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.mtp.message.InboundMessageContext;
import me.moonways.bridgenet.mtp.message.persistence.InboundMessageListener;
import me.moonways.bridgenet.mtp.message.persistence.SubscribeMessage;
import me.moonways.endpoint.players.PlayerStoreStub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Log4j2
@InboundMessageListener
@RequiredArgsConstructor
public final class InboundPlayerCommandListener {

    private final ExecutorService threadExecutor = Threads.newWorkStealingPool();
    private final PlayerStoreStub playerStoreStub;

    @Inject
    private CommandExecutor commandExecutor;

    @SubscribeMessage
    public void handle(InboundMessageContext<SendCommand> context) {
        SendCommand message = context.getMessage();
        Optional<Player> playerOptional = playerStoreStub.get(message.getPlayerId());

        if (playerOptional.isPresent()) {
            CompletableFuture<LegacyWrappedPlayerSender> executionFuture = executeCommand(playerOptional.get(),
                    reformatLabel(message.getLabel()));

            executionFuture.whenComplete((legacyWrappedSender, throwable) -> {
                if (throwable == null) {
                    context.callback(new SendCommand.Success(message.getPlayerId()));
                    legacyWrappedSender.flushMessageText();
                } else {
                    context.callback(new SendCommand.Failure(message.getPlayerId()));
                }
            });
        }
    }

    private String reformatLabel(String label) {
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        return label;
    }

    private CompletableFuture<LegacyWrappedPlayerSender> executeCommand(Player player, String label) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LegacyWrappedPlayerSender legacyWrappedSender = new LegacyWrappedPlayerSender(player);
                commandExecutor.execute(legacyWrappedSender, label);

                return legacyWrappedSender;
            } catch (CommandExecutionException exception) {
                throw new RuntimeException(exception);
            }
        }, threadExecutor);
    }

    // todo - убрать после влития новой системы команд.
    @RequiredArgsConstructor
    private static class LegacyWrappedPlayerSender implements EntityCommandSender {

        private final Player player;
        private TextComponent textComponent = Component.text("");

        public synchronized void flushMessageText() {
            if (!textComponent.content().isEmpty() || !textComponent.children().isEmpty()) {
                player.sendMessage(textComponent);
            }
        }

        @Override
        public String getName() {
            return player.getName();
        }

        @Override
        public synchronized void sendMessage(@Nullable String message) {
            if (!textComponent.content().isEmpty()) {
                message = "\n" + message;
            }
            textComponent = textComponent.content(textComponent.content() + message);
        }

        @Override
        public synchronized void sendMessage(@NotNull String message, @Nullable Object... replacements) {
            sendMessage(String.format(message, replacements));
        }

        @Override
        public synchronized boolean hasPermission(@NotNull String permission) {
            return player.hasPermission(Permission.named(permission));
        }
    }
}
