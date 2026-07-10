package me.moonways.endpoint.gui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.api.event.EventService;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.model.event.GuiItemClickEvent;
import me.moonways.bridgenet.model.service.gui.Gui;
import me.moonways.bridgenet.model.service.gui.GuiDescription;
import me.moonways.bridgenet.model.service.gui.GuiSlot;
import me.moonways.bridgenet.model.service.gui.click.ItemClickListener;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.players.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class GuiStub implements Gui {

    @ToString.Include
    private final UUID id;
    @ToString.Include
    private final GuiDescription description;

    @Getter
    private final Map<GuiSlot, ItemStack> contentMap = new ConcurrentHashMap<>();
    private final Map<GuiSlot, List<ItemClickListener>> listenersMap = new ConcurrentHashMap<>();
    private final List<ItemClickListener> globalListeners = new CopyOnWriteArrayList<>();

    @Getter
    private final GuiNetworkManager network;

    @Inject
    private BeansService beansService;
    @Inject
    private EventService eventService;

    private void addContent(GuiSlot slot, ItemStack itemStack, ItemClickListener clickListener) {
        removeContent(slot);
        if (itemStack != null) {
            contentMap.put(slot, itemStack);
        }

        addClickContent(slot, clickListener);
    }

    private void addClickContent(GuiSlot slot, ItemClickListener clickListener) {
        if (clickListener != null) {
            List<ItemClickListener> listeners = listenersMap.get(slot);

            if (listeners == null) {
                listeners = new ArrayList<>();
            }

            beansService.inject(clickListener);
            listeners.add(clickListener);

            listenersMap.put(slot, listeners);
        }
    }

    private void removeContent(GuiSlot slot) {
        contentMap.remove(slot);
        listenersMap.remove(slot);
    }

    public void fireClick(GuiItemClickEvent event) {
        eventService.fireEvent(event);
        globalListeners.forEach(listener -> fireClickOn(listener, event));

        List<ItemClickListener> listeners = listenersMap.get(event.getSlot());
        if (listeners != null) {
            listeners
                    .forEach(listener -> fireClickOn(listener, event));
        }
    }

    private void fireClickOn(ItemClickListener listener, GuiItemClickEvent event) {
        try {
            listener.consume(event);
        } catch (Throwable exception) {
            throw new GuiEndpointException(exception);
        }
    }

    @Override
    public ItemStack getItem(GuiSlot slot) {
        return contentMap.get(slot);
    }

    @Override
    public void setItem(GuiSlot slot, ItemStack itemStack, ItemClickListener clickListener) {
        addContent(slot, itemStack, clickListener);
    }

    @Override
    public void setItem(GuiSlot slot, ItemStack itemStack) {
        addContent(slot, itemStack, null);
    }

    @Override
    public void removeItem(GuiSlot slot) {
        removeContent(slot);
    }

    @Override
    public void addGlobalListener(ItemClickListener listener) {
        globalListeners.add(listener);
    }

    @Override
    public void addGlobalListener(GuiSlot slot, ItemClickListener listener) {
        addClickContent(slot, listener);
    }

    @Override
    public void open(Player player) {
        network.openGui(player, this);
    }
}
