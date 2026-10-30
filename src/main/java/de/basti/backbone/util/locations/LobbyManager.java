package de.basti.backbone.util.locations;

import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Verwaltet den Server-Lobby-Spawnpunkt und die Beitritts-Teleportationsflag in der config.yml.
 */
public class LobbyManager {

    private static final String KEY_LOC = "lobby.location";
    private static final String KEY_TELEPORT = "lobby.teleport-on-join";

    public static boolean hasLobby() {
        return DataManager.contains("", "config.yml", KEY_LOC + ".world");
    }

    public static Location getLobbyLocation() {
        if (!hasLobby()) {
            return null;
        }

        String worldName = DataManager.getString("", "config.yml", KEY_LOC + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = DataManager.getDouble("", "config.yml", KEY_LOC + ".x");
        double y = DataManager.getDouble("", "config.yml", KEY_LOC + ".y");
        double z = DataManager.getDouble("", "config.yml", KEY_LOC + ".z");
        float yaw = (float) DataManager.getDouble("", "config.yml", KEY_LOC + ".yaw");
        float pitch = (float) DataManager.getDouble("", "config.yml", KEY_LOC + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public static void setLobby(Location loc, Boolean teleportOnJoin) {
        if (loc == null || loc.getWorld() == null) return;

        DataManager.setConfig(KEY_LOC + ".world", loc.getWorld().getName());
        DataManager.setConfig(KEY_LOC + ".x", loc.getX());
        DataManager.setConfig(KEY_LOC + ".y", loc.getY());
        DataManager.setConfig(KEY_LOC + ".z", loc.getZ());
        DataManager.setConfig(KEY_LOC + ".yaw", (double) loc.getYaw());
        DataManager.setConfig(KEY_LOC + ".pitch", (double) loc.getPitch());

        if (teleportOnJoin != null) {
            DataManager.setConfig(KEY_TELEPORT, teleportOnJoin);
        }
    }

    public static boolean isTeleportOnJoin() {
        return DataManager.getBoolean("", "config.yml", KEY_TELEPORT);
    }

    public static void setTeleportOnJoin(boolean enable) {
        DataManager.setConfig(KEY_TELEPORT, enable);
    }
}
