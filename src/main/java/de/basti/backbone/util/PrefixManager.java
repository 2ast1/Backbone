package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.automod.chat.filter.ChatNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility-Klasse zur Verwaltung von Spieler-Präfixen.
 * <p>
 * Speichert Präfixe in der Datei 'playerdata.yml' direkt im Hauptverzeichnis des Plugins
 * unter dem Pfad: {@code <UUID>.prefix}
 * <p>
 * Enthält einen integrierten Schutz vor dem Überschreiben der Tabliste durch fremde Plugins
 * (Events mit Priorität MONITOR, Ticks-Verzögerung nach dem Login sowie ein zyklischer Watchdog-Task).
 */
public class PrefixManager {

    public static boolean isPrefixEnabled() {
        return DataManager.getConfig().getBoolean("prefix.enabled", true);
    }

    public static boolean isNameColorEnabled() {
        return DataManager.getConfig().getBoolean("namecolor.enabled", true);
    }

    private static final String FOLDER = "";
    private static final String FILE = "playerdata.yml";

    // Regex für Hex-Farbcodes im Format &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Regex zum Extrahieren aller gültigen Farbcodes (&0-9, &a-f, &k-o, &r sowie &#RRGGBB)
    private static final Pattern COLOR_EXTRACTOR_PATTERN = Pattern.compile("(?i)(&#[0-9a-f]{6}|&[0-9a-fk-or])");

    private static JavaPlugin plugin;
    private static BukkitTask watchdogTask;
    private static boolean initialized = false;

    // =========================================================================
    // INITIALISIERUNG & LIFECYCLE
    // =========================================================================

    /**
     * Initialisiert den PrefixManager, registriert Event-Listener zur Tablisten-Überwachung
     * und startet den Watchdog-Task gegen das Überschreiben durch andere Plugins.
     *
     * @param pluginInstance Instanz des JavaPlugins (z.B. Backbone)
     */
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        initialized = true;

        // Alten Watchdog-Task beenden, falls vorhanden
        if (watchdogTask != null && !watchdogTask.isCancelled()) {
            watchdogTask.cancel();
        }

        // Watchdog-Task: Läuft alle 20 Ticks (1 Sekunde) und stellt sicher,
        // dass kein anderes Plugin (z.B. Essentials, TAB, Scoreboard-Plugins) den Tablistennamen überschrieben hat.
        watchdogTask = Bukkit.getScheduler().runTaskTimer(pluginInstance, PrefixManager::enforceAllTabLists, 20L, 20L);

        // Tabliste aller bereits online befindlichen Spieler aktualisieren
        updateAllTabLists();
    }

    /**
     * Beendet den Watchdog-Task sauber beim Server-Stopp oder Plugin-Reload.
     */
    public static void shutdown() {
        if (watchdogTask != null && !watchdogTask.isCancelled()) {
            watchdogTask.cancel();
            watchdogTask = null;
        }
    }

    // =========================================================================
    // PRÄFIX SPEICHERN, LESEN & LÖSCHEN (CRUD)
    // =========================================================================

    /**
     * Setzt den Präfix für einen Spieler anhand seiner UUID.
     * Speichert den Wert in 'playerdata.yml' unter '<uuid>.prefix' und aktualisiert
     * bei aktuell online befindlichen Spielern sofort die Tabliste.
     *
     * @param uuid   UUID des Spielers
     * @param prefix Der neue Präfix (unterstützt '&' und '&#RRGGBB' Hex-Farbcodes)
     */
    public static void setPrefix(UUID uuid, String prefix) {
        if (uuid == null) return;

        String path = uuid + ".prefix";
        DataManager.set(FOLDER, FILE, path, prefix);

        // Falls der Spieler online ist, Tablisten-Namen sofort aktualisieren
        Player onlinePlayer = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            PlayerResolver.recordPlayer(onlinePlayer);
            updateTabList(onlinePlayer);
        }
    }

    /**
     * Setzt den Präfix für einen Online-Spieler.
     *
     * @param player Der Zielspieler
     * @param prefix Der neue Präfix
     */
    public static void setPrefix(Player player, String prefix) {
        if (player == null) return;
        setPrefix(player.getUniqueId(), prefix);
    }

    /**
     * Gibt den formatierten Präfix (inklusive Farbcodes '§') für eine UUID zurück.
     * Gibt einen leeren String "" zurück, falls kein Präfix gesetzt ist.
     *
     * @param uuid UUID des Spielers
     * @return Formatierter Präfix oder leerer String
     */
    public static String getPrefix(UUID uuid) {
        return getPrefix(uuid, "");
    }

    /**
     * Gibt den formatierten Präfix für einen Spieler zurück.
     *
     * @param player Spieler
     * @return Formatierter Präfix oder leerer String
     */
    public static String getPrefix(Player player) {
        if (player == null) return "";
        return getPrefix(player.getUniqueId());
    }

    /**
     * Gibt den formatierten Präfix zurück oder den Standardwert, falls keiner hinterlegt ist.
     *
     * @param uuid         UUID des Spielers
     * @param defaultValue Standardwert, falls kein Präfix vorhanden
     * @return Formatierter Präfix mit Farbcodes
     */
    public static String getPrefix(UUID uuid, String defaultValue) {
        if (uuid == null) return defaultValue;
        if (!isPrefixEnabled()) return "";
        String raw = getRawPrefix(uuid);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return formatColors(raw);
    }

    /**
     * Gibt den rohen, unformatierten Präfix (z.B. mit '&c[Admin]') direkt aus der Datei zurück.
     * Falls kein individueller Präfix gesetzt ist, wird bei aktivem Rangsystem der Rang-Präfix genutzt.
     *
     * @param uuid UUID des Spielers
     * @return Roher Präfix oder null, falls nicht vorhanden
     */
    public static String getRawPrefix(UUID uuid) {
        if (uuid == null) return null;
        if (!isPrefixEnabled()) return null;

        String custom = DataManager.getString(FOLDER, FILE, uuid + ".prefix");
        if (custom != null && !custom.isBlank()) {
            return custom;
        }

        // Fallback auf Rang-Präfix
        if (RankManager.isRanksEnabled() && RankManager.isUseRankPrefixEnabled()) {
            Rank rank = RankManager.getRankOfPlayer(uuid);
            if (rank != null && rank.getPrefix() != null && !rank.getPrefix().isBlank()) {
                return rank.getPrefix();
            }
        }

        return null;
    }

    /**
     * Gibt den rohen Präfix für einen Online-Spieler zurück.
     */
    public static String getRawPrefix(Player player) {
        if (player == null) return null;
        return getRawPrefix(player.getUniqueId());
    }

    /**
     * Prüft, ob ein Spieler einen Präfix hinterlegt hat.
     *
     * @param uuid UUID des Spielers
     * @return true, wenn ein Präfix existiert und nicht leer ist
     */
    public static boolean hasPrefix(UUID uuid) {
        if (uuid == null || !isPrefixEnabled()) return false;
        String raw = getRawPrefix(uuid);
        return raw != null && !raw.isBlank();
    }

    /**
     * Prüft, ob ein Spieler einen Präfix hinterlegt hat.
     */
    public static boolean hasPrefix(Player player) {
        if (player == null) return false;
        return hasPrefix(player.getUniqueId());
    }

    /**
     * Prüft, ob ein Spieler einen individuellen (nicht geerbten) Präfix in 'playerdata.yml' hat.
     *
     * @param uuid UUID des Spielers
     * @return true, wenn ein individueller Präfix gesetzt ist
     */
    public static boolean hasCustomPrefix(UUID uuid) {
        if (uuid == null || !isPrefixEnabled()) return false;
        String custom = DataManager.getString(FOLDER, FILE, uuid + ".prefix");
        return custom != null && !custom.isBlank();
    }

    /**
     * Prüft, ob ein Spieler einen individuellen (nicht geerbten) Präfix in 'playerdata.yml' hat.
     */
    public static boolean hasCustomPrefix(Player player) {
        if (player == null) return false;
        return hasCustomPrefix(player.getUniqueId());
    }

    /**
     * Löscht den Präfix eines Spielers aus 'playerdata.yml' und aktualisiert den
     * Tablisten-Namen sofort (Namensfarbe bleibt erhalten, falls gesetzt).
     *
     * @param uuid UUID des Spielers
     */
    public static void deletePrefix(UUID uuid) {
        if (uuid == null) return;

        DataManager.set(FOLDER, FILE, uuid + ".prefix", null);

        Player onlinePlayer = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            updateTabList(onlinePlayer);
        }
    }

    /**
     * Löscht den Präfix eines Online-Spielers.
     */
    public static void deletePrefix(Player player) {
        if (player == null) return;
        deletePrefix(player.getUniqueId());
    }

    /**
     * Alias für {@link #deletePrefix(UUID)}.
     */
    public static void removePrefix(UUID uuid) {
        deletePrefix(uuid);
    }

    /**
     * Alias für {@link #deletePrefix(Player)}.
     */
    public static void removePrefix(Player player) {
        deletePrefix(player);
    }

    // =========================================================================
    // NAMENSFARBE SPEICHERN, LESEN & LÖSCHEN (NAME COLOR)
    // =========================================================================

    /**
     * Filtert alle ungültigen Zeichen heraus und behält ausschließlich echte
     * Minecraft-Farbcodes (z.B. "&4&l" oder "&#FF5555") übrig.
     * Aus z.B. "HAHAHA&4hallo&l" wird automatisch "&4&l".
     *
     * @param input Der eingegebene Text
     * @return Bereinigter String, der nur noch Farbcodes enthält (oder leer, falls keine vorhanden)
     */
    public static String extractColorCodes(String input) {
        if (input == null || input.isBlank()) return "";

        // Unterstützt sowohl '&' als auch versehentlich eingegebene '§'
        String normalized = input.replace('§', '&');

        Matcher matcher = COLOR_EXTRACTOR_PATTERN.matcher(normalized);
        StringBuilder cleanColors = new StringBuilder();
        while (matcher.find()) {
            cleanColors.append(matcher.group());
        }
        return cleanColors.toString();
    }

    /**
     * Setzt die Namensfarbe für einen Spieler (z.B. "&4&l" oder "&#FFAA00").
     * <p>
     * <b>Bereinigung (Variante B):</b> Ungültiger normaler Text wie "HAHAHA" wird automatisch
     * herausgefiltert. Werden gar keine Farbcodes gefunden, wird die Namensfarbe gelöscht/zurückgesetzt.
     *
     * @param uuid      UUID des Spielers
     * @param colorCode Der gewünschte Farbcode (z.B. "&4&l" oder "test&c&l" -> wird zu "&c&l")
     */
    public static void setNameColor(UUID uuid, String colorCode) {
        if (uuid == null) return;

        String clean = extractColorCodes(colorCode);

        // Wenn gar kein Farbcode im Text war (z.B. "HAHAHA"), Namensfarbe entfernen
        if (clean.isEmpty()) {
            removeNameColor(uuid);
            return;
        }

        DataManager.set(FOLDER, FILE, uuid + ".namecolor", clean);

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            PlayerResolver.recordPlayer(onlinePlayer);
            updateTabList(onlinePlayer);
        }
    }

    /**
     * Setzt die Namensfarbe für einen Online-Spieler.
     *
     * @param player    Spieler
     * @param colorCode Der gewünschte Farbcode
     */
    public static void setNameColor(Player player, String colorCode) {
        if (player == null) return;
        setNameColor(player.getUniqueId(), colorCode);
    }

    /**
     * Gibt exakt den gespeicherten Farbcode-String aus der 'playerdata.yml' zurück
     * (z.B. "&4&l"), wie er gesetzt wurde.
     * Ränge färben Spielernamen nicht (Spielernamen sind standardmäßig immer weiß).
     *
     * @param uuid UUID des Spielers
     * @return Gespeicherter Farbcode-String oder null, falls keine individuelle Farbe gesetzt ist
     */
    public static String getNameColor(UUID uuid) {
        if (uuid == null) return null;
        if (!isNameColorEnabled()) return null;

        return DataManager.getString(FOLDER, FILE, uuid + ".namecolor");
    }

    /**
     * Gibt exakt den gespeicherten Farbcode-String für einen Online-Spieler zurück.
     *
     * @param player Spieler
     * @return Gespeicherter Farbcode-String oder null
     */
    public static String getNameColor(Player player) {
        if (player == null) return null;
        return getNameColor(player.getUniqueId());
    }

    /**
     * Gibt die Namensfarbe formatiert mit Minecraft-Farbcodes ('§') zurück.
     *
     * @param uuid UUID des Spielers
     * @return Formatierter Farbcode (z.B. "§4§l") oder leerer String
     */
    public static String getFormattedNameColor(UUID uuid) {
        if (!isNameColorEnabled()) return "";
        String raw = getNameColor(uuid);
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return formatColors(raw);
    }

    /**
     * Gibt die formatierte Namensfarbe für einen Online-Spieler zurück.
     */
    public static String getFormattedNameColor(Player player) {
        if (player == null) return "";
        return getFormattedNameColor(player.getUniqueId());
    }

    /**
     * Prüft, ob ein Spieler eine eigene Namensfarbe hinterlegt hat.
     *
     * @param uuid UUID des Spielers
     * @return true, falls eine Namensfarbe vorhanden und nicht leer ist
     */
    public static boolean hasNameColor(UUID uuid) {
        if (uuid == null || !isNameColorEnabled()) return false;
        String raw = getNameColor(uuid);
        return raw != null && !raw.isBlank();
    }

    /**
     * Prüft, ob ein Spieler eine eigene Namensfarbe hinterlegt hat.
     */
    public static boolean hasNameColor(Player player) {
        if (player == null) return false;
        return hasNameColor(player.getUniqueId());
    }

    /**
     * Entfernt die Namensfarbe eines Spielers aus der 'playerdata.yml'
     * und aktualisiert sofort seine Tabliste.
     *
     * @param uuid UUID des Spielers
     */
    public static void removeNameColor(UUID uuid) {
        if (uuid == null) return;

        DataManager.set(FOLDER, FILE, uuid + ".namecolor", null);

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            updateTabList(onlinePlayer);
        }
    }

    /**
     * Entfernt die Namensfarbe eines Online-Spielers.
     *
     * @param player Spieler
     */
    public static void removeNameColor(Player player) {
        if (player == null) return;
        removeNameColor(player.getUniqueId());
    }

    /**
     * Alias für {@link #removeNameColor(UUID)}.
     */
    public static void deleteNameColor(UUID uuid) {
        removeNameColor(uuid);
    }

    /**
     * Alias für {@link #removeNameColor(Player)}.
     */
    public static void deleteNameColor(Player player) {
        removeNameColor(player);
    }

    // =========================================================================
    // TABLISTEN-VERWALTUNG & ÜBERSCHREIB-SCHUTZ
    // =========================================================================

    /**
     * Berechnet den gewünschten Tablisten-Anzeigenamen für den Spieler:
     * <ul>
     *   <li>Nach dem Präfix wird automatisch ein Leerzeichen eingefügt (z.B. "test Basti").</li>
     *   <li>Die Farbe des Präfix wird nach dem Präfix zurückgesetzt (§r), sodass nur der Präfix gefärbt ist.</li>
     *   <li>Falls eine Namensfarbe via {@link #setNameColor} hinterlegt ist, wird der Spielername in dieser Farbe formatiert.</li>
     *   <li>Direkt nach dem Spielernamen wird zwingend mit §r zurückgesetzt, damit nachfolgende Zeichen (z.B. Chat-Trennzeichen) nicht gefärbt werden.</li>
     * </ul>
     *
     * @param player Spieler
     * @return Formatierter Name für die Tabliste
     */
    public static String getExpectedTabName(Player player) {
        if (player == null) return "";

        boolean hasPref = hasPrefix(player);
        String formattedName = formatPlayerName(player);

        if (hasPref) {
            String prefix = getPrefix(player);
            // Sicherstellen, dass nach dem Präfix genau ein Leerzeichen steht und Farbcodes zurückgesetzt werden
            String prefixWithSpace = prefix.endsWith(" ") ? prefix : prefix + " ";
            return prefixWithSpace + "§r" + formattedName;
        } else {
            return formattedName;
        }
    }

    /**
     * Gibt den formatierten Spielernamen zurück und setzt danach zwingend mit §r zurück.
     * Ist keine eigene Namensfarbe via /setnamecolor gesetzt, ist der Name standardmäßig immer weiß (§f).
     *
     * @param uuid       UUID des Spielers
     * @param playerName Name des Spielers
     * @return Formatierter Spielername mit abschließendem §r (z.B. "§fBasti§r" oder "§c§lBasti§r")
     */
    public static String formatPlayerName(UUID uuid, String playerName) {
        if (playerName == null) return "";
        if (hasNameColor(uuid)) {
            return getFormattedNameColor(uuid) + playerName + "§r";
        }
        return "§f" + playerName + "§r";
    }

    /**
     * Gibt den formatierten Spielernamen für einen Online-Spieler mit abschließendem §r zurück.
     */
    public static String formatPlayerName(Player player) {
        if (player == null) return "";
        return formatPlayerName(player.getUniqueId(), player.getName());
    }

    /**
     * Aktualisiert den Tablisten-Namen eines einzelnen Spielers.
     * Setzt den Wert nur, falls er noch nicht dem Soll-Zustand entspricht (0 Overhead/Paket-Spam).
     *
     * @param player Zu aktualisierender Spieler
     */
    public static void updateTabList(Player player) {
        if (player == null || !player.isOnline()) return;

        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(Backbone.getInstance(), () -> updateTabList(player));
            return;
        }

        String expected = getExpectedTabName(player);
        String current = player.getPlayerListName();

        if (!expected.equals(current)) {
            player.setPlayerListName(expected);
        }
        player.setDisplayName(formatPlayerName(player));
    }

    /**
     * Aktualisiert die Tabliste für alle aktuell auf dem Server befindlichen Spieler.
     */
    public static void updateAllTabLists() {
        if (Bukkit.getServer() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTabList(p);
        }
    }

    /**
     * Interner Watchdog-Check: Überprüft alle Online-Spieler und setzt den Namen
     * zurück, falls ein anderes Plugin ihn verändert hat.
     */
    private static void enforceAllTabLists() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTabList(p);
        }
    }



    // =========================================================================
    // FARB- & HEX-FORMATIERUNG
    // =========================================================================

    /**
     * Übersetzt Standard-Farbcodes ('&a', '&l', etc.) sowie Hex-Farbcodes ('&#123456')
     * in Minecraft-konforme Farbsequenzen ('§').
     *
     * @param text Rohtext
     * @return Formatierter Farbstring
     */
    public static String formatColors(String text) {
        if (text == null || text.isEmpty()) return "";

        // Hex-Farben &#RRGGBB -> §x§R§R§G§G§B§B übersetzen
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        // Standard-Farbcodes mit '&' übersetzen
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    // =========================================================================
    // SCHUTZ VOR AMTSANMASSUNG (TEAM-TITEL)
    // =========================================================================

    private static final List<String> PROTECTED_LONG_KEYWORDS = List.of(
            "owner", "inhaber", "admin", "administrator", "moderator",
            "developer", "entwickler", "supporter", "support", "helfer", "helper",
            "builder", "architekt", "bauleiter", "leitung", "serverleitung", "teamleitung",
            "projektleitung", "management", "operator", "staff"
    );

    private static final List<String> PROTECTED_SHORT_KEYWORDS = List.of(
            "mod", "dev", "sup", "op", "team"
    );

    /**
     * Prüft, ob ein Präfix geschützte Team- oder Serverränge enthält (z.B. "Owner", "Admin", "Mod").
     *
     * @param prefix Der zu prüfende Präfix
     * @return Der Name des erkannten geschützten Titels (z.B. "Owner"), oder null, falls erlaubt.
     */
    public static String getBlockedStaffTitle(String prefix) {
        if (prefix == null || prefix.isBlank()) return null;

        // 1. Farbcodes entfernen
        String stripped = ChatColor.stripColor(formatColors(prefix));
        if (stripped == null || stripped.isBlank()) return null;

        String lower = stripped.toLowerCase(Locale.ROOT);
        String deLeeted = ChatNormalizer.convertLeetspeak(lower);
        String strippedAccents = ChatNormalizer.stripAccents(deLeeted);

        // Sonderzeichen & Klammern durch Leerzeichen ersetzen für Token-Extraktion
        String tokenString = strippedAccents.replaceAll("[^a-zäöüß0-9]", " ");
        String[] tokens = tokenString.trim().split("\\s+");

        // Nur Buchstaben für Lücken-Bypässe (z.B. "O.w.n.e.r" oder "A d m i n")
        String lettersOnly = ChatNormalizer.stripNonLetters(strippedAccents);
        String collapsedLetters = ChatNormalizer.collapseRepeatedChars(lettersOnly);

        // A. Prüfung auf längere Schlüsselwörter (Substrings & Lücken-Bypässe)
        for (String keyword : PROTECTED_LONG_KEYWORDS) {
            if (collapsedLetters.contains(keyword)) {
                if (keyword.equals("admin") && collapsedLetters.contains("badminton")) {
                    String withoutBadminton = collapsedLetters.replace("badminton", "");
                    if (!withoutBadminton.contains("admin")) {
                        continue;
                    }
                }
                return capitalize(keyword);
            }
        }

        // B. Prüfung auf kurze Schlüsselwörter (als eigenständige Tokens oder mit Trennzeichen)
        for (String shortKeyword : PROTECTED_SHORT_KEYWORDS) {
            // Direktes Token oder mit wiederholten Buchstaben (z.B. "mod", "moood")
            if (ChatNormalizer.containsExactToken(tokens, shortKeyword)) {
                return capitalize(shortKeyword);
            }

            // Mit Trennzeichen (z.B. "M.o.d" oder "d-e-v")
            if (ChatNormalizer.containsSpacedShortWord(strippedAccents, shortKeyword)) {
                return capitalize(shortKeyword);
            }

            // Prüfen, ob ein Token auf das Short-Keyword endet (z.B. "SrMod", "TrialMod", "JrDev")
            // Ausdrücklich NICHT für "op" (damit "shop", "top", "stop", "drop" nicht fälschlich blockiert werden)
            if (!shortKeyword.equals("op")) {
                for (String token : tokens) {
                    String collapsedToken = ChatNormalizer.collapseRepeatedChars(token);
                    if (collapsedToken.endsWith(shortKeyword) && collapsedToken.length() <= 10) {
                        if (shortKeyword.equals("team") && (collapsedToken.equals("steam") || collapsedToken.equals("dream"))) {
                            continue;
                        }
                        return capitalize(shortKeyword);
                    }
                }
            }
        }

        return null;
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
