package me.moonways.bridgenet.mtp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.mtp.exception.ChannelException;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Getter
@Log4j2
public class MTPServer implements MTPConnection {

    private final ServerBootstrap serverBootstrap;

    private MTPChannel channel;

    private void handleChannelFuture(ChannelFuture channelFuture, CompletableFuture<MTPChannel> completableFuture) {
        if (channelFuture.isSuccess()) {

            Channel channel = channelFuture.channel();
            log.info("Successful bind server {}", channel);

            this.channel = new MTPChannel(channel);

            if (completableFuture != null) {
                completableFuture.complete(this.channel);
            }
        }
        else {
            ChannelException exception = new ChannelException(channelFuture.cause(), "Internal asynchronous bind error");
            log.error("§4Server bind proceed with exception: §c{}", exception.toString());

            if (completableFuture != null) {
                completableFuture.completeExceptionally(exception);
            }
            else {
                log.error(exception);
            }
        }
    }

    @Override
    public MTPChannel bindSync() {
        log.info("Trying netty channel bind synchronized");

        ChannelFuture channelFuture = serverBootstrap.bind().syncUninterruptibly();
        handleChannelFuture(channelFuture, null);

        return channel;
    }

    @Override
    public CompletableFuture<MTPChannel> bind() {
        log.info("Trying netty channel bind asynchronous");

        CompletableFuture<MTPChannel> completableFuture = new CompletableFuture<>();
        serverBootstrap.bind().addListener((ChannelFutureListener) future -> handleChannelFuture(future, completableFuture));

        return completableFuture;
    }

    @Override
    public MTPChannel connectSync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<MTPChannel> connect() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdownGracefully() {
        if (channel == null) {
            throw new ChannelException("channel is null");
        }

        log.info("Netty channel is shutting down");
        channel.close();
    }
}
