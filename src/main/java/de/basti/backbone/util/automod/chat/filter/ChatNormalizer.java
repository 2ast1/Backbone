package de.basti.backbone.util.automod.chat.filter;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Hilfsklasse für die Bereinigung und Normalisierung von Chat-Nachrichten,
 * um Verschleierungs- und Bypass-Versuche (Leetspeak, Sonderzeichen, Akzente etc.) aufzudecken.
 */
public class ChatNormalizer {

    /**
     * Entfernt Akzente und diakritische Zeichen (z.B. é -> e, û -> u),
     * lässt aber normale Buchstaben unverändert.
     */
    public static String stripAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Prüft, ob ein Wort an Wortgrenzen (nicht als Substring mitten in legitimen Wörtern) vorkommt.
     */
    public static boolean containsWordBoundary(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        String regex = "(?i)(?:^|[^a-zäöüß0-9])" + Pattern.quote(word) + "(?:[^a-zäöüß0-9]|$)";
        return Pattern.compile(regex).matcher(text).find();
    }

    /**
     * Wandelt typische Leetspeak-Zahlen und Symbole in ihre Buchstaben-Äquivalente um
     * (z.B. 0 -> o, 1/! -> i, 3 -> e, 4/@ -> a, 5/$ -> s, 7 -> t, 8 -> b).
     */
    public static String convertLeetspeak(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '0' -> sb.append('o');
                case '1', '!', '|' -> sb.append('i');
                case '3' -> sb.append('e');
                case '4', '@' -> sb.append('a');
                case '5', '$' -> sb.append('s');
                case '7' -> sb.append('t');
                case '8' -> sb.append('b');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Reduziert mehrfach wiederholte aufeinanderfolgende Buchstaben auf einen einzelnen Buchstaben
     * (z.B. "huuuuurensoooohn" -> "hurensohn"), um Streck-Bypässe zu erkennen.
     */
    public static String collapseRepeatedChars(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(input.length());
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != prev || !Character.isLetter(c)) {
                sb.append(c);
                prev = c;
            }
        }
        return sb.toString();
    }

    /**
     * Entfernt alle Zeichen außer Kleinbuchstaben und Umlaute.
     * Nützlich gegen Trenn-Bypässe (z.B. "h.u.r.e.n.s.o.h.n" oder "f u c k").
     */
    public static String stripNonLetters(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zäöüß]", "");
    }

    /**
     * Teilt den Text anhand von Leer- und Sonderzeichen in einzelne Wörter auf.
     */
    public static String[] extractTokens(String input) {
        if (input == null || input.isBlank()) return new String[0];
        return input.replaceAll("[^a-zäöüß0-9\\s]", " ").trim().split("\\s+");
    }

    /**
     * Prüft, ob ein kurzes verbotenes Wort (z.B. "hs") als eigenständiges Wort
     * in den Tokens vorkommt.
     */
    public static boolean containsExactToken(String[] tokens, String shortWord) {
        if (tokens == null || shortWord == null) return false;
        for (String token : tokens) {
            if (token.equals(shortWord) || collapseRepeatedChars(token).equals(shortWord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Erkennt kurze verbotene Wörter auch dann, wenn sie mit Trennzeichen/Leerzeichen
     * getrennt wurden (z.B. "h.s" oder "k y s"), ohne Teilwörter in normalen Wörtern (wie "achso") zu treffen.
     */
    public static boolean containsSpacedShortWord(String input, String shortWord) {
        if (input == null || shortWord == null || shortWord.isEmpty()) return false;
        StringBuilder regex = new StringBuilder("(?i)(?:^|\\b)");
        for (int i = 0; i < shortWord.length(); i++) {
            if (i > 0) {
                regex.append("[.\\-_\\s]+");
            }
            regex.append(Pattern.quote(String.valueOf(shortWord.charAt(i)))).append("+");
        }
        regex.append("(?:\\b|$)");
        return Pattern.compile(regex.toString()).matcher(input).find();
    }

    /**
     * Erkennt verbotene Wörter, die durch Leerzeichen oder Trennzeichen verschleiert wurden
     * (z.B. "f u c k", "s p a s t", "huren sohn"), jedoch zwingend an Wortgrenzen beginnen und enden,
     * damit Wortübergänge wie "Viel Spaß trotzdem" oder "was passt" keinen Fehlalarm auslösen.
     */
    public static boolean containsSpacedWord(String input, String word) {
        if (input == null || word == null || word.isEmpty()) return false;
        StringBuilder regex = new StringBuilder("(?i)(?:^|\\b)");
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) {
                regex.append("[.\\-_\\s]*");
            }
            regex.append(Pattern.quote(String.valueOf(word.charAt(i)))).append("+");
        }
        regex.append("(?:\\b|$)");
        return Pattern.compile(regex.toString()).matcher(input).find();
    }

    /**
     * Berechnet die Levenshtein-Distanz (Tippfehler-Abstand) zwischen zwei Strings.
     */
    public static int levenshteinDistance(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
