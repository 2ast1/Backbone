package de.basti.backbone.util.automod.punish;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.automod.message.AutoModMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility-Klasse zur Verwaltung von Spieler-Banns (Permanent und Temp-Bans).
 * Speichert alle Bann-Daten in der automod/playerdata.yml und protokolliert in der Historie.
 */
public class BanUtil {

    private static final String FOLDER = "automod";
    private static final String FILE = "playerdata.yml";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Pattern DURATION_FULL_PATTERN = Pattern.compile("^(?:\\d+[wdhms])+$");
    private static final Pattern DURATION_PART_PATTERN = Pattern.compile("(\\d+)([wdhms])");
    private static final long MAX_DURATION_MILLIS = 100L * 365 * 24 * 60 * 60 * 1000L; // 100 Jahre

    /**
     * Datenklasse für vollständige Bann-Informationen.
     */
    public record BanInfo(
            UUID playerUuid,
            boolean active,
            String reason,
            String bannedBy,
            long bannedAt,
            long until,
            boolean permanent
    ) {
        public String getFormattedBannedAt() {
            return DATE_FORMAT.format(Instant.ofEpochMilli(bannedAt));
        }

        public String getFormattedUntil() {
            if (permanent) return Lang.get("common.permanent");
            return DATE_FORMAT.format(Instant.ofEpochMilli(until));
        }

        public long getRemainingMillis() {
            if (!active) return 0;
            if (permanent) return -1;
            return Math.max(0, until - System.currentTimeMillis());
        }

        public String getFormattedRemainingTime() {
            if (!active) return Lang.get("punish.status.not-banned");
            if (permanent) return Lang.get("common.permanent");
            return formatDuration(getRemainingMillis());
        }
    }

    // =========================================================================
    // BANN SETZEN (PERMANENT & TEMP)
    // =========================================================================

    /**
     * Bannt einen Spieler permanent vom Server und kickt ihn sicher auf dem Hauptthread.
     *
     * @param player   Der zu bannende Spieler
     * @param reason   Grund für den Bann
     * @param bannedBy Ausführender (z.B. "AutoMod" oder Spielername)
     */
    public static void ban(Player player, String reason, String bannedBy) {
        if (player == null) return;
        ban(player.getUniqueId(), player.getName(), reason, bannedBy);
    }

    /**
     * Bannt eine UUID permanent vom Server.
     */
    public static void ban(UUID playerUuid, String reason, String bannedBy) {
        ban(playerUuid, null, reason, bannedBy);
    }

    /**
     * Bannt eine UUID permanent vom Server mit Spielernamen.
     */
    public static void ban(UUID playerUuid, String playerName, String reason, String bannedBy) {
        setBanData(playerUuid, playerName, reason, bannedBy, -1);
        HistoryUtil.addEntry(playerUuid, "BAN", reason, bannedBy, Lang.get("common.permanent"));

        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null && online.isOnline()) {
            kickPlayerSafe(online, getBanMessage(playerUuid, online.getName()));
        }
    }

    /**
     * Bannt einen Spieler temporär (Temp-Ban) und kickt ihn sicher auf dem Hauptthread.
     *
     * @param player         Der zu bannende Spieler
     * @param durationMillis Dauer des Banns in Millisekunden
     * @param reason         Grund für den Bann
     * @param bannedBy       Ausführender
     */
    public static void tempBan(Player player, long durationMillis, String reason, String bannedBy) {
        if (player == null) return;
        tempBan(player.getUniqueId(), player.getName(), durationMillis, reason, bannedBy);
    }

    /**
     * Bannt eine UUID temporär (Temp-Ban).
     */
    public static void tempBan(UUID playerUuid, long durationMillis, String reason, String bannedBy) {
        tempBan(playerUuid, null, durationMillis, reason, bannedBy);
    }

    /**
     * Bannt eine UUID temporär (Temp-Ban) mit Spielernamen.
     */
    public static void tempBan(UUID playerUuid, String playerName, long durationMillis, String reason, String bannedBy) {
        long until = System.currentTimeMillis() + Math.max(0, durationMillis);
        setBanData(playerUuid, playerName, reason, bannedBy, until);
        HistoryUtil.addEntry(playerUuid, "BAN", reason, bannedBy, formatDuration(durationMillis));

        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null && online.isOnline()) {
            kickPlayerSafe(online, getBanMessage(playerUuid, online.getName()));
        }
    }

    /**
     * Schreibt die Bann-Daten in automod/playerdata.yml.
     */
    private static void setBanData(UUID uuid, String reason, String bannedBy, long until) {
        setBanData(uuid, null, reason, bannedBy, until);
    }

    private static void setBanData(UUID uuid, String playerName, String reason, String bannedBy, long until) {
        if (uuid == null) return;
        String basePath = uuid + ".ban.";
        long now = System.currentTimeMillis();

        if (playerName == null || playerName.isBlank()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playerName = p.getName();
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.getName() != null) {
                    playerName = op.getName();
                }
            }
        }

        DataManager.set(FOLDER, FILE, basePath + "active", true);
        if (playerName != null && !playerName.isBlank()) {
            DataManager.set(FOLDER, FILE, basePath + "name", playerName);
            DataManager.set(FOLDER, FILE, uuid + ".name", playerName);
        }
        DataManager.set(FOLDER, FILE, basePath + "reason", reason != null ? reason : Lang.get("common.no-reason"));
        DataManager.set(FOLDER, FILE, basePath + "banned-by", bannedBy != null ? bannedBy : "AutoMod");
        DataManager.set(FOLDER, FILE, basePath + "banned-at", now);
        DataManager.set(FOLDER, FILE, basePath + "until", until);
        DataManager.set(FOLDER, FILE, basePath + "formatted-until", until == -1 ? Lang.get("common.permanent") : DATE_FORMAT.format(Instant.ofEpochMilli(until)));
    }

    // =========================================================================
    // BANN AUFHEBEN (UNBAN)
    // =========================================================================

    /**
     * Hebt den Bann eines Spielers auf.
     */
    public static void unban(Player player) {
        if (player == null) return;
        unban(player.getUniqueId(), "System");
    }

    public static void unban(Player player, String unbannedBy) {
        if (player == null) return;
        unban(player.getUniqueId(), unbannedBy);
    }

    /**
     * Hebt den Bann einer UUID auf.
     */
    public static void unban(UUID playerUuid) {
        unban(playerUuid, "System");
    }

    public static void unban(UUID playerUuid, String unbannedBy) {
        if (playerUuid == null) return;
        String basePath = playerUuid + ".ban.";
        DataManager.set(FOLDER, FILE, basePath + "active", false);
        // AutoMod-Strikes bei Entbannung zurücksetzen, damit der Spieler nicht beim nächsten Strike sofort wieder gebannt wird
        DataManager.set(FOLDER, FILE, playerUuid + ".strikes", 0);
        HistoryUtil.addEntry(playerUuid, "UNBAN", Lang.get("punish.history.unban"), unbannedBy != null ? unbannedBy : "System", "-");
    }

    // =========================================================================
    // BANN STATUS & ABFRAGEN
    // =========================================================================

    /**
     * Prüft, ob ein Spieler aktuell gebannt ist.
     */
    public static boolean isBanned(Player player) {
        if (player == null) return false;
        return isBanned(player.getUniqueId());
    }

    /**
     * Prüft, ob eine UUID aktuell gebannt ist (inkl. automatischer Ablauf-Prüfung).
     */
    public static boolean isBanned(UUID playerUuid) {
        if (playerUuid == null) return false;
        String basePath = playerUuid + ".ban.";

        boolean active = DataManager.getBoolean(FOLDER, FILE, basePath + "active");
        if (!active) {
            return false;
        }

        long until = DataManager.getLong(FOLDER, FILE, basePath + "until");
        // until == -1 bedeutet permanenter Bann
        if (until == -1) {
            return true;
        }

        // Temp-Bann Prüfung gegen aktuelle Systemzeit
        if (System.currentTimeMillis() >= until) {
            // Bann ist abgelaufen -> automatisch deaktivieren
            DataManager.set(FOLDER, FILE, basePath + "active", false);
            return false;
        }

        return true;
    }

    /**
     * Prüft, ob der Bann permanent ist.
     */
    public static boolean isPermanent(UUID playerUuid) {
        if (!isBanned(playerUuid)) return false;
        long until = DataManager.getLong(FOLDER, FILE, playerUuid + ".ban.until");
        return until == -1;
    }

    /**
     * Gibt den Grund für den Bann zurück.
     */
    public static String getReason(UUID playerUuid) {
        if (playerUuid == null) return "";
        return DataManager.getString(FOLDER, FILE, playerUuid + ".ban.reason");
    }

    /**
     * Gibt zurück, wer den Spieler gebannt hat.
     */
    public static String getBannedBy(UUID playerUuid) {
        if (playerUuid == null) return "";
        return DataManager.getString(FOLDER, FILE, playerUuid + ".ban.banned-by");
    }

    /**
     * Gibt den Zeitpunkt des Banns zurück.
     */
    public static long getBannedAt(UUID playerUuid) {
        if (playerUuid == null) return 0;
        return DataManager.getLong(FOLDER, FILE, playerUuid + ".ban.banned-at");
    }

    /**
     * Gibt das Ablaufdatum (Millis) zurück (-1 bei permanent).
     */
    public static long getUntil(UUID playerUuid) {
        if (playerUuid == null) return 0;
        return DataManager.getLong(FOLDER, FILE, playerUuid + ".ban.until");
    }

    /**
     * Gibt die verbleibende Bann-Zeit in Millisekunden zurück (-1 bei permanent, 0 bei nicht gebannt).
     */
    public static long getRemainingMillis(UUID playerUuid) {
        if (!isBanned(playerUuid)) return 0;
        long until = getUntil(playerUuid);
        if (until == -1) return -1;
        return Math.max(0, until - System.currentTimeMillis());
    }

    /**
     * Gibt die verbleibende Zeit lesbar formatiert zurück.
     */
    public static String getFormattedRemainingTime(UUID playerUuid) {
        if (!isBanned(playerUuid)) return Lang.get("punish.status.not-banned");
        if (isPermanent(playerUuid)) return Lang.get("common.permanent");
        return formatDuration(getRemainingMillis(playerUuid));
    }

    /**
     * Lädt alle Informationen zu einem Bann gebündelt als {@link BanInfo}.
     */
    public static BanInfo getBanInfo(UUID playerUuid) {
        if (playerUuid == null) return null;
        boolean active = isBanned(playerUuid);
        String reason = getReason(playerUuid);
        String bannedBy = getBannedBy(playerUuid);
        long bannedAt = getBannedAt(playerUuid);
        long until = getUntil(playerUuid);
        return new BanInfo(playerUuid, active, reason, bannedBy, bannedAt, until, until == -1);
    }

    /**
     * Gibt den gespeicherten Spielernamen einer UUID zurück (oder Fallback über Bukkit).
     */
    public static String getPlayerName(UUID playerUuid) {
        if (playerUuid == null) return null;
        String name = DataManager.getString(FOLDER, FILE, playerUuid + ".ban.name");
        if (name != null && !name.isBlank()) return name;
        name = DataManager.getString(FOLDER, FILE, playerUuid + ".name");
        if (name != null && !name.isBlank()) return name;
        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null) return p.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
        return op.getName();
    }

    /**
     * Gibt alle UUIDs zurück, die aktuell aktiv gebannt sind.
     */
    public static List<UUID> getBannedPlayerUuids() {
        List<UUID> banned = new ArrayList<>();
        FileConfiguration config = DataManager.getConfiguration(FOLDER, FILE);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (isBanned(uuid)) {
                    banned.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return banned;
    }

    /**
     * Gibt eine Liste aller Spielernamen (oder UUIDs) zurück, die momentan gebannt sind.
     */
    public static List<String> getBannedPlayerNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : getBannedPlayerUuids()) {
            String name = getPlayerName(uuid);
            if (name != null && !name.isBlank()) {
                names.add(name);
            } else {
                names.add(uuid.toString());
            }
        }
        return names;
    }

    /**
     * Versucht, eine gebannte UUID anhand eines Spielernamens oder einer UUID als String aufzulösen.
     */
    public static UUID resolveBannedUuid(String targetName) {
        if (targetName == null || targetName.isBlank()) return null;

        // 1. UUID-String-Check
        try {
            UUID directUuid = UUID.fromString(targetName);
            if (isBanned(directUuid)) {
                return directUuid;
            }
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Durchsuche alle aktiv gebannten Spieler
        for (UUID uuid : getBannedPlayerUuids()) {
            String name = getPlayerName(uuid);
            if (name != null && name.equalsIgnoreCase(targetName)) {
                return uuid;
            }
        }

        // 3. Fallback über PlayerResolver (ohne blockierenden Mojang-HTTP-Call)
        return PlayerResolver.resolveUuid(targetName);
    }

    /**
     * Erstellt den formatieren Kick-/Bann-Bildschirm für den Spieler.
     */
    public static String getBanMessage(UUID playerUuid) {
        return getBanMessage(playerUuid, null);
    }

    /**
     * Erstellt den formatieren Kick-/Bann-Bildschirm für den Spieler unter Verwendung des Namens.
     */
    public static String getBanMessage(UUID playerUuid, String playerName) {
        if (playerName == null) {
            playerName = getPlayerName(playerUuid);
            if (playerName == null) {
                playerName = Lang.get("common.unknown");
            }
        }
        String reason = getReason(playerUuid);
        String by = getBannedBy(playerUuid);
        long until = getUntil(playerUuid);
        boolean isAutoMod = by == null || by.equalsIgnoreCase("AutoMod") || by.equalsIgnoreCase("System");

        return AutoModMessages.getBanMessage(playerName, reason, by, isAutoMod, until);
    }

    /**
     * Kickt einen Spieler sicher auf dem Minecraft-Hauptthread,
     * auch wenn der Aufruf aus einem asynchronen Thread (z.B. AsyncPlayerChatEvent) kommt.
     *
     * @param player  Der zu kickende Spieler
     * @param message Die Kick-Nachricht
     */
    public static void kickPlayerSafe(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        if (Bukkit.isPrimaryThread()) {
            player.kickPlayer(message);
        } else {
            Bukkit.getScheduler().runTask(Backbone.getInstance(), () -> {
                if (player.isOnline()) {
                    player.kickPlayer(message);
                }
            });
        }
    }

    // =========================================================================
    // HILFSMETHODEN FÜR DAUER-PARSING & FORMATIERUNG
    // =========================================================================

    /**
     * Prüft, ob ein String der Syntax einer Zeitdauer (z.B. "10m", "0m", "2h", "1d") entspricht.
     */
    public static boolean isDurationSyntax(String input) {
        return de.basti.backbone.util.DurationUtil.isDurationSyntax(input);
    }

    /**
     * Parst Strings wie "10m", "2h", "1d", "30d" in Millisekunden.
     */
    public static long parseDuration(String input) {
        return de.basti.backbone.util.DurationUtil.parseDuration(input);
    }

    /**
     * Formatiert Millisekunden in einen lesbaren Text in der aktiven Sprache.
     */
    public static String formatDuration(long millis) {
        return de.basti.backbone.util.DurationUtil.formatDuration(millis);
    }
}
