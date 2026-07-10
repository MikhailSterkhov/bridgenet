package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.CommandSession;
import me.moonways.bridgenet.api.command.annotation.Command;
import me.moonways.bridgenet.api.command.annotation.MentorExecutor;
import me.moonways.bridgenet.api.command.sender.ConsoleCommandSender;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.minecraft.ChatColor;
import me.moonways.bridgenet.model.service.gui.*;
import me.moonways.bridgenet.model.service.gui.item.ItemStack;
import me.moonways.bridgenet.model.service.gui.item.types.Materials;
import me.moonways.bridgenet.model.service.players.Player;
import me.moonways.bridgenet.model.service.players.PlayersServiceModel;
import net.kyori.adventure.text.Component;

import java.util.Arrays;

@Command("gui")
public class TestGuiCommand {

    @Inject
    private GuiServiceModel guiServiceModel;
    @Inject
    private PlayersServiceModel playersServiceModel;

    @MentorExecutor
    public void defaultCommand(CommandSession session) {
        if (session.getSender() instanceof ConsoleCommandSender) {
            session.getSender().sendMessage(ChatColor.RED + "This command can use only players!");
            return;
        }

        Gui gui = guiServiceModel.createGui(
                GuiDescription.builder()
                        .title("Тестовый инвентарь")
                        .type(GuiType.CHEST)
                        .size(GuiDescription.toSize(5, GuiType.CHEST))
                        .build());

        gui.setItem(GuiSlot.at(5),
                ItemStack.create()
                        .material(Materials.STICK)
                        .name("§6§lТестовый предмет")
                        .lore(Arrays.asList("§7Написанный внутри системы §3BridgeNet", "§7и переданный по удаленному сервису")));

        gui.setItem(GuiSlot.center(gui.getDescription()),
                ItemStack.create()
                        .material(Materials.DIAMOND)
                        .name("§6§lЕще один тестовый предмет")
                        .lore(Arrays.asList("§7Написанный внутри системы §3BridgeNet", "§7и переданный по удаленному сервису", "", "§e> Сюда можно нажать!")),
                event -> {

                    Player player = event.getPlayer();
                    player.sendMessage(Component.text("Клик по алмазу воспроизведен"));
                });

        gui.addGlobalListener(GuiSlot.center(gui.getDescription()),
                (event) -> {
                    Player player = event.getPlayer();
                    player.sendMessage(Component.text("Глобальный клик по алмазу воспроизведен"));
                });

        Player player = playersServiceModel.store()
                .get(session.getSender().getName())
                .orElse(null);

        gui.open(player);
    }
}
