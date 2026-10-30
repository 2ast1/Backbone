package de.basti.backbone.util.automod.chat;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.chat.filter.AdvertisingFilter;
import de.basti.backbone.util.automod.chat.filter.ChatNormalizer;
import de.basti.backbone.util.automod.chat.filter.SpamFilter;
import de.basti.backbone.util.automod.chat.filter.WordFilter;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.message.AutoModMessages;
import de.basti.backbone.util.automod.punish.BanUtil;
import de.basti.backbone.util.automod.punish.HistoryUtil;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hauptklasse für die Chat-Moderation.
 * Koordiniert die einzelnen Filter aus dem Package 'de.basti.backbone.util.automod.chat.filter'
 * und führt konfigurierte Aktionen (Warn, Mute, Kick, Ban) anhand der Strikes aus.
 */
public class ChatMod {

    /**
     * Führt die vollständige Chat-Überprüfung durch und wendet bei Strikes
     * automatisch die in der config.yml konfigurierten Aktionen an.
     *
     * @param msg    Die gesendete Chat-Nachricht
     * @param player Der sendende Spieler
     * @return true, falls die Nachricht einen Verstoß (Strikes) ausgelöst hat
     */
    public static boolean executeChatMod(String msg, Player player) {
        if (player == null) return false;
        if (player.isOp() || player.hasPermission("backbone.automod.bypass") || player.hasPermission("backbone.admin")) {
            return false;
        }
        MessageReview review = ChatMod.checkMsg(player, msg);

        if (review.hasStrikes()) {
            String uuidStr = player.getUniqueId().toString();
            int playerStrikes = DataManager.getInt("automod", "playerdata.yml", uuidStr + ".strikes");
            int strikes = review.getStrikes();
            int finalStrikes = strikes + playerStrikes;

            String action = null;
            // Suche von der neuen Gesamtzahl der Strikes rückwärts bis über den alten Stand nach der höchsten passenden Aktion
            for (int i = finalStrikes; i > playerStrikes; i--) {
                String foundAction = DataManager.getString("automod", "config.yml", "settings.chat.strikes." + i + ".action");
                if (foundAction != null) {
                    action = foundAction;
                    break;
                }
            }

            if (action != null) {
                switch (action.toUpperCase()) {
                    case "WARN" -> {
                        HistoryUtil.addEntry(player.getUniqueId(), "WARN", review.getDescription(), "AutoMod", "-");
                        String warnMsg = AutoModMessages.getWarnMessage(player.getName(), review.getDescription(), "AutoMod", true);
                        player.sendMessage(warnMsg);
                    }
                    case "MUTE" -> {
                        // Standard-Mute: 15 Minuten Temp-Mute bei Erreichen der Mute-Schwelle
                        long muteDuration = 15 * 60 * 1000L;
                        long until = System.currentTimeMillis() + muteDuration;
                        String reason = Lang.get("automod.reason.mute-threshold", "strikes", String.valueOf(finalStrikes), "description", review.getDescription());
                        MuteUtil.tempMute(player, muteDuration, reason, "AutoMod");
                        String muteMsg = AutoModMessages.getMuteMessage(player.getName(), review.getDescription(), "AutoMod", true, until);
                        player.sendMessage(muteMsg);
                    }
                    case "KICK" -> {
                        HistoryUtil.addEntry(player.getUniqueId(), "KICK", review.getDescription(), "AutoMod", "-");
                        String kickMsg = AutoModMessages.getKickMessage(player.getName(), review.getDescription(), "AutoMod", true);
                        BanUtil.kickPlayerSafe(player, kickMsg);
                    }
                    case "BAN" -> {
                        String reason = Lang.get("automod.reason.ban-threshold", "strikes", String.valueOf(finalStrikes), "description", review.getDescription());
                        BanUtil.ban(player, reason, "AutoMod");
                    }
                }
            }

            DataManager.set("automod", "playerdata.yml", uuidStr + ".strikes", finalStrikes);
        }

        return review.hasStrikes();
    }

    /**
     * Überprüft eine Chat-Nachricht und gibt ein {@link MessageReview} zurück.
     * Nutzt modulare Filter für Wörter, Fremdwerbung und Spam.
     *
     * @param player  Der Spieler, der die Nachricht gesendet hat
     * @param message Die zu prüfende Nachricht
     * @return Ein {@link MessageReview}-Objekt mit dem Analyse-Ergebnis
     */
    public static MessageReview checkMsg(Player player, String message) {
        long now = System.currentTimeMillis();

        // 1. AutoMod oder Chat-Scan global deaktiviert?
        if (!AutoMod.isActive() || !AutoMod.isChatActive()) {
            return new MessageReview(player, message == null ? "" : message, now, Lang.get("automod.review.disabled"), 0);
        }

        // 2. Sicherheitsprüfung bei leerer Nachricht
        if (message == null || message.trim().isEmpty()) {
            return new MessageReview(player, message == null ? "" : message, now, Lang.get("automod.review.empty"), 0);
        }

        List<String> findings = new ArrayList<>();
        int strikes = 0;

        // 3. Vorbereitung & Normalisierung der Nachricht für Bypass-Erkennung
        String normalizedBase = ChatNormalizer.stripAccents(message.toLowerCase(Locale.ROOT));
        String deLeeted = ChatNormalizer.convertLeetspeak(normalizedBase);
        String collapsed = ChatNormalizer.collapseRepeatedChars(deLeeted);
        String strippedAll = ChatNormalizer.stripNonLetters(collapsed);
        String[] tokens = ChatNormalizer.extractTokens(collapsed);

        // 4. Wortfilter prüfen (Beleidigungen, Wortgrenzen, Leetspeak, Stretching, Fuzzy/Tippfehler)
        WordFilter.WordCheckResult wordResult = WordFilter.check(normalizedBase, deLeeted, collapsed, strippedAll, tokens);
        if (wordResult.detected()) {
            findings.add(wordResult.description());
            strikes = Math.max(strikes, wordResult.strikes());
        }

        // 5. Fremdwerbung prüfen (Discord-Invites, Domains, Server-IPs nach config.yml)
        AdvertisingFilter.AdvertisingCheckResult adResult = AdvertisingFilter.check(message);
        if (adResult.detected()) {
            findings.addAll(adResult.findings());
            strikes = Math.max(strikes, adResult.strikes());
        }

        // 6. Spam- & Caps-Lock-Prüfung (Flooding und exzessive Großschreibung)
        SpamFilter.SpamCheckResult spamResult = SpamFilter.check(message);
        if (spamResult.detected()) {
            findings.addAll(spamResult.findings());
            strikes = Math.max(strikes, spamResult.strikes());
        }

        // 7. Analyse-Ergebnis zusammenstellen
        String description = findings.isEmpty() ? Lang.get("automod.review.clean") : String.join(" | ", findings);
        return new MessageReview(player, message, now, description, strikes);
    }
}
