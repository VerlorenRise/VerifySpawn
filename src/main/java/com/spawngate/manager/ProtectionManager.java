package com.spawngate.manager;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import com.spawngate.util.TimeFormatter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class ProtectionManager {

    private final SpawnGatePlugin plugin;

    public ProtectionManager(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Проверка: находится ли локация в зоне спавна (по умолчанию 300х300 от 0, 0)
     */
    public boolean isInSpawnZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        boolean allWorlds = plugin.getConfig().getBoolean("spawn.all-worlds", true);
        if (!allWorlds) {
            List<String> allowedWorlds = plugin.getConfig().getStringList("spawn.worlds");
            if (!allowedWorlds.contains(loc.getWorld().getName())) {
                return false;
            }
        }

        int centerX = plugin.getConfig().getInt("spawn.center-x", 0);
        int centerZ = plugin.getConfig().getInt("spawn.center-z", 0);
        int radius = plugin.getConfig().getInt("spawn.radius", 300);

        int deltaX = Math.abs(loc.getBlockX() - centerX);
        int deltaZ = Math.abs(loc.getBlockZ() - centerZ);

        return deltaX <= radius && deltaZ <= radius;
    }

    /**
     * Проверка: ограничен ли игрок в зоне спавна
     */
    public boolean isRestricted(Player player) {
        if (player.hasPermission("spawngate.bypass") || player.isOp()) {
            return false;
        }

        PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
        if (data == null) {
            data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        }

        return !data.isVerified();
    }

    /**
     * Отправка приватного предупреждения в чат с кулдауном 5 секунд
     */
    public void sendRestrictedWarning(Player player) {
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        long cooldownSec = plugin.getConfig().getLong("verification.message-cooldown-seconds", 5);

        if (!data.checkAndSetWarningCooldown(cooldownSec * 1000L)) {
            return; // Кулдаун еще не прошел
        }

        long autoHours = plugin.getConfig().getLong("verification.auto-verify-hours", 48);
        long targetSeconds = autoHours * 3600L;
        long remainingSeconds = Math.max(0, targetSeconds - data.getPlaytimeSeconds());
        String formattedRemaining = TimeFormatter.formatRemainingTime(remainingSeconds);

        String discordUrl = plugin.getConfig().getString("verification.discord-url", "https://discord.gg/CAf7PbhZBW");

        String template = plugin.getConfig().getString("messages.spawn-restricted", "");
        String formattedMsg = template
                .replace("<discord>", discordUrl)
                .replace("<remaining_time>", formattedRemaining);

        player.sendMessage(MiniMessage.miniMessage().deserialize(formattedMsg));
    }
}
