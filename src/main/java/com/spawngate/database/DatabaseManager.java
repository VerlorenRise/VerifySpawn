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
import java.util.HashSet;
import java.util.Set;
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
            // Обновляем существующую базу без удаления данных игроков.
            Set<String> columns = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("PRAGMA table_info(players)")) {
                while (rs.next()) columns.add(rs.getString("name"));
            }
            try (Statement statement = connection.createStatement()) {
                if (!columns.contains("verified_by")) {
                    statement.execute("ALTER TABLE players ADD COLUMN verified_by TEXT");
                }
                if (!columns.contains("verification_notification_pending")) {
                    statement.execute("ALTER TABLE players ADD COLUMN verification_notification_pending INTEGER NOT NULL DEFAULT 0");
                }
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

    public synchronized PlayerData getOrLoad(UUID uuid, String username) {
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
                        PlayerData data = readPlayer(rs);
                        if (username != null) data.setUsername(username);
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

    public synchronized boolean save(PlayerData data) {
        if (data == null) return false;

        try {
            Connection conn = getConnection();
            String sql = "UPDATE players SET username = ?, rules_completed = ?, current_rule_page = ?, " +
                    "verified = ?, playtime_seconds = ?, last_seen = ?, verified_by = ?, " +
                    "verification_notification_pending = ? WHERE uuid = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getUsername());
                ps.setInt(2, data.isRulesCompleted() ? 1 : 0);
                ps.setInt(3, data.getCurrentRulePage());
                ps.setInt(4, data.isVerified() ? 1 : 0);
                ps.setLong(5, data.getPlaytimeSeconds());
                ps.setLong(6, System.currentTimeMillis());
                ps.setString(7, data.getVerifiedBy());
                ps.setInt(8, data.isVerificationNotificationPending() ? 1 : 0);
                ps.setString(9, data.getUuid().toString());
                return ps.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения игрока " + data.getUsername() + ": " + e.getMessage(), e);
        }
        return false;
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    public synchronized void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    /**
     * Поиск игрока в базе по нику (даже если он оффлайн)
     */
    public synchronized PlayerData findByUsername(String username) {
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
                        PlayerData data = readPlayer(rs);
                        // Не создаём вторую копию данных подключённого игрока.
                        PlayerData cached = cache.get(data.getUuid());
                        return cached != null ? cached : data;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка поиска игрока по нику " + username + ": " + e.getMessage(), e);
        }

        return null;
    }

    private PlayerData readPlayer(ResultSet rs) throws SQLException {
        PlayerData data = new PlayerData(
                UUID.fromString(rs.getString("uuid")), rs.getString("username"),
                rs.getInt("rules_completed") == 1, rs.getInt("current_rule_page"),
                rs.getInt("verified") == 1, rs.getLong("playtime_seconds"));
        data.setVerifiedBy(rs.getString("verified_by"));
        data.setVerificationNotificationPending(rs.getInt("verification_notification_pending") == 1);
        return data;
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
