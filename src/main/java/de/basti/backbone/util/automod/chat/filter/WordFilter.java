package de.basti.backbone.util.automod.chat.filter;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;

import java.util.List;
import java.util.Locale;

/**
 * Überprüft Nachrichten auf verbotene Wörter aus der badwords.yml.
 * Beinhaltet Schutz vor Fehlalarmen bei kurzen Wörtern sowie Anti-Bypass- und Fuzzy-Erkennung.
 */
public class WordFilter {

    private static final java.util.Set<String> WHITELIST = java.util.Set.of(
            "minute", "minuten", "minütlich", "assistenz", "assistent", "krawatte", "nachrichten"
    );

    public record WordCheckResult(boolean detected, String description, int strikes) {}

    /**
     * Prüft die Nachricht auf verbotene Wörter.
     *
     * @param normalizedBase Kleingeschriebener Text ohne Akzente
     * @param deLeeted        Text nach Leetspeak-Bereinigung
     * @param collapsed       Text nach Reduzierung wiederholter Buchstaben
     * @param strippedAll     Nur Buchstaben ohne Trenn-/Sonderzeichen
     * @param tokens          Einzelne Wörter des Textes
     * @return Ein {@link WordCheckResult} mit den Funden
     */
    public static WordCheckResult check(String normalizedBase, String deLeeted, String collapsed, String strippedAll, String[] tokens) {
        List<String> blockedWords = AutoMod.getBlockedWords();
        if (blockedWords == null || blockedWords.isEmpty()) {
            return new WordCheckResult(false, null, 0);
        }

        String[] rawCollapsedWords = collapsed == null || collapsed.isBlank()
                ? new String[0]
                : collapsed.trim().split("\\s+");

        for (String rawBadWord : blockedWords) {
            if (rawBadWord == null || rawBadWord.isBlank()) continue;

            String badWord = ChatNormalizer.stripAccents(rawBadWord.trim().toLowerCase(Locale.ROOT));
            String collapsedBadWord = ChatNormalizer.collapseRepeatedChars(badWord);

            // Fall A: Kurze Wörter (<= 3 Zeichen, z.B. "hs", "kys")
            // Dürfen NUR als eigenständiges Wort (oder getrennt mit Punkten/Leerzeichen) gematcht werden,
            // damit Wörter wie "achso", "sechs" oder "wachsen" keinen Fehlalarm auslösen!
            if (badWord.length() <= 3) {
                if (ChatNormalizer.containsExactToken(tokens, badWord) || ChatNormalizer.containsSpacedShortWord(deLeeted, badWord)) {
                    return new WordCheckResult(true, Lang.get("automod.filter.word.direct", "word", rawBadWord), 1);
                }
                continue;
            }

            // Fall B: Direkter Treffer im normalen Text (mit Wortgrenzen)
            if (ChatNormalizer.containsWordBoundary(normalizedBase, badWord)) {
                return new WordCheckResult(true, Lang.get("automod.filter.word.direct", "word", rawBadWord), 1);
            }

            // Fall C: Anti-Bypass-Treffer (Leetspeak, Buchstaben-Stretching, "h.u.r.e" im Wort oder "f u c k" an Wortgrenzen)
            boolean tokenStrippedMatch = false;
            for (String rawWord : rawCollapsedWords) {
                String strippedToken = ChatNormalizer.collapseRepeatedChars(ChatNormalizer.stripNonLetters(rawWord));
                if (WHITELIST.contains(strippedToken) || WHITELIST.contains(rawWord)) {
                    continue; // Vor False-Positives (z.B. "minuten") schützen
                }
                if (strippedToken.equals(collapsedBadWord) || strippedToken.equals(badWord)) {
                    tokenStrippedMatch = true;
                    break;
                }
                // Bei längeren Schimpfwörtern (ab 5 Zeichen) auch Substring prüfen, falls nicht in Whitelist
                if (collapsedBadWord.length() >= 5 && strippedToken.contains(collapsedBadWord)) {
                    tokenStrippedMatch = true;
                    break;
                }
            }

            if (ChatNormalizer.containsWordBoundary(deLeeted, badWord)
                    || ChatNormalizer.containsWordBoundary(collapsed, collapsedBadWord)
                    || tokenStrippedMatch
                    || ChatNormalizer.containsSpacedWord(collapsed, collapsedBadWord)) {
                return new WordCheckResult(true, Lang.get("automod.filter.word.obfuscated", "word", rawBadWord), 1);
            }

            // Fall D: Fuzzy-Matching (Levenshtein-Distanz) für längere Wörter (ab 6 Zeichen)
            // Erkennt absichtliche Buchstabendreher/Tippfehler wie "hurnesohn" statt "hurensohn"
            if (badWord.length() >= 6) {
                for (String token : tokens) {
                    if (Math.abs(token.length() - badWord.length()) <= 1 && ChatNormalizer.levenshteinDistance(token, badWord) == 1) {
                        return new WordCheckResult(true, Lang.get("automod.filter.word.typo", "token", token, "word", rawBadWord), 1);
                    }
                }
            }
        }

        return new WordCheckResult(false, null, 0);
    }
}
