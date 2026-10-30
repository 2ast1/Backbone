package de.basti.backbone.util.automod.message;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.MuteUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Verwaltet und formatiert konfigurierbare Benachrichtigungen für AutoMod und manuelle Sanktionen.
 * Unterstützt Platzhalter wie {automod_prefix}, {playername}, {operator}, {reason}, {until}, {until_HH}, {until_mm}, {until_ss}, {HH}, {mm}, {ss}.
 */
public class AutoModMessages {

    private static final String FOLDER = "automod";
    private static final String FILE = "config.yml";

    private static final DateTimeFormatter TIME_HH = DateTimeFormatter.ofPattern("HH");
    private static final DateTimeFormatter TIME_MM = DateTimeFormatter.ofPattern("mm");
    private static final DateTimeFormatter TIME_SS = DateTimeFormatter.ofPattern("ss");

    private static final Set<String> BUILTIN_DEFAULT_TEMPLATES = Set.of(
            // English defaults
            "{automod_prefix} &rWarning: This message was flagged by AutoMod. &7Reason: &e{reason}",
            "{automod_prefix} &cYou were muted for {until} due to repeated violations! &7Reason: &e{reason}",
            "{automod_prefix}\n&cYou were kicked by AutoMod!\n&7Reason: &e{reason}",
            "{automod_prefix}\n\n&c&lYou have been banned from the server!\n\n&7Reason: &e{reason}\n&7Remaining time: &c{until}",
            "&cYou were warned by &e{operator}&c! &7Reason: &e{reason}",
            "&cYou were muted by &e{operator} &cfor {until}! &7Reason: &e{reason}",
            "&cYou were kicked by &e{operator}&c!\n&7Reason: &e{reason}",
            "&cYou were kicked by &e{operator}!\n&7Reason: &e{reason}",
            "&c&lYou have been banned from the server!\n\n&7Reason: &e{reason}\n&7Banned by: &e{operator}\n&7Remaining time: &c{until}",
            "{automod_prefix} &cYou are currently muted. &7({until})",
            // German defaults
            "{automod_prefix} &rVerwarnung: Diese Nachricht wurde von AutoMod markiert. &7Grund: &e{reason}",
            "{automod_prefix} &cDu wurdest aufgrund wiederholter Verstöße für {until} gemutet! &7Grund: &e{reason}",
            "{automod_prefix}\n&cDu wurdest vom AutoMod gekickt!\n&7Grund: &e{reason}",
            "{automod_prefix}\n\n&c&lDu wurdest vom Server gebannt!\n\n&7Grund: &e{reason}\n&7Verbleibende Zeit: &c{until}",
            "&cDu wurdest von &e{operator} &cverwarnt! &7Grund: &e{reason}",
            "&cDu wurdest von &e{operator} &cfür {until} gemutet! &7Grund: &e{reason}",
            "&cDu wurdest von &e{operator} &cgekickt!\n&7Grund: &e{reason}",
            "&c&lDu wurdest vom Server gebannt!\n\n&7Grund: &e{reason}\n&7Gebannt von: &e{operator}\n&7Verbleibende Zeit: &c{until}",
            "{automod_prefix} &cDu bist momentan gemuted. &7({until})",
            "{automod_prefix} &cDu bist momentan gemutet. &7({until})"
    );

    // =========================================================================
    // GETTER FÜR KONFIGURIERTE NACHRICHTEN
    // =========================================================================

    public static String getWarnMessage(String playerName, String reason, String operator, boolean isAutoMod) {
        String key = isAutoMod ? "messages.automod.warn" : "messages.manual.warn";
        String langKey = isAutoMod ? "automod.messages.automod.warn" : "automod.messages.manual.warn";
        String template = getTemplate(key, langKey);
        return format(template, playerName, reason, operator, 0);
    }

    public static String getMuteMessage(String playerName, String reason, String operator, boolean isAutoMod, long untilMillis) {
        String key = isAutoMod ? "messages.automod.mute" : "messages.manual.mute";
        String langKey = isAutoMod ? "automod.messages.automod.mute" : "automod.messages.manual.mute";
        String template = getTemplate(key, langKey);
        return format(template, playerName, reason, operator, untilMillis);
    }

    public static String getKickMessage(String playerName, String reason, String operator, boolean isAutoMod) {
        String key = isAutoMod ? "messages.automod.kick" : "messages.manual.kick";
        String langKey = isAutoMod ? "automod.messages.automod.kick" : "automod.messages.manual.kick";
        String template = getTemplate(key, langKey);
        return format(template, playerName, reason, operator, 0);
    }

    public static String getBanMessage(String playerName, String reason, String operator, boolean isAutoMod, long untilMillis) {
        String key = isAutoMod ? "messages.automod.ban" : "messages.manual.ban";
        String langKey = isAutoMod ? "automod.messages.automod.ban" : "automod.messages.manual.ban";
        String template = getTemplate(key, langKey);
        return format(template, playerName, reason, operator, untilMillis);
    }

    public static String getChatMutedMessage(String playerName, String reason, String operator, long untilMillis) {
        String key = "messages.chat-muted";
        String langKey = "automod.messages.chat-muted";
        String template = getTemplate(key, langKey);
        return format(template, playerName, reason, operator, untilMillis);
    }

    private static String getTemplate(String key, String langKey) {
        String val = DataManager.getString(FOLDER, FILE, key);
        if (val != null && !val.isBlank() && !BUILTIN_DEFAULT_TEMPLATES.contains(val)) {
            return val;
        }
        return Lang.getRaw(langKey);
    }

    // =========================================================================
    // PLATZHALTER-FORMATIERUNG
    // =========================================================================

    /**
     * Ersetzt alle konfigurierten Platzhalter in einer Vorlage.
     *
     * @param template    Die Rohvorlage mit Platzhaltern
     * @param playerName  Name des bestraften Spielers
     * @param reason      Grund für die Maßnahme
     * @param operator    Ausführender (z.B. "AutoMod" oder Spielername)
     * @param untilMillis Zeitstempel des Endes (-1 = permanent, 0 = n/a, sonst Millis)
     * @return Fertig formatierter Text mit Farbcodes
     */
    public static String format(String template, String playerName, String reason, String operator, long untilMillis) {
        if (template == null) return "";

        LocalTime now = LocalTime.now();
        String currentHH = now.format(TIME_HH);
        String currentMM = now.format(TIME_MM);
        String currentSS = now.format(TIME_SS);

        String prefix = AutoMod.getPrefix();
        String safePrefix = (prefix != null && !prefix.isBlank()) ? prefix : "&9[AutoMod]";
        String safePlayer = (playerName != null && !playerName.isBlank()) ? playerName : Lang.get("common.unknown");
        String safeReason = (reason != null && !reason.isBlank()) ? reason : Lang.get("common.no-reason");
        String safeOperator = (operator != null && !operator.isBlank()) ? operator : "AutoMod";

        // Berechnung der Restzeit bzw. des Endes
        String untilFormatted;
        String untilHH;
        String untilMM;
        String untilSS;

        if (untilMillis == -1) {
            // Permanent
            untilFormatted = Lang.get("common.permanent");
            untilHH = Lang.get("common.permanent");
            untilMM = "00";
            untilSS = "00";
        } else if (untilMillis <= 0) {
            // Nicht temporär / nicht verfügbar (z.B. Kick/Warn)
            untilFormatted = "N/A";
            untilHH = "00";
            untilMM = "00";
            untilSS = "00";
        } else {
            // Temporäre Maßnahme -> Restzeit berechnen
            long remainingMillis = Math.max(0, untilMillis - System.currentTimeMillis());
            long totalSeconds = remainingMillis / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            untilFormatted = MuteUtil.formatDuration(remainingMillis);
            untilHH = String.format("%02d", hours);
            untilMM = String.format("%02d", minutes);
            untilSS = String.format("%02d", seconds);
        }

        String formattedTemplate = de.basti.backbone.util.PrefixManager.formatColors(template);
        String formattedPrefix = de.basti.backbone.util.PrefixManager.formatColors(safePrefix);

        return formattedTemplate
                .replace("{automod_prefix}", formattedPrefix)
                .replace("{pluginPrefix}", formattedPrefix)
                .replace("{playername}", safePlayer)
                .replace("{operator}", safeOperator)
                .replace("{reason}", safeReason)
                .replace("{until}", untilFormatted)
                .replace("{until_HH}", untilHH)
                .replace("{until_hh}", untilHH)
                .replace("{until_mm}", untilMM)
                .replace("{until_MM}", untilMM)
                .replace("{until_ss}", untilSS)
                .replace("{until_SS}", untilSS)
                .replace("{HH}", currentHH)
                .replace("{hh}", currentHH)
                .replace("{mm}", currentMM)
                .replace("{ss}", currentSS);
    }
}
