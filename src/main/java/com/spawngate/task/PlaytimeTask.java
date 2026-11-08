package com.spawngate.task;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PlaytimeTask extends BukkitRunnable {

    private final SpawnGatePlugin plugin;
    private final long intervalSeconds;

    public PlaytimeTask(SpawnGatePlugin plugin, long intervalSeconds) {
        this.plugin = plugin;
        this.intervalSeconds = intervalSeconds;
    }

    @Override
    public void run() {
        long targetHours = plugin.getConfig().getLong("verification.auto-verify-hours", 48);
        long targetSeconds = targetHours * 3600L;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getDatabaseManager().getCached(player.getUniqueId());
            if (data == null) continue;

            // Наигрывание времени идет только после того, как игрок прочитал правила!
            if (!data.isRulesCompleted()) continue;

            // Если уже верифицирован — пропускаем
            if (data.isVerified()) continue;

            data.addPlaytimeSeconds(intervalSeconds);

            // Проверка достижения порога 48 часов
            if (data.getPlaytimeSeconds() >= targetSeconds) {
                plugin.getVerificationManager().triggerAutoVerify(player, data);
            }
        }

        // Сохраняем состояние базы данных
        plugin.getDatabaseManager().saveAll();
    }
}
