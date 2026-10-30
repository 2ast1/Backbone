package de.basti.backbone.util.chat;

import de.basti.backbone.util.DataManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet den Status und die Abklingzeit des Slowchats.
 */
public final class SlowchatManager {

    private static boolean enabled = false;
    private static long expiryTimestamp = 0; // 0 = permanent
    private static final Map<UUID, Long> lastChatTimes = new ConcurrentHashMap<>();

    private SlowchatManager() {}

    /**
     * Prüft, ob Slowchat aktuell aktiv ist (unter Berücksichtigung eventueller Ablaufzeiten).
     */
    public static boolean isSlowchatEnabled() {
        if (!enabled) {
            return false;
        }
        if (expiryTimestamp > 0 && System.currentTimeMillis() > expiryTimestamp) {
            enabled = false;
            expiryTimestamp = 0;
            return false;
        }
        return true;
    }

    /**
     * Aktiviert Slowchat permanent oder für eine festgelegte Dauer.
     *
     * @param durationMillis Dauer in Millisekunden (0 für permanent)
     */
    public static void enable(long durationMillis) {
        enabled = true;
        expiryTimestamp = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0;
    }

    /**
     * Deaktiviert Slowchat.
     */
    public static void disable() {
        enabled = false;
        expiryTimestamp = 0;
        lastChatTimes.clear();
    }

    public static boolean isPermanent() {
        return enabled && expiryTimestamp == 0;
    }

    public static long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public static long getRemainingMillis() {
        if (!enabled) return 0;
        if (expiryTimestamp == 0) return -1;
        return Math.max(0, expiryTimestamp - System.currentTimeMillis());
    }

    /**
     * Gibt die konfigurierte Verzögerung zwischen Nachrichten in Sekunden zurück.
     */
    public static int getCooldownSeconds() {
        return Math.max(1, DataManager.getConfig().getInt("chat.slowchat.delay-seconds", 5));
    }

    /**
     * Ermittelt die verbleibende Wartezeit eines Spielers in Millisekunden.
     */
    public static long getPlayerRemainingCooldownMillis(UUID uuid) {
        if (uuid == null) return 0;
        Long lastTime = lastChatTimes.get(uuid);
        if (lastTime == null) return 0;

        long cooldownMillis = getCooldownSeconds() * 1000L;
        long elapsed = System.currentTimeMillis() - lastTime;
        if (elapsed >= cooldownMillis) {
            return 0;
        }
        return cooldownMillis - elapsed;
    }

    /**
     * Speichert den aktuellen Chat-Zeitstempel des Spielers.
     */
    public static void recordChat(UUID uuid) {
        if (uuid != null) {
            lastChatTimes.put(uuid, System.currentTimeMillis());
        }
    }

    public static void clear() {
        lastChatTimes.clear();
    }
}
