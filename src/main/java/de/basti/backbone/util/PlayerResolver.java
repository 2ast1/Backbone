package de.basti.backbone.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Utility-Klasse zur schnellen, lokalen Auflösung von Spielernamen und UUIDs.
 * Nutzt die 'playerdata.yml' als lokalen Cache, um blockierende Mojang-API-Anfragen
 * für Offline-Spieler zu vermeiden.
 */
public class PlayerResolver {

    private static final String FOLDER = "";
    private static final String FILE = "playerdata.yml";
    private static final java.util.regex.Pattern VALID_USERNAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_.]{1,16}$");

    /**
     * Prüft, ob ein String ein formal gültiger Minecraft-Spielername ist (1-16 Zeichen, keine Farbcodes/Leerzeichen).
     */
    public static boolean isValidUsername(String name) {
        return name != null && VALID_USERNAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Speichert den aktuellen Spielernamen zur UUID in playerdata.yml.
     *
     * @param player Der Online-Spieler
     */
    public static void recordPlayer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        DataManager.set(FOLDER, FILE, uuid + ".name", player.getName());
    }

    /**
     * Speichert einen Namen zu einer UUID (auch für Offline-Spieler, falls bekannt).
     */
    public static void recordPlayer(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;
        DataManager.set(FOLDER, FILE, uuid + ".name", name);
    }

    /**
     * Löst einen Spielernamen oder einen UUID-String zu einer UUID auf.
     * Reihenfolge der Prüfung:
     * 1. Direkter UUID-String
     * 2. Aktuell online befindlicher Spieler
     * 3. Lokale playerdata.yml (blitzschnell & lagfrei)
     * 4. Bukkit OfflinePlayer Fallback
     *
     * @param input Spielername oder UUID-String
     * @return Die ermittelte UUID oder null, falls der Spieler unbekannt ist
     */
    public static UUID resolveUuid(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = input.trim();

        // 1. UUID-String-Check
        try {
            return UUID.fromString(clean);
        } catch (IllegalArgumentException ignored) {}

        // Ungültige Zeichen (z.B. Farbcodes wie "&6H" oder Leerzeichen) sofort abweisen
        if (!isValidUsername(clean)) {
            return null;
        }

        // 2. Online-Spieler (exakter Treffer zuerst)
        if (Bukkit.getServer() != null) {
            Player online = Bukkit.getPlayerExact(clean);
            if (online != null) {
                return online.getUniqueId();
            }
        }

        // 3. Lokale playerdata.yml durchsuchen
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        for (String key : cfg.getKeys(false)) {
            String storedName = cfg.getString(key + ".name");
            if (storedName != null && storedName.equalsIgnoreCase(clean)) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 4. Nicht-blockierender Server-Cache (ohne synchronen Mojang-HTTP-Call!)
        if (Bukkit.getServer() != null) {
            try {
                java.lang.reflect.Method cachedMethod = Bukkit.getServer().getClass().getMethod("getOfflinePlayerIfCached", String.class);
                Object cachedObj = cachedMethod.invoke(Bukkit.getServer(), clean);
                if (cachedObj instanceof OfflinePlayer op && (op.hasPlayedBefore() || op.isOnline() || op.getName() != null)) {
                    return op.getUniqueId();
                }
            } catch (Throwable ignored) {
                try {
                    for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                        if (op != null && op.getName() != null && op.getName().equalsIgnoreCase(clean)) {
                            return op.getUniqueId();
                        }
                    }
                } catch (Throwable ignored2) {}
            }

            Player onlineApprox = Bukkit.getPlayer(clean);
            if (onlineApprox != null) {
                return onlineApprox.getUniqueId();
            }
        }

        return null;
    }

    /**
     * Ermittelt den Spielernamen für eine UUID.
     *
     * @param uuid UUID des Spielers
     * @return Der Name des Spielers oder die UUID als String
     */
    public static String getPlayerName(UUID uuid) {
        if (uuid == null) return Lang.get("common.unknown");

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        String stored = DataManager.getString(FOLDER, FILE, uuid + ".name");
        if (stored != null && !stored.isBlank()) {
            return stored;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null && !op.getName().isBlank()) {
            return op.getName();
        }

        return uuid.toString();
    }

    /**
     * Liefert eine alphabetisch sortierte Liste aller bekannten Spielernamen
     * (Online-Spieler + in playerdata.yml hinterlegte Spieler) für Tab-Vervollständigungen.
     */
    public static List<String> getAllKnownPlayerNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        for (String key : cfg.getKeys(false)) {
            String name = cfg.getString(key + ".name");
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        return new ArrayList<>(names);
    }
}
