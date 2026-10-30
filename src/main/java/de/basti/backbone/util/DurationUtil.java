package de.basti.backbone.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zentrales Utility für Zeitangaben (Dauer-Parsing und Formatierung).
 * Unterstützt Sekunden (s), Minuten (m), Stunden (h), Tage (d), Wochen (w), Monate (mo, 30d) und Jahre (y, 365d)
 * sowie Kombinationen (z. B. "1y6mo", "30d", "12h30m").
 */
public final class DurationUtil {

    private static final Pattern DURATION_FULL_PATTERN = Pattern.compile("^(?:\\d+(?:mo|[wdhmsy]))+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PART_PATTERN = Pattern.compile("(\\d+)(mo|[wdhmsy])", Pattern.CASE_INSENSITIVE);
    public static final long MAX_DURATION_MILLIS = 100L * 365 * 24 * 60 * 60 * 1000L; // 100 Jahre

    private DurationUtil() {}

    /**
     * Prüft, ob ein String ein Schlüsselwort für permanent ("perm", "permanent", "-1") ist.
     */
    public static boolean isPermanent(String input) {
        if (input == null) return false;
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return clean.equals("perm") || clean.equals("permanent") || clean.equals("-1");
    }

    /**
     * Prüft, ob ein String der Syntax einer Zeitdauer (z.B. "30d", "2h", "1mo", "1y6mo") entspricht.
     */
    public static boolean isDurationSyntax(String input) {
        if (input == null || input.isBlank()) return false;
        return DURATION_FULL_PATTERN.matcher(input.trim().toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Parst Strings wie "10m", "2h", "1d", "30s", "3mo", "1y" in Millisekunden.
     * Unterstützt Kombinationen wie "1y2mo3d".
     *
     * @param input Formatierter Zeitstring
     * @return Dauer in Millisekunden oder 0 bei ungültigem Format
     */
    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) return 0;

        String clean = input.trim().toLowerCase(Locale.ROOT);
        if (!DURATION_FULL_PATTERN.matcher(clean).matches()) {
            return 0;
        }

        Matcher matcher = DURATION_PART_PATTERN.matcher(clean);
        long totalMillis = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return MAX_DURATION_MILLIS;
            }
            if (value <= 0) continue;

            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long unitMillis = switch (unit) {
                case "y" -> 365L * 24 * 60 * 60 * 1000L;
                case "mo" -> 30L * 24 * 60 * 60 * 1000L;
                case "w" -> 7L * 24 * 60 * 60 * 1000L;
                case "d" -> 24L * 60 * 60 * 1000L;
                case "h" -> 60L * 60 * 1000L;
                case "m" -> 60L * 1000L;
                case "s" -> 1000L;
                default -> 0L;
            };

            if (value > MAX_DURATION_MILLIS / Math.max(1L, unitMillis)) {
                return MAX_DURATION_MILLIS;
            }
            totalMillis = Math.min(MAX_DURATION_MILLIS, totalMillis + (value * unitMillis));
        }

        return found ? totalMillis : 0;
    }

    /**
     * Formatiert Millisekunden in einen lesbaren Text in der aktiven Sprache.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return Lang.get("duration.zero");

        long seconds = millis / 1000;
        long years = seconds / (365 * 24 * 3600);
        seconds %= (365 * 24 * 3600);
        long months = seconds / (30 * 24 * 3600);
        seconds %= (30 * 24 * 3600);
        long days = seconds / (24 * 3600);
        seconds %= (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(" ").append(years == 1 ? Lang.get("duration.year-singular") : Lang.get("duration.year-plural")).append(", ");
        if (months > 0) sb.append(months).append(" ").append(months == 1 ? Lang.get("duration.month-singular") : Lang.get("duration.month-plural")).append(", ");
        if (days > 0) sb.append(days).append(" ").append(days == 1 ? Lang.get("duration.day-singular") : Lang.get("duration.day-plural")).append(", ");
        if (hours > 0) sb.append(hours).append(" ").append(hours == 1 ? Lang.get("duration.hour-singular") : Lang.get("duration.hour-plural")).append(", ");
        if (minutes > 0) sb.append(minutes).append(" ").append(minutes == 1 ? Lang.get("duration.minute-singular") : Lang.get("duration.minute-plural")).append(", ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append(" ").append(seconds == 1 ? Lang.get("duration.second-singular") : Lang.get("duration.second-plural"));

        String result = sb.toString().trim();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
