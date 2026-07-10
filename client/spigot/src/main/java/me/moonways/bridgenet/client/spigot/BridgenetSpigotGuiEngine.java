package me.moonways.bridgenet.client.spigot;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.client.api.BridgenetServerSync;
import me.moonways.bridgenet.client.spigot.service.gui.BukkitGui;
import me.moonways.bridgenet.client.spigot.service.gui.RemoteItemParser;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Класс BridgenetSpigotGuiEngine отвечает за управление графическими интерфейсами (GUI) на стороне сервера Spigot
 * и обработку взаимодействий с ними.
 */
@Autobind
public final class BridgenetSpigotGuiEngine {
    private final Cache<UUID, BukkitGui> remoteGuisCache =
            CacheBuilder.newBuilder()
                    .expireAfterAccess(10, TimeUnit.MINUTES)
                    .build();

    @Inject
    private RemoteItemParser remoteItemParser;
    @Inject
    private BeansService beansService;

    @Inject
    private BridgenetServerSync bridgenetServerSync;

    /**
     * Обрабатывает действие клика по GUI.
     *
     * @param event событие клика в инвентаре.
     */
    public void sendClickAction(InventoryClickEvent event) {
        UUID playerId = event.getWhoClicked().getUniqueId();

        remoteGuisCache.cleanUp();
        BukkitGui bukkitGui = remoteGuisCache.getIfPresent(playerId);

        if (bukkitGui == null) {
            return;
        }

        event.setCancelled(true);
        bridgenetServerSync.exportGuiClickAction(
                playerId,
                bukkitGui.getId(),
                event.getSlot() + 1,
                remoteItemParser.remote(event.getClick()));
    }

    /**
     * Сохраняет GUI для указанного игрока в кэш.
     *
     * @param player игрок, для которого сохраняется GUI.
     * @param bukkitGui GUI, который необходимо сохранить.
     */
    public void save(Player player, BukkitGui bukkitGui) {
        beansService.inject(bukkitGui);

        remoteGuisCache.put(player.getUniqueId(), bukkitGui);
        remoteGuisCache.cleanUp();
    }

    /**
     * Удаляет сохраненный GUI для указанного игрока из кэша.
     *
     * @param player игрок, для которого необходимо удалить GUI.
     */
    public void invalidate(Player player) {
        remoteGuisCache.invalidate(player.getUniqueId());
        remoteGuisCache.cleanUp();
    }
}
