package de.basti.backbone.ranks;

import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages per-player individual permissions (both global and world-specific).
 * Stored in 'playerdata.yml' under:
 *   <uuid>.permissions: [perm1, perm2]
 *   <uuid>.world-permissions.<world>: [permA, permB]
 */
public final class PlayerPermissionManager {

    private static final String FOLDER = "";
    private static final String FILE = "playerdata.yml";

    private PlayerPermissionManager() {}

    /**
     * Returns an unmodifiable list of global individual permissions for the player.
     */
    public static List<String> getGlobalPermissions(UUID uuid) {
        if (uuid == null) return Collections.emptyList();
        List<String> list = DataManager.getStringList(FOLDER, FILE, uuid + ".permissions");
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * Returns a map of world -> permissions for all world-specific individual permissions of the player.
     */
    public static Map<String, List<String>> getWorldPermissions(UUID uuid) {
        if (uuid == null) return Collections.emptyMap();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        String basePath = uuid + ".world-permissions";
        if (!cfg.contains(basePath) || !cfg.isConfigurationSection(basePath)) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> map = new LinkedHashMap<>();
        var sec = cfg.getConfigurationSection(basePath);
        if (sec != null) {
            for (String worldKey : sec.getKeys(false)) {
                List<String> perms = cfg.getStringList(basePath + "." + worldKey);
                map.put(worldKey.toLowerCase(Locale.ROOT), Collections.unmodifiableList(new ArrayList<>(perms)));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the individual permissions configured for a specific world.
     */
    public static List<String> getPermissionsForWorld(UUID uuid, String world) {
        if (uuid == null || world == null || world.isBlank()) return Collections.emptyList();
        String path = uuid + ".world-permissions." + world.trim().toLowerCase(Locale.ROOT);
        List<String> list = DataManager.getStringList(FOLDER, FILE, path);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * Adds an individual permission to a player.
     * If worlds is null or empty, the permission is added globally.
     * Otherwise, it is added to the specified worlds.
     *
     * @return true if added to at least one scope, false if it already existed everywhere.
     */
    public static synchronized boolean addPermission(UUID uuid, String permission, Collection<String> worlds) {
        if (uuid == null || permission == null || permission.isBlank()) return false;
        String cleanPerm = permission.trim();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        boolean modified = false;

        if (worlds == null || worlds.isEmpty()) {
            List<String> current = new ArrayList<>(getGlobalPermissions(uuid));
            if (!containsIgnoreCase(current, cleanPerm)) {
                current.add(cleanPerm);
                cfg.set(uuid + ".permissions", current);
                modified = true;
            }
        } else {
            for (String w : worlds) {
                if (w == null || w.isBlank()) continue;
                String worldKey = w.trim().toLowerCase(Locale.ROOT);
                String path = uuid + ".world-permissions." + worldKey;
                List<String> current = new ArrayList<>(cfg.getStringList(path));
                if (!containsIgnoreCase(current, cleanPerm)) {
                    current.add(cleanPerm);
                    cfg.set(path, current);
                    modified = true;
                }
            }
        }

        if (modified) {
            DataManager.saveConfiguration(FOLDER, FILE, cfg);
            syncPlayerIfOnline(uuid);
        }

        return modified;
    }

    /**
     * Removes an individual permission from a player.
     * If worlds is null or empty, it is removed globally.
     * Otherwise, it is removed from the specified worlds.
     *
     * @return true if removed from at least one scope, false if not found.
     */
    public static synchronized boolean removePermission(UUID uuid, String permission, Collection<String> worlds) {
        if (uuid == null || permission == null || permission.isBlank()) return false;
        String cleanPerm = permission.trim();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        boolean modified = false;

        if (worlds == null || worlds.isEmpty()) {
            List<String> current = new ArrayList<>(getGlobalPermissions(uuid));
            if (removeIgnoreCase(current, cleanPerm)) {
                cfg.set(uuid + ".permissions", current.isEmpty() ? null : current);
                modified = true;
            }
        } else {
            for (String w : worlds) {
                if (w == null || w.isBlank()) continue;
                String worldKey = w.trim().toLowerCase(Locale.ROOT);
                String path = uuid + ".world-permissions." + worldKey;
                List<String> current = new ArrayList<>(cfg.getStringList(path));
                if (removeIgnoreCase(current, cleanPerm)) {
                    if (current.isEmpty()) {
                        cfg.set(path, null);
                        // Clean up parent section if empty
                        String basePath = uuid + ".world-permissions";
                        var sec = cfg.getConfigurationSection(basePath);
                        if (sec != null && sec.getKeys(false).isEmpty()) {
                            cfg.set(basePath, null);
                        }
                    } else {
                        cfg.set(path, current);
                    }
                    modified = true;
                }
            }
        }

        if (modified) {
            DataManager.saveConfiguration(FOLDER, FILE, cfg);
            syncPlayerIfOnline(uuid);
        }

        return modified;
    }

    /**
     * Resets all individual permissions (both global and world-specific) of the player.
     */
    public static synchronized void resetPermissions(UUID uuid) {
        if (uuid == null) return;
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(uuid + ".permissions", null);
        cfg.set(uuid + ".world-permissions", null);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        syncPlayerIfOnline(uuid);
    }

    /**
     * Resets all individual permissions AND removes the rank assignment of the player
     * (reverting them back to the default rank).
     */
    public static synchronized void resetAll(UUID uuid) {
        if (uuid == null) return;
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(uuid + ".permissions", null);
        cfg.set(uuid + ".world-permissions", null);
        cfg.set(uuid + ".rank", null);
        cfg.set(uuid + ".rank_assigned_at", null);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);
        syncPlayerIfOnline(uuid);
        de.basti.backbone.util.PrefixManager.updateAllTabLists();
    }

    private static boolean containsIgnoreCase(List<String> list, String target) {
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeIgnoreCase(List<String> list, String target) {
        return list.removeIf(s -> s != null && s.equalsIgnoreCase(target));
    }

    private static void syncPlayerIfOnline(UUID uuid) {
        if (RankManager.getPermissionService() != null && RankManager.isRanksEnabled()) {
            RankManager.getPermissionService().updatePlayer(uuid);
        }
    }
}
