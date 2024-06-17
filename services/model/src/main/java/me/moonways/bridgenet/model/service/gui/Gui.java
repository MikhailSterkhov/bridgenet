package me.moonways.bridgenet.model.service.gui;

import me.moonways.bridgenet.model.service.gui.click.ItemClickListener;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.players.Player;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

/**
 * Представляет собой удаленный интерфейс для управления GUI интерфейсом на клиенте Minecraft.
 * Этот интерфейс предоставляет методы для получения информации о GUI,
 * установки элементов в слотах, удаления элементов и добавления слушателей событий кликов.
 */
public interface Gui extends Remote {

    /**
     * @return Идентификатор GUI.
     */
    UUID getId() throws RemoteException;

    /**
     * @return Описание GUI.
     */
    GuiDescription getDescription() throws RemoteException;

    /**
     * Получить элемент в указанном слоте GUI.
     *
     * @param slot - Слот GUI, в котором находится элемент.
     * @return - Элемент ItemStack в указанном слоте.
     */
    ItemStack getItem(GuiSlot slot) throws RemoteException;

    /**
     * Установить элемент в указанный слот GUI с указанным слушателем событий кликов.
     *
     * @param slot          - Слот GUI, в который нужно установить элемент.
     * @param itemStack     - Элемент ItemStack для установки.
     * @param clickListener - Слушатель событий кликов для элемента.
     */
    void setItem(GuiSlot slot, ItemStack itemStack, ItemClickListener clickListener) throws RemoteException;

    /**
     * Установить элемент в указанный слот GUI.
     *
     * @param slot      - Слот GUI, в который нужно установить элемент.
     * @param itemStack - Элемент ItemStack для установки.
     */
    void setItem(GuiSlot slot, ItemStack itemStack) throws RemoteException;

    /**
     * Удалить элемент из указанного слота GUI.
     *
     * @param slot - Слот GUI, из которого нужно удалить элемент.
     */
    void removeItem(GuiSlot slot) throws RemoteException;

    /**
     * Добавить слушателя событий кликов для всего GUI.
     *
     * @param listener - Слушатель событий кликов для всего GUI.
     */
    void addGlobalListener(ItemClickListener listener) throws RemoteException;

    /**
     * Добавить слушателя событий кликов для указанного слота GUI.
     *
     * @param slot     - Слот GUI, для которого нужно добавить слушателя событий кликов.
     * @param listener - Слушатель событий кликов для указанного слота GUI.
     */
    void addGlobalListener(GuiSlot slot, ItemClickListener listener) throws RemoteException;

    /**
     * Открыть текущий инвентарь определенному
     * игроку, находящемуся в статусе онлайн.
     */
    void open(Player player) throws RemoteException;
}
