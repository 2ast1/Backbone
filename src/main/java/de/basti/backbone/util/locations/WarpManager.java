package de.basti.backbone.util.locations;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Verwaltet Warps in locations/warps.yml sowie die Sperrwelten in der config.yml.
 */
public class WarpManager {

    private static final String FOLDER = "locations";
    private static final String FILE = "warps.yml";
    private static final String KEY_BLACKLIST = "warps.disabled-worlds";

    public static boolean isWorldBlacklisted(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        List<String> list = getBlacklistedWorlds();
        for (String w : list) {
            if (w.equalsIgnoreCase(worldName.trim())) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getBlacklistedWorlds() {
        return DataManager.getStringList("", "config.yml", KEY_BLACKLIST);
    }

    /**
     * Schaltet eine Welt auf die Blacklist (hinzufügen/entfernen).
     * @return true, wenn hinzugefügt; false, wenn entfernt.
     */
    public static boolean toggleWorldBlacklist(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        String clean = worldName.trim();
        List<String> list = new ArrayList<>(getBlacklistedWorlds());

        boolean removed = list.removeIf(w -> w.equalsIgnoreCase(clean));
        boolean added = false;
        if (!removed) {
            list.add(clean);
            added = true;
        }

        DataManager.setConfig(KEY_BLACKLIST, list);
        return added;
    }

    private static String sanitizeKey(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT)
                .replace(".", "_")
                .replace("/", "_")
                .replace("\\", "_");
    }

    public static boolean warpExists(String name) {
        if (name == null || name.isBlank()) return false;
        String key = sanitizeKey(name);
        if (key.isEmpty()) return false;
        return DataManager.contains(FOLDER, FILE, key);
    }

    public static Location getWarp(String name) {
        if (!warpExists(name)) return null;
        String key = sanitizeKey(name);

        String worldName = DataManager.getString(FOLDER, FILE, key + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = DataManager.getDouble(FOLDER, FILE, key + ".x");
        double y = DataManager.getDouble(FOLDER, FILE, key + ".y");
        double z = DataManager.getDouble(FOLDER, FILE, key + ".z");
        float yaw = (float) DataManager.getDouble(FOLDER, FILE, key + ".yaw");
        float pitch = (float) DataManager.getDouble(FOLDER, FILE, key + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static boolean setWarp(String name, Location loc, String creator) {
        if (name == null || name.isBlank() || loc == null || loc.getWorld() == null) return false;
        String key = sanitizeKey(name);
        if (key.isEmpty()) return false;

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(key + ".world", loc.getWorld().getName());
        cfg.set(key + ".x", loc.getX());
        cfg.set(key + ".y", loc.getY());
        cfg.set(key + ".z", loc.getZ());
        cfg.set(key + ".yaw", (double) loc.getYaw());
        cfg.set(key + ".pitch", (double) loc.getPitch());
        cfg.set(key + ".created-by", creator != null ? creator : Lang.getRaw("common.unknown"));
        cfg.set(key + ".created-at", System.currentTimeMillis());

        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        return true;
    }

    public static boolean deleteWarp(String name) {
        if (!warpExists(name)) return false;
        String key = sanitizeKey(name);

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(key, null);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        return true;
    }

    public static Set<String> getWarps() {
        if (!DataManager.fileExists(FOLDER, FILE)) {
            return Collections.emptySet();
        }
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        return new TreeSet<>(cfg.getKeys(false));
    }
}
