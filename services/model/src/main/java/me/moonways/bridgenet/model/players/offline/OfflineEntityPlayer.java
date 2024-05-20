package me.moonways.bridgenet.model.players.offline;

import me.moonways.bridgenet.api.command.uses.entity.EntitySenderType;
import me.moonways.bridgenet.model.players.EntityPlayer;
import me.moonways.bridgenet.model.players.Title;
import me.moonways.bridgenet.model.servers.EntityServer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@RequiredArgsConstructor
public class OfflineEntityPlayer implements EntityPlayer, Serializable {

    private static final long serialVersionUID = 4063154190690621348L;

    private final UUID uniqueId;

    private final String name;

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return false; // todo
    }

    @Override
    public EntitySenderType getType() {
        return EntitySenderType.USER;
    }

    @Override
    public boolean isInstanceOf(EntitySenderType entityType) {
        return entityType.equals(EntitySenderType.USER);
    }

    @Override
    public UUID getUuid() {
        return uniqueId;
    }

    @Override
    public void sendMessage(@Nullable String message) {
        throw new UnsupportedOperationException("offline");
    }

    @Override
    public void sendMessage(@NotNull String message, @Nullable Object... replacements) {
        sendMessage(String.format(message, replacements));
    }

    @Override
    public EntityServer getVelocityServer() {
        return null;
    }

    @Override
    public EntityServer getSpigotServer() {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> redirect(@NotNull EntityServer server) {
        CompletableFuture<Boolean> completableFuture = CompletableFuture.completedFuture(false);
        completableFuture.completeExceptionally(new UnsupportedOperationException("offline"));

        return completableFuture;
    }

    @Override
    public void sendTitle(@Nullable String title, @Nullable String subtitle) {
        throw new UnsupportedOperationException("offline");
    }

    @Override
    public void sendTitle(@NotNull Title title) {
        throw new UnsupportedOperationException("offline");
    }
}
