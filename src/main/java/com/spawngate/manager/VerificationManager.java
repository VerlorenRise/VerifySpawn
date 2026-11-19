package com.spawngate.manager;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class VerificationManager {

    private final SpawnGatePlugin plugin;

    public VerificationManager(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean verify(String username, boolean notify) {
        return verify(username, notify, "Администратор");
    }

    public boolean verify(String username, boolean notify, String administrator) {
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

        Player onlinePlayer = Bukkit.getPlayer(data.getUuid());
        boolean canNotifyNow = onlinePlayer != null && onlinePlayer.isOnline()
                && plugin.getAuthMeHook().isAuthenticated(onlinePlayer);
        String previousAdministrator = data.getVerifiedBy();
        boolean previousPending = data.isVerificationNotificationPending();
        data.setVerified(true);
        data.setVerifiedBy(administrator);
        data.setVerificationNotificationPending(notify && !canNotifyNow);
        if (!plugin.getDatabaseManager().save(data)) {
            data.setVerified(false);
            data.setVerifiedBy(previousAdministrator);
            data.setVerificationNotificationPending(previousPending);
            return false;
        }

        // Интеграция с LuckPerms
        plugin.getLuckPermsHook().addVerification(data.getUuid());

        // Оповещение игрока, если он онлайн
        if (canNotifyNow && notify) {
            String msg = plugin.getConfig().getString("messages.manual-verified", "<green>Ваша заявка подтверждена!</green>");
            sendVerificationMessage(onlinePlayer, msg, administrator);
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

        String previousAdministrator = data.getVerifiedBy();
        boolean previousPending = data.isVerificationNotificationPending();
        data.setVerified(false);
        data.setVerifiedBy(null);
        data.setVerificationNotificationPending(false);
        if (!plugin.getDatabaseManager().save(data)) {
            data.setVerified(true);
            data.setVerifiedBy(previousAdministrator);
            data.setVerificationNotificationPending(previousPending);
            return false;
        }

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

    public void sendPendingNotification(Player player) {
        if (!player.isOnline() || !plugin.getAuthMeHook().isAuthenticated(player)) return;
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        if (!data.isVerified() || !data.isVerificationNotificationPending()) return;

        String message = plugin.getConfig().getString("messages.offline-verified",
                "<green>Пока вы были офлайн, администратор <admin> выдал вам верификацию. " +
                        "Ограничения на спавне сняты!</green>");
        sendVerificationMessage(player, message, data.getVerifiedBy());
        data.setVerificationNotificationPending(false);
        plugin.getDatabaseManager().save(data);
    }

    private void sendVerificationMessage(Player player, String message, String administrator) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                message.replace("<prefix>", plugin.getConfig().getString("messages.prefix", "")),
                Placeholder.unparsed("admin", administrator == null ? "Администратор" : administrator)));
    }

    public void triggerAutoVerify(Player player, PlayerData data) {
        data.setVerified(true);
        data.setVerifiedBy(null);
        data.setVerificationNotificationPending(false);
        plugin.getDatabaseManager().save(data);
        plugin.getLuckPermsHook().addVerification(player.getUniqueId());

        String msg = plugin.getConfig().getString("messages.auto-verified", "<green>Поздравляем с авто-верификацией!</green>");
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }
}
