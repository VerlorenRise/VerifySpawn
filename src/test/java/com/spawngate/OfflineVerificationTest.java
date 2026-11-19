package com.spawngate;

import com.spawngate.data.PlayerData;
import com.spawngate.database.DatabaseManager;
import com.spawngate.hook.AuthMeHook;
import com.spawngate.hook.LuckPermsHook;
import com.spawngate.manager.VerificationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfflineVerificationTest {
    @TempDir Path folder;
    SpawnGatePlugin plugin;
    DatabaseManager database;
    VerificationManager manager;
    AuthMeHook auth;
    LuckPermsHook permissions;
    MockedStatic<Bukkit> bukkit;

    @BeforeEach void setUp() {
        plugin = mock(SpawnGatePlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SpawnGateTest"));
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        auth = mock(AuthMeHook.class);
        when(auth.isAuthenticated(any())).thenReturn(true);
        when(plugin.getAuthMeHook()).thenReturn(auth);
        permissions = mock(LuckPermsHook.class);
        when(plugin.getLuckPermsHook()).thenReturn(permissions);
        openDatabase();
        manager = new VerificationManager(plugin);
        bukkit = mockStatic(Bukkit.class);
    }

    void openDatabase() {
        database = new DatabaseManager(plugin);
        database.init();
        when(plugin.getDatabaseManager()).thenReturn(database);
    }

    PlayerData knownPlayer() {
        PlayerData data = database.getOrLoad(UUID.randomUUID(), "Steve");
        data.setRulesCompleted(true);
        data.setPlaytimeSeconds(1234);
        database.unload(data.getUuid());
        return data;
    }

    Player connect(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        return player;
    }

    @AfterEach void tearDown() {
        bukkit.close();
        database.close();
    }

    @Test void offlineGrantSurvivesRestartAndNotifiesExactlyOnceAfterAuthentication() {
        PlayerData original = knownPlayer();
        assertTrue(manager.verify("sTeVe", true, "AdminName"));
        verify(permissions).addVerification(original.getUuid());
        assertFalse(manager.verify("Steve", true, "OtherAdmin"));

        database.close();
        openDatabase();
        PlayerData restored = database.findByUsername("STEVE");
        assertTrue(restored.isVerified());
        assertTrue(restored.isRulesCompleted());
        assertEquals(1234, restored.getPlaytimeSeconds());
        assertEquals("AdminName", restored.getVerifiedBy());
        assertTrue(restored.isVerificationNotificationPending());

        Player player = connect(original.getUuid());
        when(auth.isAuthenticated(player)).thenReturn(false);
        manager.sendPendingNotification(player);
        verify(player, never()).sendMessage(any(Component.class));
        assertTrue(database.getCached(original.getUuid()) == null
                || database.getCached(original.getUuid()).isVerificationNotificationPending());

        when(auth.isAuthenticated(player)).thenReturn(true);
        manager.sendPendingNotification(player);
        manager.sendPendingNotification(player);
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(player, times(1)).sendMessage(message.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(message.getValue());
        assertTrue(text.contains("AdminName"));
        assertTrue(text.contains("офлайн"));

        database.close();
        openDatabase();
        assertFalse(database.findByUsername("Steve").isVerificationNotificationPending());
        manager.sendPendingNotification(player);
        verify(player, times(1)).sendMessage(any(Component.class));
    }

    @Test void revokingOfflineGrantCancelsNoticeAcrossRestart() {
        PlayerData original = knownPlayer();
        assertTrue(manager.verify("Steve", true, "AdminName"));
        assertTrue(manager.unverify("steve", true));
        verify(permissions).removeVerification(original.getUuid());
        database.close();
        openDatabase();
        PlayerData restored = database.findByUsername("Steve");
        assertFalse(restored.isVerified());
        assertFalse(restored.isVerificationNotificationPending());
        Player player = connect(original.getUuid());
        manager.sendPendingNotification(player);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test void unknownNicknameDoesNotCreatePlayerOrGrantPermissions() {
        assertFalse(manager.verify("NeverJoined", true, "AdminName"));
        assertNull(database.findByUsername("NeverJoined"));
        verifyNoInteractions(permissions);
    }

    @Test void onlineGrantNotifiesImmediatelyAndQueuesNothing() {
        PlayerData data = knownPlayer();
        Player player = connect(data.getUuid());
        plugin.getConfig().set("messages.manual-verified", "<green>Выдал <admin></green>");
        assertTrue(manager.verify("Steve", true, "AdminName"));
        verify(player).sendMessage(Component.text("Выдал AdminName").color(
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
        assertFalse(database.findByUsername("Steve").isVerificationNotificationPending());
    }

    @Test void grantBeforeLoginWaitsForAuthentication() {
        PlayerData data = knownPlayer();
        Player player = connect(data.getUuid());
        when(auth.isAuthenticated(player)).thenReturn(false);
        assertTrue(manager.verify("Steve", true, "Консоль"));
        assertTrue(database.findByUsername("Steve").isVerificationNotificationPending());
        verify(player, never()).sendMessage(any(Component.class));
        when(auth.isAuthenticated(player)).thenReturn(true);
        manager.sendPendingNotification(player);
        verify(player).sendMessage(any(Component.class));
    }

    @Test void oldDatabaseMigratesWithoutLosingProgressAndCanReopen() throws Exception {
        database.close();
        UUID uuid = UUID.randomUUID();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("database.db"));
             var sql = conn.createStatement()) {
            sql.execute("DROP TABLE players");
            sql.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, username TEXT, rules_completed INTEGER, "
                    + "current_rule_page INTEGER, verified INTEGER, playtime_seconds INTEGER, last_seen INTEGER)");
            sql.execute("INSERT INTO players VALUES ('" + uuid + "', 'Steve', 1, 7, 0, 9876, 0)");
        }
        openDatabase();
        PlayerData restored = database.findByUsername("Steve");
        assertTrue(restored.isRulesCompleted());
        assertEquals(7, restored.getCurrentRulePage());
        assertEquals(9876, restored.getPlaytimeSeconds());
        assertFalse(restored.isVerificationNotificationPending());
        assertTrue(manager.verify("Steve", true, "AdminName"));
        database.close();
        openDatabase();
        assertEquals("AdminName", database.findByUsername("Steve").getVerifiedBy());
    }

    @Test void failedSaveDoesNotGrantPermissionsOrReportSuccess() {
        knownPlayer();
        DatabaseManager failing = spy(database);
        doReturn(false).when(failing).save(any());
        when(plugin.getDatabaseManager()).thenReturn(failing);
        assertFalse(manager.verify("Steve", true, "AdminName"));
        verifyNoInteractions(permissions);
        assertFalse(database.findByUsername("Steve").isVerified());
    }
}
