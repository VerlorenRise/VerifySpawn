package com.spawngate.hook;

import com.spawngate.SpawnGatePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AuthMeHook {

    private final SpawnGatePlugin plugin;
    private boolean enabled = false;

    public AuthMeHook(SpawnGatePlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("AuthMe")) {
            this.enabled = true;
            plugin.getLogger().info("Обнаружен AuthMe! Активирована интеграция авторизации.");
        } else {
            plugin.getLogger().info("AuthMe не обнаружен. Правила будут открываться сразу при входе.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAuthenticated(Player player) {
        if (!enabled) {
            return true;
        }
        try {
            return fr.xephi.authme.api.v3.AuthMeApi.getInstance().isAuthenticated(player);
        } catch (Throwable t) {
            return true;
        }
    }
}
