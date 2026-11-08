package com.spawngate.command;

import com.spawngate.SpawnGatePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VerifyCommand implements CommandExecutor, TabCompleter {

    private final SpawnGatePlugin plugin;
    private final boolean isVerifyAction;

    public VerifyCommand(SpawnGatePlugin plugin, boolean isVerifyAction) {
        this.plugin = plugin;
        this.isVerifyAction = isVerifyAction;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("spawngate.admin")) {
            send(sender, plugin.getConfig().getString("messages.admin.no-permission", "<red>Нет прав!</red>"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cИспользование: /" + label + " <ник_игрока>");
            return true;
        }

        String targetName = args[0];

        if (isVerifyAction) {
            boolean success = plugin.getVerificationManager().verify(targetName, true);
            if (success) {
                String msg = plugin.getConfig().getString("messages.admin.verify-success", "<green>Игрок <player> верифицирован!</green>")
                        .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""))
                        .replace("<player>", targetName);
                send(sender, msg);
            } else {
                String msg = plugin.getConfig().getString("messages.admin.already-verified", "<red>Игрок <player> уже верифицирован или не найден!</red>")
                        .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""))
                        .replace("<player>", targetName);
                send(sender, msg);
            }
        } else {
            boolean success = plugin.getVerificationManager().unverify(targetName, true);
            if (success) {
                String msg = plugin.getConfig().getString("messages.admin.unverify-success", "<yellow>Верификация у <player> снята.</yellow>")
                        .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""))
                        .replace("<player>", targetName);
                send(sender, msg);
            } else {
                String msg = plugin.getConfig().getString("messages.admin.not-verified", "<red>Игрок <player> не был верифицирован или не найден!</red>")
                        .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""))
                        .replace("<player>", targetName);
                send(sender, msg);
            }
        }

        return true;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
