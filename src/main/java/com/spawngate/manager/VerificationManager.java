package com.spawngate.manager;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class VerificationManager {

    private final SpawnGatePlugin plugin;

    public VerificationManager(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean verify(String username, boolean notify) {
        PlayerData data = plugin.getDatabaseManager().findByUsername(username);
        if (data == null) {
            Player online = Bukkit.getPlayerExact(username);
            if (online != null) {
                data = plugin.getDatabaseManager().getOrLoad(online.getUniqueId(), online.getName());
            }
        }

        if (data == null) {
            return false;
        }

        if (data.isVerified()) {
            return false;
        }

        data.setVerified(true);
        plugin.getDatabaseManager().save(data);

        // Интеграция с LuckPerms
        plugin.getLuckPermsHook().addVerification(data.getUuid());

        // Оповещение игрока, если он онлайн
        Player onlinePlayer = Bukkit.getPlayer(data.getUuid());
        if (onlinePlayer != null && onlinePlayer.isOnline() && notify) {
            String msg = plugin.getConfig().getString("messages.manual-verified", "<green>Ваша заявка подтверждена!</green>");
            onlinePlayer.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        return true;
    }

    public boolean unverify(String username, boolean notify) {
        PlayerData data = plugin.getDatabaseManager().findByUsername(username);
        if (data == null) {
            Player online = Bukkit.getPlayerExact(username);
            if (online != null) {
                data = plugin.getDatabaseManager().getOrLoad(online.getUniqueId(), online.getName());
            }
        }

        if (data == null) {
            return false;
        }

        if (!data.isVerified()) {
            return false;
        }

        data.setVerified(false);
        plugin.getDatabaseManager().save(data);

        // Снятие группы/права LuckPerms
        plugin.getLuckPermsHook().removeVerification(data.getUuid());

        // Оповещение игрока
        Player onlinePlayer = Bukkit.getPlayer(data.getUuid());
        if (onlinePlayer != null && onlinePlayer.isOnline() && notify) {
            String msg = plugin.getConfig().getString("messages.manual-unverified", "<red>Верификация отозвана!</red>");
            onlinePlayer.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }

        return true;
    }

    public void triggerAutoVerify(Player player, PlayerData data) {
        data.setVerified(true);
        plugin.getDatabaseManager().save(data);
        plugin.getLuckPermsHook().addVerification(player.getUniqueId());

        String msg = plugin.getConfig().getString("messages.auto-verified", "<green>Поздравляем с авто-верификацией!</green>");
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }
}
