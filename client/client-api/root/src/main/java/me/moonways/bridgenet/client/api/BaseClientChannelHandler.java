package me.moonways.bridgenet.client.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.mtp.channel.BridgenetNetworkChannel;
import me.moonways.bridgenet.mtp.connection.client.BridgenetNetworkClientHandler;

import java.util.concurrent.CompletableFuture;

@Getter
@RequiredArgsConstructor
public class BaseClientChannelHandler implements BridgenetNetworkClientHandler {

    private final BridgenetClient connector;

    private CompletableFuture<BridgenetNetworkChannel> future;
    private BridgenetNetworkChannel channel;

    private void awaitNewChannel() {
        channel = null;
        future = new CompletableFuture<>();
    }

    public BridgenetNetworkChannel getChannel() {
        if (channel == null) {
            if (future != null) {
                return future.join();
            }
        }
        return channel;
    }

    private void completeAndFlush(BridgenetNetworkChannel channel) {
        if (this.future != null) {
            this.future.complete(channel);
            this.future = null;
        }
        this.channel = channel;
    }

    @Override
    public void onConnected(BridgenetNetworkChannel channel) {
        completeAndFlush(channel);

        connector.onConnected(channel);
    }

    @Override
    public void onDisconnected(BridgenetNetworkChannel channel) {
        connector.getBridgenetServerSync().exportClientDisconnect();

        connector.onConnectionClosed();

        this.channel = null;
    }

    @Override
    public void onReconnect(BridgenetNetworkChannel channel) {
        if (channel != null) {
            connector.handleConnection(channel);
            onConnected(channel);
        } else {
            awaitNewChannel();
        }
    }
}
