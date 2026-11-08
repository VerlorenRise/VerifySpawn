package com.spawngate.listener;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.RegisterEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AuthMeListener implements Listener {

    private final SpawnGatePlugin plugin;

    public AuthMeListener(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogin(LoginEvent event) {
        handleAuthenticated(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegister(RegisterEvent event) {
        handleAuthenticated(event.getPlayer());
    }

    private void handleAuthenticated(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
            data.setSessionJoinTimestamp(System.currentTimeMillis());

            // Если правила еще не прочитаны — только теперь блокируем игрока и выводим правила в чат!
            if (!data.isRulesCompleted()) {
                plugin.getRulesManager().freezePlayer(player);
                plugin.getRulesManager().sendPage(player, data.getCurrentRulePage());
            }
        }, 5L); // Небольшая задержка 5 тиков, чтобы AuthMe закончил свои процедуры
    }
}
