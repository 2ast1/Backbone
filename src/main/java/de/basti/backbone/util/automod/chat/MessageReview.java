package de.basti.backbone.util.automod.chat;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Repräsentiert das Ergebnis einer Nachrichtenüberprüfung durch den AutoMod (ChatMod).
 * Kann über den DataManager in einer YAML-Datei gespeichert und wieder geladen werden.
 */
public class MessageReview {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private Player player;
    private final UUID playerUuid;
    private final String playerName;
    private final String message;
    private final long timestamp;
    private final String description;
    private final int strikes;

    /**
     * Erstellt ein neues MessageReview-Objekt mit dem aktuellen Zeitstempel.
     *
     * @param player      Der Spieler, der die Nachricht gesendet hat
     * @param message     Die ursprüngliche Chat-Nachricht
     * @param description Kurze Beschreibung, was gefunden wurde (z.B. "Wortfilter: hurensohn (verschleiert)")
     * @param strikes     Anzahl der verhängten Strikes (0 = unauffällig/Hinweis, 1 = Verstoß, >1 = schwerer Verstoß)
     */
    public MessageReview(Player player, String message, String description, int strikes) {
        this(player, message, System.currentTimeMillis(), description, strikes);
    }

    /**
     * Erstellt ein neues MessageReview-Objekt mit einem festgelegten Zeitstempel.
     *
     * @param player      Der Spieler, der die Nachricht gesendet hat
     * @param message     Die ursprüngliche Chat-Nachricht
     * @param timestamp   Zeitpunkt der Prüfung in Millisekunden (Epoch-Time)
     * @param description Kurze Beschreibung, was gefunden wurde
     * @param strikes     Anzahl der verhängten Strikes
     */
    public MessageReview(Player player, String message, long timestamp, String description, int strikes) {
        this.player = player;
        this.playerUuid = (player != null) ? player.getUniqueId() : null;
        this.playerName = (player != null) ? player.getName() : Lang.get("common.unknown");
        this.message = message;
        this.timestamp = timestamp;
        this.description = description;
        this.strikes = Math.max(0, strikes);
    }

    /**
     * Konstruktor für das Laden aus einer Datei (über den DataManager),
     * auch falls der Spieler zu diesem Zeitpunkt offline sein sollte.
     */
    public MessageReview(UUID playerUuid, String playerName, String message, long timestamp, String description, int strikes) {
        this.player = (playerUuid != null) ? Bukkit.getPlayer(playerUuid) : null;
        this.playerUuid = playerUuid;
        this.playerName = (playerName != null) ? playerName : Lang.get("common.unknown");
        this.message = message;
        this.timestamp = timestamp;
        this.description = description;
        this.strikes = Math.max(0, strikes);
    }

    /**
     * Gibt das Bukkit Player-Objekt zurück (falls der Spieler aktuell online ist).
     */
    public Player getPlayer() {
        if (player == null && playerUuid != null) {
            player = Bukkit.getPlayer(playerUuid);
        }
        return player;
    }

    /**
     * Gibt die UUID des Spielers zurück (nützlich für das Speichern/Laden im DataManager).
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Gibt den Namen des Spielers zum Zeitpunkt der Nachricht zurück.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gibt die geprüfte Original-Nachricht zurück.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gibt den Zeitstempel (Millisekunden seit 01.01.1970) zurück.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gibt den Zeitstempel lesbar formatiert zurück (z.B. "21.10.2026 23:45:12").
     */
    public String getFormattedTimestamp() {
        return DATE_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * Gibt die kurze Beschreibung zurück, was bei der Prüfung erkannt wurde.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gibt die Anzahl der Strikes zurück, die AutoMod für diese Nachricht vergibt.
     */
    public int getStrikes() {
        return strikes;
    }

    /**
     * Hilfsmethode: Gibt true zurück, wenn mindestens 1 Strike verhängt wurde.
     */
    public boolean hasStrikes() {
        return strikes > 0;
    }

    @Override
    public String toString() {
        return "MessageReview{" +
                "player=" + playerName +
                ", message='" + message + '\'' +
                ", timestamp=" + getFormattedTimestamp() +
                ", description='" + description + '\'' +
                ", strikes=" + strikes +
                '}';
    }
}
