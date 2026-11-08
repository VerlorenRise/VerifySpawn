package com.spawngate.hook;

import com.spawngate.SpawnGatePlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Level;

public class LuckPermsHook {

    private final SpawnGatePlugin plugin;
    private LuckPerms luckPerms;
    private boolean enabled = false;

    public LuckPermsHook(SpawnGatePlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                this.luckPerms = LuckPermsProvider.get();
                this.enabled = true;
                plugin.getLogger().info("Обнаружен LuckPerms! Интеграция успешно подключена.");
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось инициализировать LuckPermsProvider: " + t.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        return enabled && plugin.getConfig().getBoolean("luckperms.enabled", true);
    }

    public void addVerification(UUID uuid) {
        if (!isEnabled() || luckPerms == null) return;

        luckPerms.getUserManager().modifyUser(uuid, (User user) -> {
            String mode = plugin.getConfig().getString("luckperms.mode", "group");
            if ("group".equalsIgnoreCase(mode)) {
                String groupName = plugin.getConfig().getString("luckperms.group-name", "verified");
                InheritanceNode node = InheritanceNode.builder(groupName).build();
                user.data().add(node);
            } else {
                String perm = plugin.getConfig().getString("luckperms.permission-node", "spawngate.verified");
                PermissionNode node = PermissionNode.builder(perm).build();
                user.data().add(node);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Ошибка при добавлении группы/права LuckPerms для " + uuid, throwable);
            return null;
        });
    }

    public void removeVerification(UUID uuid) {
        if (!isEnabled() || luckPerms == null) return;

        luckPerms.getUserManager().modifyUser(uuid, (User user) -> {
            String mode = plugin.getConfig().getString("luckperms.mode", "group");
            if ("group".equalsIgnoreCase(mode)) {
                String groupName = plugin.getConfig().getString("luckperms.group-name", "verified");
                InheritanceNode node = InheritanceNode.builder(groupName).build();
                user.data().remove(node);
            } else {
                String perm = plugin.getConfig().getString("luckperms.permission-node", "spawngate.verified");
                PermissionNode node = PermissionNode.builder(perm).build();
                user.data().remove(node);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Ошибка при удалении группы/права LuckPerms для " + uuid, throwable);
            return null;
        });
    }
}
