package de.basti.backbone.util.locations;

import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Verwaltet Spieler-Homes in locations/homes.yml sowie die Home-Sperrwelten in der config.yml.
 */
public class HomeManager {

    private static final String FOLDER = "locations";
    private static final String FILE = "homes.yml";
    private static final String KEY_BLACKLIST = "homes.disabled-worlds";

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
     * Schaltet eine Welt für Homes auf die Blacklist (hinzufügen/entfernen).
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

    public static int getMaxHomes() {
        if (!DataManager.getConfig().contains("homes.max-homes")) {
            return 5;
        }
        return DataManager.getConfig().getInt("homes.max-homes", 5);
    }

    public static boolean homeExists(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return false;
        String cleanName = sanitizeKey(name);
        if (cleanName.isEmpty()) return false;
        return DataManager.contains(FOLDER, FILE, uuid + "." + cleanName);
    }

    public static Location getHome(UUID uuid, String name) {
        if (!homeExists(uuid, name)) return null;
        String path = uuid + "." + sanitizeKey(name);

        String worldName = DataManager.getString(FOLDER, FILE, path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = DataManager.getDouble(FOLDER, FILE, path + ".x");
        double y = DataManager.getDouble(FOLDER, FILE, path + ".y");
        double z = DataManager.getDouble(FOLDER, FILE, path + ".z");
        float yaw = (float) DataManager.getDouble(FOLDER, FILE, path + ".yaw");
        float pitch = (float) DataManager.getDouble(FOLDER, FILE, path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static boolean setHome(UUID uuid, String name, Location loc) {
        if (uuid == null || name == null || name.isBlank() || loc == null || loc.getWorld() == null) return false;
        String cleanName = sanitizeKey(name);
        if (cleanName.isEmpty()) return false;
        String path = uuid + "." + cleanName;

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
        cfg.set(path + ".created-at", System.currentTimeMillis());

        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        return true;
    }

    public static boolean deleteHome(UUID uuid, String name) {
        if (!homeExists(uuid, name)) return false;
        String cleanName = sanitizeKey(name);

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(uuid + "." + cleanName, null);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        return true;
    }

    public static Set<String> getHomes(UUID uuid) {
        if (uuid == null || !DataManager.fileExists(FOLDER, FILE)) {
            return Collections.emptySet();
        }
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        ConfigurationSection section = cfg.getConfigurationSection(uuid.toString());
        if (section == null) {
            return Collections.emptySet();
        }
        return new TreeSet<>(section.getKeys(false));
    }
}
