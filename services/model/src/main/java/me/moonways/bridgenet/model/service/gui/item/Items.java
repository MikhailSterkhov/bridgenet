package me.moonways.bridgenet.model.service.gui.item;

import me.moonways.bridgenet.model.service.gui.item.entries.material.Material;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Интерфейс Items представляет собой фабрику ItemStack для создания различных типов предметов в Minecraft.
 * Этот интерфейс определяет методы для создания ItemStack
 * с различными характеристиками, такими как материал, название и прочность.
 */
public interface Items extends Remote {

    /**
     * @return - Пустой ItemStack.
     */
    ItemStack empty() throws RemoteException;

    /**
     * Создать пустой ItemStack с заданным количеством.
     *
     * @param amount - Количество предметов в ItemStack.
     * @return - Пустой ItemStack с заданным количеством.
     */
    ItemStack empty(int amount) throws RemoteException;

    /**
     * Создать ItemStack с указанным материалом.
     *
     * @param material - Материал предмета.
     * @return - ItemStack с указанным материалом.
     */
    ItemStack typed(Material material) throws RemoteException;

    /**
     * Создать ItemStack с указанным материалом и названием.
     *
     * @param material - Материал предмета.
     * @param name - Название предмета.
     * @return - ItemStack с указанным материалом и названием.
     */
    ItemStack named(Material material, String name) throws RemoteException;

    /**
     * Создать ItemStack с указанным материалом и прочностью.
     *
     * @param material - Материал предмета.
     * @param durability - Прочность предмета.
     * @return - ItemStack с указанным материалом и прочностью.
     */
    ItemStack item(Material material, int durability) throws RemoteException;

    /**
     * Создать ItemStack с указанным материалом, прочностью и количеством.
     *
     * @param material - Материал предмета.
     * @param durability - Прочность предмета.
     * @param amount - Количество предметов в ItemStack.
     * @return - ItemStack с указанным материалом, прочностью и количеством.
     */
    ItemStack item(Material material, int durability, int amount) throws RemoteException;
}
