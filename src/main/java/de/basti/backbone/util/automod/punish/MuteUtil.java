package de.basti.backbone.util.automod.punish;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility-Klasse zur Verwaltung von Spieler-Mutes (Permanent und Temp-Mutes).
 * Speichert alle Mute-Daten in der automod/playerdata.yml und protokolliert in der Historie.
 */
public class MuteUtil {

    private static final String FOLDER = "automod";
    private static final String FILE = "playerdata.yml";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Pattern DURATION_FULL_PATTERN = Pattern.compile("^(?:\\d+[wdhms])+$");
    private static final Pattern DURATION_PART_PATTERN = Pattern.compile("(\\d+)([wdhms])");
    private static final long MAX_DURATION_MILLIS = 100L * 365 * 24 * 60 * 60 * 1000L; // 100 Jahre

    /**
     * Datenklasse für vollständige Mute-Informationen.
     */
    public record MuteInfo(
            UUID playerUuid,
            boolean active,
            String reason,
            String mutedBy,
            long mutedAt,
            long until,
            boolean permanent
    ) {
        public String getFormattedMutedAt() {
            return DATE_FORMAT.format(Instant.ofEpochMilli(mutedAt));
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
            if (!active) return Lang.get("punish.status.not-muted");
            if (permanent) return Lang.get("common.permanent");
            return formatDuration(getRemainingMillis());
        }
    }

    // =========================================================================
    // MUTE SETZEN (PERMANENT & TEMP)
    // =========================================================================

    /**
     * Muted einen Spieler permanent.
     *
     * @param player   Der zu mutende Spieler
     * @param reason   Grund für den Mute
     * @param mutedBy  Ausführender (z.B. "AutoMod" oder Spielername)
     */
    public static void mute(Player player, String reason, String mutedBy) {
        if (player == null) return;
        mute(player.getUniqueId(), reason, mutedBy);
    }

    /**
     * Muted eine UUID permanent.
     */
    public static void mute(UUID playerUuid, String reason, String mutedBy) {
        setMuteData(playerUuid, reason, mutedBy, -1);
        HistoryUtil.addEntry(playerUuid, "MUTE", reason, mutedBy, Lang.get("common.permanent"));
    }

    /**
     * Muted einen Spieler temporär (Temp-Mute).
     *
     * @param player         Der zu mutende Spieler
     * @param durationMillis Dauer des Mutes in Millisekunden
     * @param reason         Grund für den Mute
     * @param mutedBy        Ausführender
     */
    public static void tempMute(Player player, long durationMillis, String reason, String mutedBy) {
        if (player == null) return;
        tempMute(player.getUniqueId(), durationMillis, reason, mutedBy);
    }

    /**
     * Muted eine UUID temporär (Temp-Mute).
     */
    public static void tempMute(UUID playerUuid, long durationMillis, String reason, String mutedBy) {
        long until = System.currentTimeMillis() + Math.max(0, durationMillis);
        setMuteData(playerUuid, reason, mutedBy, until);
        HistoryUtil.addEntry(playerUuid, "MUTE", reason, mutedBy, formatDuration(durationMillis));
    }

    /**
     * Schreibt die Mute-Daten in automod/playerdata.yml.
     */
    private static void setMuteData(UUID uuid, String reason, String mutedBy, long until) {
        if (uuid == null) return;
        String basePath = uuid + ".mute.";
        long now = System.currentTimeMillis();

        DataManager.set(FOLDER, FILE, basePath + "active", true);
        DataManager.set(FOLDER, FILE, basePath + "reason", reason != null ? reason : Lang.get("common.no-reason"));
        DataManager.set(FOLDER, FILE, basePath + "muted-by", mutedBy != null ? mutedBy : "AutoMod");
        DataManager.set(FOLDER, FILE, basePath + "muted-at", now);
        DataManager.set(FOLDER, FILE, basePath + "until", until);
        DataManager.set(FOLDER, FILE, basePath + "formatted-until", until == -1 ? Lang.get("common.permanent") : DATE_FORMAT.format(Instant.ofEpochMilli(until)));
    }

    // =========================================================================
    // MUTE AUFHEBEN (UNMUTE)
    // =========================================================================

    /**
     * Hebt den Mute eines Spielers auf.
     */
    public static void unmute(Player player) {
        if (player == null) return;
        unmute(player.getUniqueId(), "System");
    }

    /**
     * Hebt den Mute einer UUID auf.
     */
    public static void unmute(UUID playerUuid) {
        unmute(playerUuid, "System");
    }

    public static void unmute(UUID playerUuid, String unmutedBy) {
        if (playerUuid == null) return;
        String basePath = playerUuid + ".mute.";
        DataManager.set(FOLDER, FILE, basePath + "active", false);
        // AutoMod-Strikes bei Entmutung zurücksetzen, damit der Spieler nicht beim nächsten Strike sofort wieder bestraft wird
        DataManager.set(FOLDER, FILE, playerUuid + ".strikes", 0);
        HistoryUtil.addEntry(playerUuid, "UNMUTE", Lang.get("punish.history.unmute"), unmutedBy != null ? unmutedBy : "System", "-");
    }

    // =========================================================================
    // MUTE STATUS & ABFRAGEN
    // =========================================================================

    /**
     * Prüft, ob ein Spieler aktuell gemutet ist.
     * Läuft ein Temp-Mute automatisch ab, wird der Status direkt auf inaktiv gesetzt.
     */
    public static boolean isMuted(Player player) {
        if (player == null) return false;
        return isMuted(player.getUniqueId());
    }

    /**
     * Prüft, ob eine UUID aktuell gemutet ist (inkl. automatischer Ablauf-Prüfung).
     */
    public static boolean isMuted(UUID playerUuid) {
        if (playerUuid == null) return false;
        String basePath = playerUuid + ".mute.";

        boolean active = DataManager.getBoolean(FOLDER, FILE, basePath + "active");
        if (!active) {
            return false;
        }

        long until = DataManager.getLong(FOLDER, FILE, basePath + "until");
        // until == -1 bedeutet permanenter Mute
        if (until == -1) {
            return true;
        }

        // Temp-Mute Prüfung gegen aktuelle Zeit
        if (System.currentTimeMillis() >= until) {
            // Mute ist abgelaufen -> automatisch deaktivieren
            DataManager.set(FOLDER, FILE, basePath + "active", false);
            return false;
        }

        return true;
    }

    /**
     * Prüft, ob der Mute permanent ist.
     */
    public static boolean isPermanent(UUID playerUuid) {
        if (!isMuted(playerUuid)) return false;
        long until = DataManager.getLong(FOLDER, FILE, playerUuid + ".mute.until");
        return until == -1;
    }

    /**
     * Gibt den Grund für den Mute zurück.
     */
    public static String getReason(UUID playerUuid) {
        if (playerUuid == null) return "";
        return DataManager.getString(FOLDER, FILE, playerUuid + ".mute.reason");
    }

    /**
     * Gibt zurück, wer den Spieler gemutet hat.
     */
    public static String getMutedBy(UUID playerUuid) {
        if (playerUuid == null) return "";
        return DataManager.getString(FOLDER, FILE, playerUuid + ".mute.muted-by");
    }

    /**
     * Gibt den Zeitpunkt des Mutes zurück.
     */
    public static long getMutedAt(UUID playerUuid) {
        if (playerUuid == null) return 0;
        return DataManager.getLong(FOLDER, FILE, playerUuid + ".mute.muted-at");
    }

    /**
     * Gibt das Ablaufdatum (Millis) zurück (-1 bei permanent).
     */
    public static long getUntil(UUID playerUuid) {
        if (playerUuid == null) return 0;
        return DataManager.getLong(FOLDER, FILE, playerUuid + ".mute.until");
    }

    /**
     * Gibt die verbleibende Mute-Zeit in Millisekunden zurück (-1 bei permanent, 0 bei nicht gemutet).
     */
    public static long getRemainingMillis(UUID playerUuid) {
        if (!isMuted(playerUuid)) return 0;
        long until = getUntil(playerUuid);
        if (until == -1) return -1;
        return Math.max(0, until - System.currentTimeMillis());
    }

    /**
     * Gibt die verbleibende Zeit lesbar formatiert zurück.
     */
    public static String getFormattedRemainingTime(UUID playerUuid) {
        if (!isMuted(playerUuid)) return Lang.get("punish.status.not-muted");
        if (isPermanent(playerUuid)) return Lang.get("common.permanent");
        return formatDuration(getRemainingMillis(playerUuid));
    }

    /**
     * Lädt alle Informationen zu einem Mute gebündelt als {@link MuteInfo}.
     */
    public static MuteInfo getMuteInfo(UUID playerUuid) {
        if (playerUuid == null) return null;
        boolean active = isMuted(playerUuid);
        String reason = getReason(playerUuid);
        String mutedBy = getMutedBy(playerUuid);
        long mutedAt = getMutedAt(playerUuid);
        long until = getUntil(playerUuid);
        return new MuteInfo(playerUuid, active, reason, mutedBy, mutedAt, until, until == -1);
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
     * Parst Strings wie "10m", "2h", "1d", "30s" in Millisekunden.
     * Unterstützt Kombinationen wie "1d12h30m".
     *
     * @param input Formatierter Zeitstring
     * @return Dauer in Millisekunden oder 0 bei ungültigem Format
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
