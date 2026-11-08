package com.spawngate.listener;

import com.spawngate.SpawnGatePlugin;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SpawnProtectionListener implements Listener {

    private final SpawnGatePlugin plugin;

    public SpawnProtectionListener(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Блокировка разрушения блоков на спавне
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectionManager().isRestricted(player) &&
                plugin.getProtectionManager().isInSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getProtectionManager().sendRestrictedWarning(player);
        }
    }

    /**
     * Блокировка установки блоков на спавне
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectionManager().isRestricted(player) &&
                plugin.getProtectionManager().isInSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getProtectionManager().sendRestrictedWarning(player);
        }
    }

    /**
     * Блокировка взаимодействия с контейнерами и миром (кроме открытия дверей!)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getProtectionManager().isRestricted(player)) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked != null && plugin.getProtectionManager().isInSpawnZone(clicked.getLocation())) {

            // Разрешено: открытие дверей любого типа (деревянные, железные и т.д.)
            if (isDoor(clicked.getType())) {
                return;
            }

            // Любые другие блоки (сундуки, бочки, печи, воронки, кнопки, рычаги и т.д.) блокируем
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.getProtectionManager().sendRestrictedWarning(player);
                return;
            }
        }

        // Защита от разливания жидкостей / использования ведер
        if (plugin.getProtectionManager().isInSpawnZone(player.getLocation())) {
            Material hand = event.getMaterial();
            if (hand == Material.LAVA_BUCKET || hand == Material.WATER_BUCKET ||
                    hand == Material.FLINT_AND_STEEL || hand == Material.FIRE_CHARGE) {
                event.setCancelled(true);
                plugin.getProtectionManager().sendRestrictedWarning(player);
            }
        }
    }

    /**
     * Блокировка выливания ведер
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectionManager().isRestricted(player) &&
                plugin.getProtectionManager().isInSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getProtectionManager().sendRestrictedWarning(player);
        }
    }

    /**
     * Блокировка зачерпывания ведрами
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectionManager().isRestricted(player) &&
                plugin.getProtectionManager().isInSpawnZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.getProtectionManager().sendRestrictedWarning(player);
        }
    }

    /**
     * Блокировка взаимодействия со стойками для брони, рамками, жителями и т.д.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getProtectionManager().isRestricted(player) &&
                plugin.getProtectionManager().isInSpawnZone(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            plugin.getProtectionManager().sendRestrictedWarning(player);
        }
    }

    /**
     * Блокировка сбивания рамок и картин
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (plugin.getProtectionManager().isRestricted(player) &&
                    plugin.getProtectionManager().isInSpawnZone(event.getEntity().getLocation())) {
                event.setCancelled(true);
                plugin.getProtectionManager().sendRestrictedWarning(player);
            }
        }
    }

    /**
     * Вспомогательный метод для проверки, является ли материал дверью
     */
    private boolean isDoor(Material material) {
        if (material == null) return false;
        try {
            return Tag.DOORS.isTagged(material) || material.name().endsWith("_DOOR");
        } catch (Throwable t) {
            return material.name().endsWith("_DOOR");
        }
    }
}
