package me.moonways.bridgenet.test.services;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.gui.Gui;
import me.moonways.bridgenet.model.service.gui.GuiDescription;
import me.moonways.bridgenet.model.service.gui.GuiServiceModel;
import me.moonways.bridgenet.model.service.gui.GuiType;
import me.moonways.bridgenet.model.service.gui.click.ClickAction;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.gui.item.entries.material.Material;
import me.moonways.bridgenet.model.service.gui.item.entries.material.MaterialEntry;
import me.moonways.bridgenet.model.service.gui.item.types.Materials;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@Log4j2
@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class GuiServiceEndpointTest {

    @Inject
    private GuiServiceModel serviceModel;

    @Test
    public void test_materialsGet() {
        Material material = Materials.STONE;
        log.debug(material);

        MaterialEntry stoneEntry = material.entry();

        assertEquals(1, stoneEntry.getId());
        assertEquals("block.minecraft.stone", stoneEntry.getTranslationKey());
        assertEquals("minecraft:stone", stoneEntry.getCorrespondingBlock());
    }

    @Test
    public void test_itemStackCreate() {
        ItemStack itemStack = ItemStack.create()
                .material(TestConst.Items.MATERIAL)
                .name(TestConst.Items.NAME);

        log.debug(itemStack);

        assertEquals(1, itemStack.amount());
        assertEquals(itemStack, serviceModel.getItems().named(Materials.DIAMOND_BLOCK, TestConst.Items.NAME));
    }

    @Test
    public void test_createGuiFromType() {
        Gui gui = serviceModel.createGui(GuiType.CHEST_4_ROW);

        log.debug(gui);

        assertNotNull(gui);
        assertNotNull(gui.getId());
        assertNotNull(gui.getDescription());
        assertNull(gui.getItem(TestConst.Items.SLOT));
    }

    @Test
    public void test_createGuiFromDescription() {
        Gui gui = serviceModel.createGui(
                GuiDescription.builder()
                        .type(TestConst.Inventory.TYPE)
                        .size(TestConst.Inventory.SIZE)
                        .title(TestConst.Inventory.TITLE)
                        .build());

        log.debug(gui);

        assertNotNull(gui);
        assertNotNull(gui.getId());
        assertNull(gui.getItem(TestConst.Items.SLOT));
    }

    @Test
    public void test_createGuiWithItems() {
        Gui gui = serviceModel.createGui(TestConst.Inventory.TYPE);
        gui.setItem(TestConst.Items.SLOT,
                ItemStack.create()
                        .material(TestConst.Items.MATERIAL)
                        .name(TestConst.Items.NAME));

        gui.addGlobalListener(TestConst.Items.SLOT, log::debug);

        serviceModel.fireClickAction(
                ClickAction.builder()
                        .slot(TestConst.Items.SLOT)
                        .clickType(TestConst.Items.CLICK_TYPE)
                        .playerId(TestConst.Player.ID)
                        .guiId(gui.getId())
                        .build());

        log.debug(gui);

        assertNotNull(gui);
        assertNotNull(gui.getId());
        assertNotNull(gui.getDescription());
        assertNotNull(gui.getItem(TestConst.Items.SLOT));
    }
}

