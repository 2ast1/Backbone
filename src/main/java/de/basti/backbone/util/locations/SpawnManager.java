package de.basti.backbone.util.locations;

import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Verwaltet Spawnpunkte der Welten in locations/spawns.yml.
 * Unterstützt die Flag spawn-on-join-world zum automatischen Teleportieren beim Betreten der Welt.
 */
public class SpawnManager {

    private static final String FOLDER = "locations";
    private static final String FILE = "spawns.yml";

    public static boolean hasWorldSpawn(World world) {
        if (world == null) return false;
        return DataManager.contains(FOLDER, FILE, world.getName());
    }

    public static Location getWorldSpawn(World world) {
        if (world == null) return null;
        String key = world.getName();

        if (!hasWorldSpawn(world)) {
            // Fallback auf den Vanilla Spawnpunkt der Welt
            return world.getSpawnLocation();
        }

        double x = DataManager.getDouble(FOLDER, FILE, key + ".x");
        double y = DataManager.getDouble(FOLDER, FILE, key + ".y");
        double z = DataManager.getDouble(FOLDER, FILE, key + ".z");
        float yaw = (float) DataManager.getDouble(FOLDER, FILE, key + ".yaw");
        float pitch = (float) DataManager.getDouble(FOLDER, FILE, key + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static void setWorldSpawn(Location loc, Boolean spawnOnJoin) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        String key = world.getName();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(key + ".x", loc.getX());
        cfg.set(key + ".y", loc.getY());
        cfg.set(key + ".z", loc.getZ());
        cfg.set(key + ".yaw", (double) loc.getYaw());
        cfg.set(key + ".pitch", (double) loc.getPitch());

        if (spawnOnJoin != null) {
            cfg.set(key + ".spawn-on-join-world", spawnOnJoin);
        } else if (!cfg.contains(key + ".spawn-on-join-world")) {
            cfg.set(key + ".spawn-on-join-world", false);
        }

        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        // Auch Vanilla World-Spawn aktualisieren
        world.setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static boolean isSpawnOnJoin(World world) {
        if (world == null) return false;
        return DataManager.getBoolean(FOLDER, FILE, world.getName() + ".spawn-on-join-world");
    }

    public static void setSpawnOnJoin(World world, boolean enable) {
        if (world == null) return;
        String key = world.getName();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(key + ".spawn-on-join-world", enable);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);
    }
}
