package com.spawngate;

import com.spawngate.manager.ProtectionManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnProtectionTest {
    @Test void squareIncludesCornersAndExcludesBlocksBeyond200() {
        SpawnGatePlugin plugin = mock(SpawnGatePlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        ProtectionManager protection = new ProtectionManager(plugin);
        World world = mock(World.class);
        for (int x : new int[]{-200, 0, 200}) {
            for (int z : new int[]{-200, 0, 200}) {
                assertTrue(protection.isInSpawnZone(new Location(world, x, 1000, z)));
            }
        }
        for (int edge : new int[]{-201, 201}) {
            assertFalse(protection.isInSpawnZone(new Location(world, edge, 64, 0)));
            assertFalse(protection.isInSpawnZone(new Location(world, 0, 64, edge)));
        }
    }

    @Test void oldConfigUpgradesOnceAndRetainsCustomSettings() throws Exception {
        SpawnGatePlugin plugin = mock(SpawnGatePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        config.set("spawn.radius", 300);
        config.set("spawn.center-x", 50);
        config.set("verification.auto-verify-hours", 12);
        when(plugin.getConfig()).thenReturn(config);
        var upgrade = SpawnGatePlugin.class.getDeclaredMethod("upgradeConfig");
        upgrade.setAccessible(true);
        upgrade.invoke(plugin);
        assertEquals(200, config.getInt("spawn.radius"));
        assertEquals(50, config.getInt("spawn.center-x"));
        assertEquals(12, config.getInt("verification.auto-verify-hours"));
        config.set("spawn.radius", 300);
        upgrade.invoke(plugin);
        assertEquals(300, config.getInt("spawn.radius"));
        verify(plugin, times(1)).saveConfig();
    }

    @Test void customRadiusSurvivesUpgrade() throws Exception {
        SpawnGatePlugin plugin = mock(SpawnGatePlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn.radius", 150);
        when(plugin.getConfig()).thenReturn(config);
        var upgrade = SpawnGatePlugin.class.getDeclaredMethod("upgradeConfig");
        upgrade.setAccessible(true);
        upgrade.invoke(plugin);
        assertEquals(150, config.getInt("spawn.radius"));
    }
}
