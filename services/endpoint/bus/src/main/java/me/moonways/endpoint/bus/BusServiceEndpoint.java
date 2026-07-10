package me.moonways.endpoint.bus;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.bus.BusServiceModel;
import me.moonways.bridgenet.mtp.BridgenetNetworkController;
import me.moonways.bridgenet.mtp.channel.ChannelDirection;
import me.moonways.bridgenet.mtp.config.NetworkJsonConfiguration;
import me.moonways.bridgenet.mtp.connection.BridgenetNetworkConnectionFactory;
import me.moonways.bridgenet.mtp.connection.NetworkBootstrapFactory;
import me.moonways.bridgenet.mtp.inbound.InboundChannelOptionsHandler;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import me.moonways.endpoint.bus.handler.GetCommandsMessageHandler;

public final class BusServiceEndpoint extends EndpointServiceObject implements BusServiceModel {

    @Inject
    private BridgenetNetworkConnectionFactory networkConnectionFactory;
    @Inject
    private BridgenetNetworkController networkController;
    @Inject
    private InboundChannelOptionsHandler inboundChannelOptionsHandler;

    @Override
    protected void construct(EndpointRemoteContext context) {
        networkController.bindMessages();
        networkController.bindMessageListeners();

        bindServer();

        context.registerMessageListener(new GetCommandsMessageHandler());
    }

    private void bindServer() {
        ChannelFactory<? extends ServerChannel> serverChannelFactory = NetworkBootstrapFactory.createServerChannelFactory();
        NetworkJsonConfiguration configuration = networkConnectionFactory.getConfiguration();

        EventLoopGroup parentWorker = NetworkBootstrapFactory.createEventLoopGroup(configuration.getSettings().getWorkers().getBossThreads());
        EventLoopGroup childWorker = NetworkBootstrapFactory.createEventLoopGroup(configuration.getSettings().getWorkers().getChildThreads());

        inboundChannelOptionsHandler.setChannelDirection(ChannelDirection.TO_CLIENT);

        networkConnectionFactory.newServerBuilder()
                .setChannelFactory(serverChannelFactory)
                .setChannelInitializer(inboundChannelOptionsHandler)
                .setGroup(parentWorker, childWorker)
                .build()
                .bindSync();
    }
}
