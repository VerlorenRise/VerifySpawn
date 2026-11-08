package com.spawngate.command;

import com.spawngate.SpawnGatePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SpawnGateCommand implements CommandExecutor, TabCompleter {

    private final SpawnGatePlugin plugin;

    public SpawnGateCommand(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("spawngate.admin")) {
            String noPerm = plugin.getConfig().getString("messages.admin.no-permission", "<red>Нет прав!</red>");
            sender.sendMessage(MiniMessage.miniMessage().deserialize(noPerm));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            String msg = plugin.getConfig().getString("messages.admin.reload-success", "<green>Конфигурация перезагружена!</green>")
                    .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""));
            sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return true;
        }

        sender.sendMessage("§e[SpawnGate] §fИспользуйте: §a/spawngate reload");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
