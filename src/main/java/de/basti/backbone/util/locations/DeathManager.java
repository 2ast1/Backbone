package de.basti.backbone.util.locations;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet die letzten Todesorte von Spielern für den /back Befehl.
 */
public final class DeathManager {

    private static final Map<UUID, Location> lastDeathLocations = new ConcurrentHashMap<>();

    private DeathManager() {}

    /**
     * Speichert den letzten Todesort eines Spielers.
     */
    public static void setLastDeathLocation(UUID uuid, Location location) {
        if (uuid != null && location != null) {
            lastDeathLocations.put(uuid, location.clone());
        }
    }

    /**
     * Gibt den letzten bekannten Todesort des Spielers zurück.
     * Nutzt als Fallback player.getLastDeathLocation().
     */
    public static Location getLastDeathLocation(Player player) {
        if (player == null) return null;
        Location loc = lastDeathLocations.get(player.getUniqueId());
        if (loc != null) {
            return loc;
        }
        try {
            return player.getLastDeathLocation();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void clear() {
        lastDeathLocations.clear();
    }
}
