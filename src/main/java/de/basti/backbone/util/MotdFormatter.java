package de.basti.backbone.util;

import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatter for server MOTDs.
 * Supports dynamic placeholders, color codes (&amp; -&gt; §), line breaks (\n),
 * and automatic text centering via &lt;center&gt;...&lt;/c&gt;.
 */
public class MotdFormatter {

    // Standard pixel width of a line in the Minecraft server browser (multiplayer list)
    private static final int TARGET_LINE_PIXELS = 260;
    private static final int SPACE_PIXELS = 4;

    /**
     * Formats the raw MOTD text including all placeholders and centering.
     */
    public static String format(String rawMotd) {
        if (rawMotd == null || rawMotd.isBlank()) {
            return "";
        }

        // 1. Resolve placeholders
        String formatted = replacePlaceholders(rawMotd);

        // 2. Standardize color codes (& and &#RRGGBB -> §)
        formatted = PrefixManager.formatColors(formatted);

        // 3. Calculate centering line by line
        return formatCentering(formatted);
    }

    /**
     * Determines the currently active MOTD (taking maintenance mode & placeholders into account).
     */
    public static String getCurrentFormattedMotd() {
        boolean maintenance = DataManager.getConfigBoolean("server.maintenance");
        String rawMotd;
        if (maintenance) {
            boolean shouldChange = DataManager.contains("", "config.yml", "server.motd.maintenance.change")
                    ? DataManager.getBoolean("", "config.yml", "server.motd.maintenance.change")
                    : true;
            if (shouldChange) {
                String sessionMsg = de.basti.backbone.commands.maintenance.MaintenanceCommand.getSessionMessage();
                if (sessionMsg != null && !sessionMsg.isBlank()) {
                    rawMotd = sessionMsg;
                } else {
                    rawMotd = DataManager.getString("", "config.yml", "server.motd.maintenance.text");
                    if (rawMotd == null || rawMotd.isBlank()) {
                        rawMotd = Lang.getRaw("maintenance.default-motd");
                    }
                }
            } else {
                rawMotd = DataManager.getString("", "config.yml", "server.motd.normal");
            }
        } else {
            rawMotd = DataManager.getString("", "config.yml", "server.motd.normal");
        }

        if (rawMotd == null || rawMotd.isBlank()) {
            rawMotd = "A Minecraft Server";
        }

        return format(rawMotd);
    }

    /**
     * Returns a specific line of the formatted MOTD (1 or 2).
     */
    public static String getMotdLine(int lineNumber) {
        String motd = getCurrentFormattedMotd();
        String[] lines = motd.split("\\\\n|\n");
        if (lineNumber == 1) {
            return lines.length > 0 ? lines[0] : "";
        } else if (lineNumber == 2) {
            return lines.length > 1 ? lines[1] : "";
        }
        return "";
    }

    private static String replacePlaceholders(String text) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        double tps = TpsTracker.getTps();
        String tpsString = String.format(Locale.ROOT, "%.1f", tps);

        int slots = 0;
        int players = 0;
        try {
            if (Bukkit.getServer() != null) {
                slots = Bukkit.getMaxPlayers();
                players = HideManager.getPublicOnlineCount();
            }
        } catch (Exception ignored) {}

        String rawPluginPrefix = Lang.getPrefix();
        String pluginPrefixFull = (rawPluginPrefix != null && !rawPluginPrefix.isEmpty()) ? rawPluginPrefix : "§9[Backbone] ";
        if (!pluginPrefixFull.endsWith(" ")) {
            pluginPrefixFull = pluginPrefixFull + " ";
        }
        String pluginPrefixTrimmed = pluginPrefixFull.trim();

        return text
                .replace("{pluginPrefix} ", pluginPrefixFull)
                .replace("{pluginPrefix}", pluginPrefixTrimmed)
                .replace("{plugin_prefix} ", pluginPrefixFull)
                .replace("{plugin_prefix}", pluginPrefixTrimmed)
                .replace("{plugin-prefix} ", pluginPrefixFull)
                .replace("{plugin-prefix}", pluginPrefixTrimmed)
                .replace("{Plugin-Prefix} ", pluginPrefixFull)
                .replace("{Plugin-Prefix}", pluginPrefixTrimmed)
                .replace("{Plugin-Präfix} ", pluginPrefixFull)
                .replace("{Plugin-Präfix}", pluginPrefixTrimmed)
                .replace("{plugin-präfix} ", pluginPrefixFull)
                .replace("{plugin-präfix}", pluginPrefixTrimmed)
                .replace("{prefix} ", pluginPrefixFull)
                .replace("{prefix}", pluginPrefixTrimmed)
                .replace("{players}", String.valueOf(players))
                .replace("{slots}", String.valueOf(slots))
                .replace("{maxmb}", String.valueOf(maxMb))
                .replace("{freemb}", String.valueOf(freeMb))
                .replace("{usedmb}", String.valueOf(usedMb))
                .replace("{TPS}", tpsString)
                .replace("{tps}", tpsString)
                .replace("{day}", String.valueOf(today.getDayOfMonth()))
                .replace("{month}", String.valueOf(today.getMonthValue()))
                .replace("{year}", String.valueOf(today.getYear()))
                .replace("{hh}", now.format(DateTimeFormatter.ofPattern("HH")))
                .replace("{mm}", now.format(DateTimeFormatter.ofPattern("mm")))
                .replace("{ss}", now.format(DateTimeFormatter.ofPattern("ss")));
    }

    private static String formatCentering(String text) {
        String[] lines = text.split("\\\\n|\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean centerLine = false;

            if (line.contains("<center>") || line.contains("</c>")) {
                centerLine = true;
                line = line.replace("<center>", "").replace("</c>", "");
            }

            if (centerLine) {
                int lineWidth = getPixelWidth(line);
                int padding = Math.max(0, (TARGET_LINE_PIXELS - lineWidth) / 2);
                int spacesCount = padding / SPACE_PIXELS;
                line = " ".repeat(spacesCount) + line;
            }

            result.append(line);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Calculates the exact pixel width of a text based on the default Minecraft font.
     * Color codes (§a, §c etc.) are ignored; bold formatting (§l) adds 1 pixel per character.
     */
    public static int getPixelWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        boolean isBold = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'l') {
                    isBold = true;
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
                    isBold = false;
                }
                i++;
                continue;
            }

            int charWidth = getCharWidth(c);
            if (isBold && c != ' ') {
                charWidth += 1;
            }
            width += charWidth;
        }

        return width;
    }

    private static int getCharWidth(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '.', ',', ':', ';', '|', '`', 'i' -> 2;
            case '\'', 'l' -> 3;
            case 'I', 't', '[', ']', '"' -> 4;
            case 'k', 'f', '{', '}', '(', ')', '<', '>' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
