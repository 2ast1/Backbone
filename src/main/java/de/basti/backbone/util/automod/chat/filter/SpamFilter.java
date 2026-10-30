package de.basti.backbone.util.automod.chat.filter;

import de.basti.backbone.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Überprüft Nachrichten auf Zeichen-Flooding und übermäßige Großschreibung (Caps-Lock).
 */
public class SpamFilter {

    // Erkennt extremes Zeichen-Flooding (z.B. "halloooooooooo" oder "!!!!!!!!" ab 8 gleichen Zeichen in Folge)
    private static final Pattern FLOOD_PATTERN = Pattern.compile("(.)\\1{7,}");

    public record SpamCheckResult(boolean detected, List<String> findings, int strikes) {}

    /**
     * Prüft die Nachricht auf Spam und Caps-Lock.
     *
     * @param message Die Originalnachricht
     * @return Ein {@link SpamCheckResult} mit den Funden
     */
    public static SpamCheckResult check(String message) {
        if (message == null || message.isBlank()) {
            return new SpamCheckResult(false, List.of(), 0);
        }

        List<String> findings = new ArrayList<>();
        int strikes = 0;

        // 1. Zeichen-Flooding (8+ identische Zeichen hintereinander)
        if (FLOOD_PATTERN.matcher(message).find()) {
            findings.add(Lang.get("automod.filter.spam.flood"));
            // Leichtes Flooding gibt nur einen Hinweis (0 Strikes), bei langen Nachrichten 1 Strike
            if (message.length() >= 35) {
                strikes = 1;
            }
        }

        // 2. Caps-Lock-Prüfung (nur bei Nachrichten mit mindestens 8 Buchstaben relevant)
        double capsRatio = calculateCapsRatio(message);
        if (capsRatio >= 0.75) {
            findings.add(Lang.get("automod.filter.spam.caps", "percent", String.valueOf(Math.round(capsRatio * 100))));
            // Normales Caps-Lock gibt 0 Strikes (nur Hinweis), bei extremem Caps-Spam (> 40 Zeichen) 1 Strike
            if (message.length() >= 40) {
                strikes = 1;
            }
        }

        return new SpamCheckResult(!findings.isEmpty(), findings, strikes);
    }

    /**
     * Berechnet das Verhältnis von Großbuchstaben zu allen Buchstaben (0.0 bis 1.0).
     */
    public static double calculateCapsRatio(String message) {
        if (message == null) return 0.0;
        int letters = 0;
        int upperCase = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upperCase++;
                }
            }
        }
        if (letters < 8) {
            return 0.0;
        }
        return (double) upperCase / letters;
    }
}
