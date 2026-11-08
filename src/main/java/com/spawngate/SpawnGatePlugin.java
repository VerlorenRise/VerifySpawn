package com.spawngate;

import com.spawngate.command.RulesCommand;
import com.spawngate.command.SpawnGateCommand;
import com.spawngate.command.VerifyCommand;
import com.spawngate.database.DatabaseManager;
import com.spawngate.hook.AuthMeHook;
import com.spawngate.hook.LuckPermsHook;
import com.spawngate.listener.AuthMeListener;
import com.spawngate.listener.PlayerConnectionListener;
import com.spawngate.listener.RuleFreezeListener;
import com.spawngate.listener.SpawnProtectionListener;
import com.spawngate.manager.ProtectionManager;
import com.spawngate.manager.RulesManager;
import com.spawngate.manager.VerificationManager;
import com.spawngate.task.PlaytimeTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SpawnGatePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private AuthMeHook authMeHook;
    private LuckPermsHook luckPermsHook;
    private RulesManager rulesManager;
    private ProtectionManager protectionManager;
    private VerificationManager verificationManager;
    private PlaytimeTask playtimeTask;

    @Override
    public void onEnable() {
        // Загрузка конфигураций
        saveDefaultConfig();

        // Инициализация базы данных SQLite
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        // Инициализация хуков
        this.authMeHook = new AuthMeHook(this);
        this.luckPermsHook = new LuckPermsHook(this);

        // Инициализация менеджеров
        this.rulesManager = new RulesManager(this);
        this.rulesManager.load();

        this.protectionManager = new ProtectionManager(this);
        this.verificationManager = new VerificationManager(this);

        // Регистрация слушателей событий
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(this), this);
        pm.registerEvents(new RuleFreezeListener(this), this);
        pm.registerEvents(new SpawnProtectionListener(this), this);

        // Если есть AuthMe, регистрируем отдельный AuthMe-слушатель
        if (authMeHook.isEnabled()) {
            pm.registerEvents(new AuthMeListener(this), this);
        }

        // Регистрация команд
        VerifyCommand verifyCmd = new VerifyCommand(this, true);
        VerifyCommand unverifyCmd = new VerifyCommand(this, false);
        Objects.requireNonNull(getCommand("verify")).setExecutor(verifyCmd);
        Objects.requireNonNull(getCommand("verify")).setTabCompleter(verifyCmd);

        Objects.requireNonNull(getCommand("unverify")).setExecutor(unverifyCmd);
        Objects.requireNonNull(getCommand("unverify")).setTabCompleter(unverifyCmd);

        Objects.requireNonNull(getCommand("rules")).setExecutor(new RulesCommand(this));

        SpawnGateCommand sgCmd = new SpawnGateCommand(this);
        Objects.requireNonNull(getCommand("spawngate")).setExecutor(sgCmd);
        Objects.requireNonNull(getCommand("spawngate")).setTabCompleter(sgCmd);

        // Запуск задачи отслеживания наигранного времени (каждые 20 секунд = 400 тиков)
        this.playtimeTask = new PlaytimeTask(this, 20L);
        this.playtimeTask.runTaskTimerAsynchronously(this, 400L, 400L);

        getLogger().info("=========================================");
        getLogger().info(" SpawnGate v" + getDescription().getVersion() + " успешно запущен!");
        getLogger().info(" Сервер: " + getServer().getName() + " " + getServer().getVersion());
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }

        // Размораживаем всех игроков при выключении плагина во избежание зависания
        if (rulesManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (rulesManager.isFrozen(player.getUniqueId())) {
                    rulesManager.unfreezePlayer(player);
                }
            }
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("SpawnGate успешно выключен.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (rulesManager != null) {
            rulesManager.load();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuthMeHook getAuthMeHook() {
        return authMeHook;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public RulesManager getRulesManager() {
        return rulesManager;
    }

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }
}
