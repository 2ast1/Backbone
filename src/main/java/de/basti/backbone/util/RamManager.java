package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import de.basti.backbone.commands.teleport.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Verwaltet das automatische und manuelle RAM-Compacting von Backbone.
 * Unterstützt drei Modi:
 * - FAST (Schneller, hält Caches im RAM)
 * - BALANCED (Ausgewogen, periodisches Auslagern ungenutzter Daten)
 * - LIGHT (Leichter auf dem RAM, aggressives Auslagern auf die Festplatte)
 */
public class RamManager {

    public enum RamMode {
        FAST,
        BALANCED,
        LIGHT;

        public static RamMode fromString(String value) {
            if (value == null || value.isBlank()) return BALANCED;
            String upper = value.trim().toUpperCase(Locale.ROOT);
            return switch (upper) {
                case "FAST", "HIGH", "PERFORMANCE", "SCHNELL", "SCHNELLER" -> FAST;
                case "LIGHT", "LOW", "COMPACT", "LANGSAM", "LANGSAMER", "LEICHT" -> LIGHT;
                default -> BALANCED;
            };
        }
    }

    public record CompactionResult(
            double beforeMb,
            double afterMb,
            double savedMb,
            int filesOffloaded,
            int expiredTpaCleaned,
            long durationMs
    ) {}

    private static Backbone plugin;
    private static BukkitTask autoCompactTask;

    public static void init(Backbone backboneInstance) {
        plugin = backboneInstance;
        applyMode();
    }

    public static void shutdown() {
        if (autoCompactTask != null) {
            autoCompactTask.cancel();
            autoCompactTask = null;
        }
    }

    public static RamMode getMode() {
        String raw = DataManager.getConfigString("performance.ram-mode");
        return RamMode.fromString(raw);
    }

    public static void setMode(RamMode mode) {
        if (mode == null) mode = RamMode.BALANCED;
        DataManager.setConfig("performance.ram-mode", mode.name());
        applyMode();
    }

    public static boolean isLightMode() {
        return getMode() == RamMode.LIGHT;
    }

    /**
     * Startet oder aktualisiert den Hintergrund-Task entsprechend dem konfigurierten RAM-Modus.
     */
    public static void applyMode() {
        if (autoCompactTask != null) {
            autoCompactTask.cancel();
            autoCompactTask = null;
        }
        if (plugin == null || !plugin.isEnabled()) return;

        RamMode mode = getMode();
        switch (mode) {
            case FAST -> {
                // Nur abgelaufene TPA-Anfragen alle 5 Minuten bereinigen, YAML-Caches bleiben im RAM
                autoCompactTask = Bukkit.getScheduler().runTaskTimer(plugin, TpaManager::cleanupExpired, 6000L, 6000L);
            }
            case BALANCED -> {
                // Alle 5 Minuten (6000 Ticks) sanftes Auslagern von Offline-Inventaren & Locations;
                // bei hoher Speicherauslastung (> 85%) komplettes Compacting
                autoCompactTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    TpaManager.cleanupExpired();
                    Runtime rt = Runtime.getRuntime();
                    long max = rt.maxMemory();
                    long used = rt.totalMemory() - rt.freeMemory();
                    boolean highPressure = max > 0 && ((double) used / max) > 0.85;
                    DataManager.compactCache(highPressure);
                    if (highPressure) {
                        System.gc();
                    }
                }, 6000L, 6000L);
            }
            case LIGHT -> {
                // Alle 90 Sekunden (1800 Ticks) aggressives Auslagern aller nicht zwingend benötigten Caches auf die Festplatte
                autoCompactTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    executeCompact(true);
                }, 1800L, 1800L);
            }
        }
    }

    /**
     * Führt eine sofortige RAM-Komprimierung durch:
     * Speichert alle offenen Laufzeitdaten auf die Festplatte, wirft nicht benötigte Caches aus dem RAM
     * und stößt bei Bedarf die Garbage Collection an.
     */
    public static CompactionResult executeCompact(boolean triggerGc) {
        long start = System.currentTimeMillis();
        Runtime rt = Runtime.getRuntime();

        long usedBeforeBytes = rt.totalMemory() - rt.freeMemory();

        // 1. Abgelaufene TPA-Requests bereinigen
        int expiredTpa = TpaManager.cleanupExpired();

        // 2. Alle gecachten YAML-Dateien von Backbone auf die Festplatte sichern und aus dem RAM werfen
        int filesOffloaded = DataManager.compactCache(true);

        // 3. Garbage Collection ausführen, um den freigegebenen Heap-Speicher sofort zurückzugeben
        if (triggerGc) {
            System.gc();
        }

        long usedAfterBytes = rt.totalMemory() - rt.freeMemory();
        long savedBytes = Math.max(0L, usedBeforeBytes - usedAfterBytes);

        double beforeMb = usedBeforeBytes / (1024.0 * 1024.0);
        double afterMb = usedAfterBytes / (1024.0 * 1024.0);
        double savedMb = savedBytes / (1024.0 * 1024.0);
        long elapsed = System.currentTimeMillis() - start;

        return new CompactionResult(beforeMb, afterMb, savedMb, filesOffloaded, expiredTpa, elapsed);
    }
}
