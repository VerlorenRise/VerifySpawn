package com.spawngate.listener;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final SpawnGatePlugin plugin;

    public PlayerConnectionListener(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        data.setSessionJoinTimestamp(System.currentTimeMillis());

        // Если AuthMe отсутствует на сервере, правила открываются сразу при входе
        if (!plugin.getAuthMeHook().isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !data.isRulesCompleted()) {
                    plugin.getRulesManager().freezePlayer(player);
                    plugin.getRulesManager().sendPage(player, data.getCurrentRulePage());
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());

        if (data != null) {
            long now = System.currentTimeMillis();
            long sessionSec = Math.max(0, (now - data.getSessionJoinTimestamp()) / 1000L);
            data.addPlaytimeSeconds(sessionSec);
            plugin.getDatabaseManager().unload(player.getUniqueId());
        }

        plugin.getRulesManager().unfreezePlayer(player);
    }
}
