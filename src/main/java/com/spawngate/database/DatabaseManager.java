package com.spawngate.database;

import com.spawngate.SpawnGatePlugin;
import com.spawngate.data.PlayerData;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DatabaseManager {

    private final SpawnGatePlugin plugin;
    private final File dbFile;
    private Connection connection;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DatabaseManager(SpawnGatePlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
    }

    public void init() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "username VARCHAR(32), " +
                        "rules_completed INTEGER DEFAULT 0, " +
                        "current_rule_page INTEGER DEFAULT 1, " +
                        "verified INTEGER DEFAULT 0, " +
                        "playtime_seconds INTEGER DEFAULT 0, " +
                        "last_seen INTEGER" +
                        ");");
            }
            plugin.getLogger().info("База данных SQLite успешно инициализирована.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при подключении к SQLite базе данных: " + e.getMessage(), e);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }

    public PlayerData getOrLoad(UUID uuid, String username) {
        if (cache.containsKey(uuid)) {
            PlayerData cached = cache.get(uuid);
            if (username != null && !username.equalsIgnoreCase(cached.getUsername())) {
                cached.setUsername(username);
            }
            return cached;
        }

        try {
            Connection conn = getConnection();
            String selectSql = "SELECT * FROM players WHERE uuid = ?;";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerData data = new PlayerData(
                                uuid,
                                rs.getString("username"),
                                rs.getInt("rules_completed") == 1,
                                rs.getInt("current_rule_page"),
                                rs.getInt("verified") == 1,
                                rs.getLong("playtime_seconds")
                        );
                        cache.put(uuid, data);
                        return data;
                    }
                }
            }

            // Новый игрок
            PlayerData newData = new PlayerData(uuid, username, false, 1, false, 0);
            String insertSql = "INSERT INTO players (uuid, username, rules_completed, current_rule_page, verified, playtime_seconds, last_seen) " +
                    "VALUES (?, ?, 0, 1, 0, 0, ?);";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }

            cache.put(uuid, newData);
            return newData;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при загрузке данных игрока " + username + ": " + e.getMessage(), e);
            PlayerData fallback = new PlayerData(uuid, username, false, 1, false, 0);
            cache.put(uuid, fallback);
            return fallback;
        }
    }

    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public void save(PlayerData data) {
        if (data == null) return;

        try {
            Connection conn = getConnection();
            String sql = "UPDATE players SET username = ?, rules_completed = ?, current_rule_page = ?, " +
                    "verified = ?, playtime_seconds = ?, last_seen = ? WHERE uuid = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getUsername());
                ps.setInt(2, data.isRulesCompleted() ? 1 : 0);
                ps.setInt(3, data.getCurrentRulePage());
                ps.setInt(4, data.isVerified() ? 1 : 0);
                ps.setLong(5, data.getPlaytimeSeconds());
                ps.setLong(6, System.currentTimeMillis());
                ps.setString(7, data.getUuid().toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения игрока " + data.getUsername() + ": " + e.getMessage(), e);
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    /**
     * Поиск игрока в базе по нику (даже если он оффлайн)
     */
    public PlayerData findByUsername(String username) {
        // Проверяем онлайн кэш
        for (PlayerData data : cache.values()) {
            if (data.getUsername().equalsIgnoreCase(username)) {
                return data;
            }
        }

        try {
            Connection conn = getConnection();
            String sql = "SELECT * FROM players WHERE LOWER(username) = LOWER(?);";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        return new PlayerData(
                                uuid,
                                rs.getString("username"),
                                rs.getInt("rules_completed") == 1,
                                rs.getInt("current_rule_page"),
                                rs.getInt("verified") == 1,
                                rs.getLong("playtime_seconds")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка поиска игрока по нику " + username + ": " + e.getMessage(), e);
        }

        return null;
    }

    public void close() {
        try {
            saveAll();
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка закрытия соединения SQLite: " + e.getMessage(), e);
        }
    }
}
