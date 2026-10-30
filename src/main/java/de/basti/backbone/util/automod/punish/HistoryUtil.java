package de.basti.backbone.util.automod.punish;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Verwaltet die Historie von Verstößen und Sanktionen (Warns, Mutes, Kicks, Bans) eines Spielers.
 * Speichert alle Einträge chronologisch in der automod/playerdata.yml.
 */
public class HistoryUtil {

    private static final String FOLDER = "automod";
    private static final String FILE = "playerdata.yml";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Ein einzelner Eintrag in der Spieler-Historie.
     */
    public record HistoryEntry(
            String action,     // z.B. WARN, MUTE, KICK, BAN, UNMUTE, UNBAN
            String reason,     // Grund bzw. was AutoMod erkannt hat
            long timestamp,    // Zeitstempel in ms
            String date,       // Lesbares Datum
            String by,         // Wer die Aktion ausgeführt hat (z.B. "AutoMod" oder Spielername)
            String duration    // Dauer (z.B. "15 Minuten", "Permanent" oder "-")
    ) {}

    /**
     * Fügt einen neuen Eintrag zur Historie des Spielers hinzu.
     *
     * @param playerUuid UUID des Spielers
     * @param action     Art der Aktion (WARN, MUTE, KICK, BAN etc.)
     * @param reason     Grund für die Aktion
     * @param by         Ausführender (z.B. "AutoMod")
     * @param duration   Dauer (oder "-" bei Aktionen ohne Zeit)
     */
    public static void addEntry(UUID playerUuid, String action, String reason, String by, String duration) {
        if (playerUuid == null) return;

        FileConfiguration config = DataManager.getConfiguration(FOLDER, FILE);
        String path = playerUuid + ".history";

        List<Map<?, ?>> rawList = config.getMapList(path);
        List<Map<String, Object>> historyList = new ArrayList<>();

        for (Map<?, ?> rawMap : rawList) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                entry.put(String.valueOf(e.getKey()), e.getValue());
            }
            historyList.add(entry);
        }

        long now = System.currentTimeMillis();
        Map<String, Object> newEntry = new LinkedHashMap<>();
        newEntry.put("action", action != null ? action.toUpperCase() : "INFO");
        newEntry.put("reason", reason != null ? reason : Lang.get("common.no-reason"));
        newEntry.put("timestamp", now);
        newEntry.put("date", DATE_FORMAT.format(Instant.ofEpochMilli(now)));
        newEntry.put("by", by != null ? by : "AutoMod");
        newEntry.put("duration", duration != null ? duration : "-");

        historyList.add(newEntry);

        config.set(path, historyList);
        DataManager.saveConfiguration(FOLDER, FILE, config);
    }

    /**
     * Gibt die komplette Historie eines Spielers zurück.
     *
     * @param playerUuid UUID des Spielers
     * @return Liste aller Historien-Einträge
     */
    public static List<HistoryEntry> getHistory(UUID playerUuid) {
        if (playerUuid == null) return Collections.emptyList();

        FileConfiguration config = DataManager.getConfiguration(FOLDER, FILE);
        List<Map<?, ?>> rawList = config.getMapList(playerUuid + ".history");
        List<HistoryEntry> entries = new ArrayList<>();

        for (Map<?, ?> map : rawList) {
            String action = map.get("action") != null ? String.valueOf(map.get("action")) : "INFO";
            String reason = map.get("reason") != null ? String.valueOf(map.get("reason")) : "";
            long timestamp = 0;
            Object tsObj = map.get("timestamp");
            if (tsObj instanceof Number number) {
                timestamp = number.longValue();
            }
            String date = map.get("date") != null ? String.valueOf(map.get("date")) : "";
            String by = map.get("by") != null ? String.valueOf(map.get("by")) : "AutoMod";
            String duration = map.get("duration") != null ? String.valueOf(map.get("duration")) : "-";

            entries.add(new HistoryEntry(action, reason, timestamp, date, by, duration));
        }

        return entries;
    }
}
