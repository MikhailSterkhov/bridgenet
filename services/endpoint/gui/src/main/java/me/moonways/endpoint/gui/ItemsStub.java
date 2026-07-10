package me.moonways.endpoint.gui;

import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.gui.item.Items;
import me.moonways.bridgenet.model.service.gui.item.entries.material.Material;
import me.moonways.bridgenet.model.service.gui.item.types.Materials;


public class ItemsStub implements Items {

    @Override
    public ItemStack empty() {
        return ItemStack.create()
                .material(Materials.AIR);
    }

    @Override
    public ItemStack empty(int amount) {
        return empty().amount(amount);
    }

    @Override
    public ItemStack typed(Material material) {
        return ItemStack.create()
                .material(material);
    }

    @Override
    public ItemStack named(Material material, String name) {
        return ItemStack.create()
                .material(material)
                .name(name);
    }

    @Override
    public ItemStack item(Material material, int durability) {
        return ItemStack.create()
                .material(material)
                .durability(durability);
    }

    @Override
    public ItemStack item(Material material, int durability, int amount) {
        return ItemStack.create()
                .material(material)
                .durability(durability)
                .amount(amount);
    }
}
