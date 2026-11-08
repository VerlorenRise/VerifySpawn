package com.spawngate.manager;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RulesManager {

    private final SpawnGatePlugin plugin;
    private final File rulesFile;
    private FileConfiguration rulesConfig;

    private final Map<Integer, RulePage> pages = new HashMap<>();
    private final Set<UUID> frozenPlayers = Collections.synchronizedSet(new HashSet<>());
    private int maxPages = 8;

    public RulesManager(SpawnGatePlugin plugin) {
        this.plugin = plugin;
        this.rulesFile = new File(plugin.getDataFolder(), "rules.yml");
    }

    public void load() {
        if (!rulesFile.exists()) {
            plugin.saveResource("rules.yml", false);
        }
        rulesConfig = YamlConfiguration.loadConfiguration(rulesFile);
        pages.clear();

        if (rulesConfig.isConfigurationSection("pages")) {
            for (String key : rulesConfig.getConfigurationSection("pages").getKeys(false)) {
                try {
                    int pageNum = Integer.parseInt(key);
                    String title = rulesConfig.getString("pages." + key + ".title", "Правила - Страница " + key);
                    List<String> lines = rulesConfig.getStringList("pages." + key + ".lines");
                    pages.put(pageNum, new RulePage(pageNum, title, lines));
                } catch (NumberFormatException ignored) {}
            }
        }

        this.maxPages = pages.keySet().stream().max(Integer::compareTo).orElse(8);
        plugin.getLogger().info("Загружено " + pages.size() + " страниц правил из rules.yml.");
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public void freezePlayer(Player player) {
        frozenPlayers.add(player.getUniqueId());
        // Накладываем слепоту и бессмертие
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, -1, 0, false, false, false));
        player.setInvulnerable(true);
    }

    public void unfreezePlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setInvulnerable(false);
    }

    public void sendPage(Player player, int pageNum) {
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        if (pageNum < 1) pageNum = 1;
        if (pageNum > maxPages) pageNum = maxPages;

        RulePage page = pages.get(pageNum);
        if (page == null) {
            page = new RulePage(pageNum, "<red>Страница " + pageNum + "</red>", Collections.singletonList("<gray>Текст правил не настроен.</gray>"));
        }

        MiniMessage mm = MiniMessage.miniMessage();

        player.sendMessage(Component.empty());
        player.sendMessage(mm.deserialize("<gradient:#008a61:#057285><b>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</b></gradient>"));
        player.sendMessage(mm.deserialize(page.title));
        player.sendMessage(Component.empty());

        for (String line : page.lines) {
            player.sendMessage(mm.deserialize(line));
        }

        player.sendMessage(Component.empty());

        // Нижняя панель навигации
        StringBuilder navBuilder = new StringBuilder("<dark_gray>  ");

        // Кнопка [Назад]
        if (pageNum > 1) {
            navBuilder.append("<click:run_command:'/rules prev'><hover:show_text:'<gray>Вернуться на предыдущую страницу'><gradient:#057285:#008a61><b>[◀ Назад]</b></gradient></hover></click> ");
        } else {
            navBuilder.append("<dark_gray>[◀ Назад]</dark_gray> ");
        }

        navBuilder.append("  <gray>Стр. <color:#008a61><b>").append(pageNum).append("</b></color>/<color:#057285>").append(maxPages).append("</color></gray>   ");

        long timeRequired = plugin.getConfig().getLong("verification.anti-skip-seconds-per-page", 20);
        long elapsed = data.getSecondsOnCurrentPage();
        long remaining = Math.max(0, timeRequired - elapsed);

        // Кнопка [Вперёд] или [Завершить]
        if (pageNum < maxPages) {
            if (remaining > 0) {
                navBuilder.append("<click:run_command:'/rules next'><hover:show_text:'<red>Внимательно читайте! Осталось: <color:#008a61>").append(remaining).append(" сек.</color>'><red><b>[Вперёд ▶ (").append(remaining).append("с)]</b></red></hover></click>");
            } else {
                navBuilder.append("<click:run_command:'/rules next'><hover:show_text:'<green>Перейти к следующей странице правил'><gradient:#008a61:#057285><b>[Вперёд ▶]</b></gradient></hover></click>");
            }
        } else {
            // На последней странице кулдаун полностью снят - кнопка активна сразу!
            navBuilder.append("<click:run_command:'/rules accept'><hover:show_text:'<green>Нажмите, чтобы подтвердить прочтение правил!'><gradient:#008a61:#057285><b>[✔ Я ПРОЧИТАЛ И СОГЛАСЕН]</b></gradient></hover></click>");
        }

        navBuilder.append("</dark_gray>");
        player.sendMessage(mm.deserialize(navBuilder.toString()));
        player.sendMessage(mm.deserialize("<gradient:#008a61:#057285><b>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</b></gradient>"));
        player.sendMessage(Component.empty());
    }

    public void handleNext(Player player) {
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        long timeRequired = plugin.getConfig().getLong("verification.anti-skip-seconds-per-page", 10);
        long elapsed = data.getSecondsOnCurrentPage();

        if (elapsed < timeRequired) {
            long remaining = timeRequired - elapsed;
            String msg = plugin.getConfig().getString("messages.rules-timer-wait", "<red>Осталось: <seconds> сек.")
                    .replace("<prefix>", plugin.getConfig().getString("messages.prefix", ""))
                    .replace("<seconds>", String.valueOf(remaining));
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            return;
        }

        int next = data.getCurrentRulePage() + 1;
        if (next > maxPages) {
            next = maxPages;
        }
        data.setCurrentRulePage(next);
        plugin.getDatabaseManager().save(data);
        sendPage(player, next);
    }

    public void handlePrev(Player player) {
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());
        int prev = data.getCurrentRulePage() - 1;
        if (prev < 1) prev = 1;
        data.setCurrentRulePage(prev);
        plugin.getDatabaseManager().save(data);
        sendPage(player, prev);
    }

    public void handleAccept(Player player) {
        PlayerData data = plugin.getDatabaseManager().getOrLoad(player.getUniqueId(), player.getName());

        if (data.getCurrentRulePage() < maxPages) {
            sendPage(player, data.getCurrentRulePage());
            return;
        }

        // На последней странице кулдауна нет - сразу завершаем чтение правил!
        data.setRulesCompleted(true);
        plugin.getDatabaseManager().save(data);
        unfreezePlayer(player);

        String discordUrl = plugin.getConfig().getString("verification.discord-url", "https://discord.gg/CAf7PbhZBW");
        String completedMsg = plugin.getConfig().getString("messages.rules-completed", "<green>Правила прочитаны!</green>")
                .replace("<discord>", discordUrl);
        player.sendMessage(MiniMessage.miniMessage().deserialize(completedMsg));
    }

    public int getMaxPages() {
        return maxPages;
    }

    private static class RulePage {
        final int number;
        final String title;
        final List<String> lines;

        RulePage(int number, String title, List<String> lines) {
            this.number = number;
            this.title = title;
            this.lines = lines != null ? lines : new ArrayList<>();
        }
    }
}
