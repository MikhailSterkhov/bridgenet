package me.moonways.bridgenet.model.players;

import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.model.servers.EntityServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EntityPlayer extends EntityCommandSender, Remote {

    UUID getUniqueId() throws RemoteException;

    EntityServer getVelocityServer() throws RemoteException;

    EntityServer getSpigotServer() throws RemoteException;

    CompletableFuture<Boolean> redirect(@NotNull EntityServer server) throws RemoteException;

    void sendTitle(@Nullable String title, @Nullable String subtitle);

    void sendTitle(@NotNull Title title);
}
