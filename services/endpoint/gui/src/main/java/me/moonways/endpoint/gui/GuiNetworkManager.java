package me.moonways.endpoint.gui;

import me.moonways.bridgenet.model.message.CloseGui;
import me.moonways.bridgenet.model.message.OpenGui;
import me.moonways.bridgenet.model.service.gui.Gui;
import me.moonways.bridgenet.model.service.gui.GuiSlot;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.players.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Предоставляет методы обработки и передачи
 * удаленных данных по сети BridgeNet между
 * клиентами и сервером.
 */
public class GuiNetworkManager {

    /**
     * Открывает GUI для указанного игрока.
     *
     * @param player игрок, для которого открывается GUI.
     * @param gui    графический интерфейс, который необходимо открыть.
     */
    public void openGui(Player player, Gui gui) {
        OpenGui openGui = new OpenGui(
                player.getId(),
                gui.getId(),
                gui.getDescription(), getItemViewList(gui));

        player.getConnection().send(openGui);
    }

    /**
     * Закрывает открытый GUI для указанного игрока.
     *
     * @param player игрок, для которого закрывается GUI.
     */
    public void closeOpenedGui(Player player) {
        player.getConnection().send(new CloseGui(player.getId()));
    }

    /**
     * Формирует список элементов GUI для указанного графического интерфейса.
     *
     * @param gui графический интерфейс, для которого формируется список элементов.
     * @return список представлений элементов GUI.
     */
    private List<OpenGui.GuiItemView> getItemViewList(Gui gui) {
        List<OpenGui.GuiItemView> result = new ArrayList<>();
        GuiStub guiStub = ((GuiStub) gui);

        for (Map.Entry<GuiSlot, ItemStack> item : guiStub.getContentMap().entrySet()) {
            result.add(
                    OpenGui.GuiItemView.builder()
                            .slot(item.getKey().get())
                            .itemStack(item.getValue())
                            .build());
        }
        return result;
    }
}
