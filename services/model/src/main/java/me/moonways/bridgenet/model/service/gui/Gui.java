package me.moonways.bridgenet.model.service.gui;

import me.moonways.bridgenet.model.service.gui.click.ItemClickListener;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.players.Player;

import java.util.UUID;

/**
 * Представляет собой удаленный интерфейс для управления GUI интерфейсом на клиенте Minecraft.
 * Этот интерфейс предоставляет методы для получения информации о GUI,
 * установки элементов в слотах, удаления элементов и добавления слушателей событий кликов.
 */
public interface Gui {

    /**
     * @return Идентификатор GUI.
     */
    UUID getId();

    /**
     * @return Описание GUI.
     */
    GuiDescription getDescription();

    /**
     * Получить элемент в указанном слоте GUI.
     *
     * @param slot - Слот GUI, в котором находится элемент.
     * @return - Элемент ItemStack в указанном слоте.
     */
    ItemStack getItem(GuiSlot slot);

    /**
     * Установить элемент в указанный слот GUI с указанным слушателем событий кликов.
     *
     * @param slot          - Слот GUI, в который нужно установить элемент.
     * @param itemStack     - Элемент ItemStack для установки.
     * @param clickListener - Слушатель событий кликов для элемента.
     */
    void setItem(GuiSlot slot, ItemStack itemStack, ItemClickListener clickListener);

    /**
     * Установить элемент в указанный слот GUI.
     *
     * @param slot      - Слот GUI, в который нужно установить элемент.
     * @param itemStack - Элемент ItemStack для установки.
     */
    void setItem(GuiSlot slot, ItemStack itemStack);

    /**
     * Удалить элемент из указанного слота GUI.
     *
     * @param slot - Слот GUI, из которого нужно удалить элемент.
     */
    void removeItem(GuiSlot slot);

    /**
     * Добавить слушателя событий кликов для всего GUI.
     *
     * @param listener - Слушатель событий кликов для всего GUI.
     */
    void addGlobalListener(ItemClickListener listener);

    /**
     * Добавить слушателя событий кликов для указанного слота GUI.
     *
     * @param slot     - Слот GUI, для которого нужно добавить слушателя событий кликов.
     * @param listener - Слушатель событий кликов для указанного слота GUI.
     */
    void addGlobalListener(GuiSlot slot, ItemClickListener listener);

    /**
     * Открыть текущий инвентарь определенному
     * игроку, находящемуся в статусе онлайн.
     */
    void open(Player player);
}
