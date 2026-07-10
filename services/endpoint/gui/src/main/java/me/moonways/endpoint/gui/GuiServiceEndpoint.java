package me.moonways.endpoint.gui;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.model.event.GuiItemClickEvent;
import me.moonways.bridgenet.model.service.gui.Gui;
import me.moonways.bridgenet.model.service.gui.GuiDescription;
import me.moonways.bridgenet.model.service.gui.GuiServiceModel;
import me.moonways.bridgenet.model.service.gui.GuiType;
import me.moonways.bridgenet.model.service.gui.click.ClickAction;
import me.moonways.bridgenet.model.service.gui.item.Items;
import me.moonways.bridgenet.model.service.language.MessageTypes;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteContext;
import me.moonways.bridgenet.rmi.endpoint.persistance.EndpointRemoteObject;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Log4j2
public final class GuiServiceEndpoint extends EndpointRemoteObject implements GuiServiceModel {

    private static final long serialVersionUID = -698118002154484440L;

    private final Cache<UUID, GuiStub> guisCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(10, TimeUnit.MINUTES)
                    .build();

    @Inject
    private BeansService beansService;
    @Inject
    private PlayersServiceModel playersServiceModel;

    @Getter
    private final Items items = new ItemsStub();
    @Getter
    private final GuiNetworkManager guiNetworkManager = new GuiNetworkManager();

    public GuiServiceEndpoint() throws RemoteException {
        super();
    }

    @Override
    protected void construct(EndpointRemoteContext context) {
        context.registerMessageListener(new InboundGuiClickListener(this));
    }

    @Override
    public Gui createGui(GuiType type) throws RemoteException {
        return createGui(
                GuiDescription.builder()
                        .title("")
                        .type(type)
                        .size(type.getSize())
                        .build()
        );
    }

    @Override
    public Gui createGui(GuiDescription description) throws RemoteException {
        GuiStub guiStub = new GuiStub(UUID.randomUUID(), description, guiNetworkManager);

        beansService.inject(guiStub);
        guisCache.put(guiStub.getId(), guiStub);

        return guiStub;
    }

    @Override
    public Optional<Gui> getGui(UUID id) {
        guisCache.cleanUp();
        return Optional.ofNullable(guisCache.getIfPresent(id));
    }

    @Override
    public void fireClickAction(ClickAction clickAction) throws RemoteException {
        Optional<Player> playerOptional = playersServiceModel.store().get(clickAction.getPlayerId());
        if (!playerOptional.isPresent()) {
            return;
        }

        Player player = playerOptional.get();
        Optional<Gui> guiOptional = getGui(clickAction.getGuiId());

        if (!guiOptional.isPresent()) {
            player.sendMessage(MessageTypes.GUI_SESSION_EXPIRED);

            fireCloseGui(player);
            return;
        }

        Gui gui = guiOptional.get();
        GuiItemClickEvent event =
                GuiItemClickEvent.builder()
                        .player(player)
                        .gui(gui)
                        .slot(clickAction.getSlot())
                        .clickType(clickAction.getClickType())
                        .itemStack(gui.getItem(clickAction.getSlot()))
                        .build();

        ((GuiStub) gui).fireClick(event);
    }

    @Override
    public void fireCloseGui(Player player) throws RemoteException {
        guiNetworkManager.closeOpenedGui(player);
    }
}
