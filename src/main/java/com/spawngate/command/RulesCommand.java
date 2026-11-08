package com.spawngate.command;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RulesCommand implements CommandExecutor {

    private final SpawnGatePlugin plugin;

    public RulesCommand(SpawnGatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());

        if (args.length == 0) {
            // Открываем текущую страницу
            int page = data.isRulesCompleted() ? 1 : data.getCurrentRulePage();
            plugin.getRulesManager().sendPage(player, page);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "next" -> plugin.getRulesManager().handleNext(player);
            case "prev" -> plugin.getRulesManager().handlePrev(player);
            case "accept" -> plugin.getRulesManager().handleAccept(player);
            case "page" -> {
                if (args.length > 1) {
                    try {
                        int pageNum = Integer.parseInt(args[1]);
                        // Если игрок еще читает правила, он не может прыгать вперед дальше своего прогресса
                        if (!data.isRulesCompleted() && pageNum > data.getCurrentRulePage()) {
                            pageNum = data.getCurrentRulePage();
                        }
                        plugin.getRulesManager().sendPage(player, pageNum);
                    } catch (NumberFormatException ignored) {
                        plugin.getRulesManager().sendPage(player, 1);
                    }
                }
            }
            default -> plugin.getRulesManager().sendPage(player, data.getCurrentRulePage());
        }

        return true;
    }
}
