package de.basti.backbone.util.scoreboard;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.MotdFormatter;
import de.basti.backbone.util.TpsTracker;
import de.basti.backbone.util.automod.punish.MuteUtil;
import de.basti.backbone.util.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leistungsfähiger Parser für Scoreboard-Zeilen und Titel.
 * Unterstützt dynamische Platzhalter, wechselnden Text (&lt;switch&gt;),
 * bedingte Anweisungen (&lt;if&gt;), Zentrierung (&lt;center&gt;) und Farbcodes (&amp;).
 */
public class ScoreboardParser {

    private static final ThreadLocal<DecimalFormat> CASH_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.GERMANY))
    );
    private static final DateTimeFormatter DATE_FORMAT_MUTE = DateTimeFormatter.ofPattern("d.M.yyyy")
            .withZone(java.time.ZoneId.systemDefault());

    // Regex für <switch sek=30>Text1</s>Text2</s> oder <switch sek=30>Text1</s>Text2</switch>
    private static final Pattern SWITCH_PATTERN = Pattern.compile(
            "<switch(?:\\s+(?:sek|sec|time)?\\s*=\\s*(\\d+)|\\s+(\\d+))?>(?:(.*?)</switch>|((?:(?!</?switch\\b).*?</s>)+))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Regex für <if [var] [op] [val]>TrueText<else>FalseText</if> (unterstützt <else>, </else>, </if = false>, <if = false>)
    private static final Pattern IF_PATTERN = Pattern.compile(
            "<if\\s+([^>=<!]+?)\\s*([><!=]=?|=)\\s*([^>]+?)>((?:(?!<if\\b).)*?)(?:<(?:/?else|/?if\\s*=\\s*false)>((?:(?!<if\\b).)*?)(?:</else>)?)?</if>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Richtwert für Scoreboard-Sidebar Pixelbreite zur Zentrierung
    private static final int SCOREBOARD_TARGET_PIXELS = 130;
    private static final int SPACE_PIXELS = 4;

    private static final com.sun.management.OperatingSystemMXBean OS_BEAN;

    static {
        com.sun.management.OperatingSystemMXBean found = null;
        try {
            java.lang.management.OperatingSystemMXBean bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                found = sunBean;
            }
        } catch (Throwable ignored) {}
        OS_BEAN = found;
    }

    public static double getProcessCpuLoad() {
        if (OS_BEAN == null) return 0.0;
        try {
            double load = OS_BEAN.getProcessCpuLoad();
            if (load < 0) {
                load = OS_BEAN.getCpuLoad();
            }
            if (load < 0) return 0.0;
            return Math.max(0.0, Math.min(100.0, load * 100.0));
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public static double getSystemCpuLoad() {
        if (OS_BEAN == null) return 0.0;
        try {
            double load = OS_BEAN.getCpuLoad();
            if (load < 0) return 0.0;
            return Math.max(0.0, Math.min(100.0, load * 100.0));
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public static String getFormattedUptime() {
        try {
            long ms = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
            long totalSeconds = ms / 1000;
            long days = totalSeconds / 86400;
            long hours = (totalSeconds % 86400) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            if (days > 0) {
                return days + "d " + hours + "h";
            } else if (hours > 0) {
                return hours + "h " + minutes + "m";
            } else {
                long seconds = totalSeconds % 60;
                return minutes + "m " + seconds + "s";
            }
        } catch (Throwable e) {
            return "0m";
        }
    }

    /**
     * Formatiert Beitritts- und Verlassensnachrichten (server.join / server.leave)
     * inklusive aller Spieler-, Welt-, Server- und Economy-Platzhalter.
     * Hierbei steht {pluginPrefix} (sowie abwärtskompatibel {prefix}) für das Plugin-Präfix (z.B. &9[Backbone] ),
     * während {playerPrefix} für das individuelle Spieler-Präfix genutzt werden kann.
     */
    public static String formatJoinLeaveMessage(String rawText, Player player) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        String pluginPrefix = Lang.getPrefix();
        String pluginPrefixFull = (pluginPrefix != null && !pluginPrefix.isEmpty()) ? pluginPrefix : "§9[Backbone] ";
        if (!pluginPrefixFull.endsWith(" ")) {
            pluginPrefixFull = pluginPrefixFull + " ";
        }
        String pluginPrefixTrimmed = pluginPrefixFull.trim();
        String customPlayerPrefix = (player != null) ? de.basti.backbone.util.PrefixManager.getPrefix(player) : "";

        String prepared = rawText
                .replace("{playerPrefix}", customPlayerPrefix != null ? customPlayerPrefix : "")
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
                .replace("{prefix}", pluginPrefixTrimmed);

        return parse(prepared, player);
    }

    /**
     * Hauptmethode zum Parsen einer Zeile oder eines Titels für einen bestimmten Spieler.
     */
    public static String parse(String rawText, Player player) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        // 1. Basis-Platzhalter auflösen
        String parsed = replacePlaceholders(rawText, player);

        // 2. Verschachtelte Tags auflösen (Switch & If)
        // Bis zu 5 Durchläufe, um Verschachtelungen wie <if> in <switch> oder <switch> in <if> sauber aufzulösen
        for (int i = 0; i < 5; i++) {
            boolean changed = false;

            if (parsed.contains("<switch")) {
                String afterSwitch = parseSwitch(parsed);
                if (!afterSwitch.equals(parsed)) {
                    parsed = afterSwitch;
                    changed = true;
                }
            }

            if (parsed.contains("<if")) {
                String afterIf = parseIf(parsed);
                if (!afterIf.equals(parsed)) {
                    parsed = afterIf;
                    changed = true;
                }
            }

            if (!changed) break;
        }

        // 3. Platzhalter ein zweites Mal prüfen (falls durch if/switch neue Platzhalter entstanden sind)
        if (parsed.contains("{")) {
            parsed = replacePlaceholders(parsed, player);
        }

        // 4. Farbcodes (inkl. Hex &#RRGGBB und Standard-&) sauber übersetzen
        parsed = de.basti.backbone.util.PrefixManager.formatColors(parsed);

        // 5. Zentrierung <center>Text</c>
        if (parsed.contains("<center>") || parsed.contains("</c>")) {
            parsed = formatCentering(parsed);
        }

        return parsed;
    }

    private static String replacePlaceholders(String text, Player player) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        double tps = TpsTracker.getTps();
        String tpsString = String.format(Locale.ROOT, "%.1f", tps);

        double cashVal = 0.0;
        String cashSymbol = "$";
        String cashString = "0.00";
        if (text.contains("{cash}") || text.contains("{cashSymbol}")) {
            cashSymbol = EconomyManager.getCurrencySymbol();
            if (player != null) {
                if (EconomyManager.isEconomyEnabled()) {
                    cashVal = EconomyManager.getBalance(player.getUniqueId());
                } else {
                    Double extBal = de.basti.backbone.util.vault.VaultManager.getExternalBalance(player);
                    cashVal = (extBal != null && Double.isFinite(extBal)) ? extBal : EconomyManager.getBalance(player.getUniqueId());
                    String extSym = de.basti.backbone.util.vault.VaultManager.getExternalCurrencySymbol();
                    if (extSym != null && !extSym.isBlank()) {
                        cashSymbol = extSym;
                    }
                }
            }
            cashString = CASH_FORMAT.get().format(cashVal);
        }

        String cashCirc = "";
        if (text.contains("{cashCirc}")) {
            cashCirc = CASH_FORMAT.get().format(EconomyManager.getTotalCirculation());
        }

        String worldName = (player != null && player.getWorld() != null) ? player.getWorld().getName() : "";
        String playerName = (player != null) ? player.getName() : "";
        String playerUuid = (player != null) ? player.getUniqueId().toString() : "";

        // Mute-Informationen nur bei Bedarf auflösen
        String muteString = "false";
        String muteUntilString = "";
        if (text.contains("{mute}") || text.contains("{muteUntil}")) {
            boolean isMuted = (player != null) && MuteUtil.isMuted(player.getUniqueId());
            muteString = isMuted ? "true" : "false";
            muteUntilString = Lang.get("scoreboard.placeholder.mute-none");

            if (isMuted && player != null) {
                MuteUtil.MuteInfo info = MuteUtil.getMuteInfo(player.getUniqueId());
                if (info != null) {
                    if (info.permanent()) {
                        muteUntilString = Lang.get("common.permanent");
                    } else {
                        muteUntilString = DATE_FORMAT_MUTE.format(java.time.Instant.ofEpochMilli(info.until()));
                    }
                }
            }
        }

        int slots = 0;
        int players = 0;
        try {
            if (Bukkit.getServer() != null) {
                slots = Bukkit.getMaxPlayers();
                players = de.basti.backbone.util.HideManager.getVisibleOnlineCount(player);
            }
        } catch (Exception ignored) {}

        String cpuString = "0.0";
        String cpuIntString = "0";
        String cpuSystemString = "0.0";
        if (text.contains("{cpu}") || text.contains("{cpu_int}") || text.contains("{cpu_system}")) {
            double cpuPercent = getProcessCpuLoad();
            cpuString = String.format(Locale.ROOT, "%.1f", cpuPercent);
            cpuIntString = String.valueOf((int) Math.round(cpuPercent));
            cpuSystemString = String.format(Locale.ROOT, "%.1f", getSystemCpuLoad());
        }

        int ping = (player != null) ? player.getPing() : 0;
        int health = (player != null) ? (int) Math.ceil(player.getHealth()) : 0;
        int maxHealth = 20;
        if (player != null && text.contains("{maxhealth}")) {
            try {
                org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    maxHealth = (int) attr.getValue();
                }
            } catch (Throwable ignored) {}
        }
        int food = (player != null) ? player.getFoodLevel() : 20;
        int level = (player != null) ? player.getLevel() : 0;
        String gamemode = (player != null && player.getGameMode() != null) ? player.getGameMode().name() : "";
        boolean isFlying = (player != null) && player.isFlying();
        String x = "0";
        String y = "0";
        String z = "0";
        if (player != null && (text.contains("{x}") || text.contains("{y}") || text.contains("{z}"))) {
            try {
                if (player.getLocation() != null) {
                    x = String.valueOf(player.getLocation().getBlockX());
                    y = String.valueOf(player.getLocation().getBlockY());
                    z = String.valueOf(player.getLocation().getBlockZ());
                }
            } catch (Throwable ignored) {}
        }
        int worldPlayers = 0;
        if (text.contains("{world_players}") && player != null && player.getWorld() != null) {
            worldPlayers = de.basti.backbone.util.HideManager.getVisibleWorldPlayerCount(player.getWorld(), player);
        }

        String rankName = "";
        String rankPrefix = "";
        String rankExpires = "";
        String rankTimeLeft = "";
        String customPrefix = "";
        String nameColor = "";
        if (player != null) {
            if (text.contains("{rank")) {
                try {
                    de.basti.backbone.ranks.Rank rank = de.basti.backbone.ranks.RankManager.getRankOfPlayer(player);
                    if (rank != null && (de.basti.backbone.ranks.RankManager.isRanksEnabled() || !de.basti.backbone.util.vault.VaultManager.isVaultAvailable())) {
                        rankName = safe(rank.getName());
                        rankPrefix = safe(rank.getPrefix());
                        if (de.basti.backbone.ranks.RankManager.isPlayerRankPermanent(player.getUniqueId())) {
                            rankExpires = Lang.get("common.permanent");
                            rankTimeLeft = Lang.get("common.permanent");
                        } else {
                            long exp = de.basti.backbone.ranks.RankManager.getPlayerExpiresAt(player.getUniqueId());
                            if (exp > 0) {
                                rankExpires = DATE_FORMAT_MUTE.format(java.time.Instant.ofEpochMilli(exp));
                                rankTimeLeft = de.basti.backbone.ranks.RankManager.getFormattedPlayerRemainingTime(player.getUniqueId());
                            }
                        }
                    }
                    if (rankName.isEmpty()) {
                        rankName = safe(de.basti.backbone.util.vault.VaultManager.getExternalRankName(player));
                    }
                    if (rankPrefix.isEmpty()) {
                        rankPrefix = safe(de.basti.backbone.util.vault.VaultManager.getExternalRankPrefix(player));
                    }
                } catch (Throwable ignored) {}
            }
            if (text.contains("Prefix}") || text.contains("{prefix}") || text.contains("{nameColor}")) {
                try {
                    customPrefix = safe(de.basti.backbone.util.PrefixManager.getPrefix(player));
                    if (customPrefix.isEmpty()) {
                        customPrefix = safe(de.basti.backbone.util.vault.VaultManager.getExternalPlayerPrefix(player));
                    }
                    nameColor = safe(de.basti.backbone.util.PrefixManager.getNameColor(player));
                } catch (Throwable ignored) {}
            }
        }

        String uptime = getFormattedUptime();
        String rawPluginPrefix = Lang.getPrefix();
        String pluginPrefixFull = (rawPluginPrefix != null && !rawPluginPrefix.isEmpty()) ? rawPluginPrefix : "§9[Backbone] ";
        if (!pluginPrefixFull.endsWith(" ")) {
            pluginPrefixFull = pluginPrefixFull + " ";
        }
        String pluginPrefixTrimmed = pluginPrefixFull.trim();

        return text
                .replace("{pluginPrefix} ", safe(pluginPrefixFull))
                .replace("{pluginPrefix}", safe(pluginPrefixTrimmed))
                .replace("{plugin_prefix} ", safe(pluginPrefixFull))
                .replace("{plugin_prefix}", safe(pluginPrefixTrimmed))
                .replace("{plugin-prefix} ", safe(pluginPrefixFull))
                .replace("{plugin-prefix}", safe(pluginPrefixTrimmed))
                .replace("{Plugin-Prefix} ", safe(pluginPrefixFull))
                .replace("{Plugin-Prefix}", safe(pluginPrefixTrimmed))
                .replace("{Plugin-Präfix} ", safe(pluginPrefixFull))
                .replace("{Plugin-Präfix}", safe(pluginPrefixTrimmed))
                .replace("{plugin-präfix} ", safe(pluginPrefixFull))
                .replace("{plugin-präfix}", safe(pluginPrefixTrimmed))
                .replace("{playerPrefix}", safe(customPrefix))
                .replace("{playername}", safe(playerName))
                .replace("{playeruuid}", safe(playerUuid))
                .replace("{freemb}", String.valueOf(freeMb))
                .replace("{usedmb}", String.valueOf(usedMb))
                .replace("{maxmb}", String.valueOf(maxMb))
                .replace("{slots}", String.valueOf(slots))
                .replace("{players}", String.valueOf(players))
                .replace("{online}", String.valueOf(players))
                .replace("{maxplayers}", String.valueOf(slots))
                .replace("{TPS}", safe(tpsString))
                .replace("{tps}", safe(tpsString))
                .replace("{cpu}", safe(cpuString))
                .replace("{cpu_int}", safe(cpuIntString))
                .replace("{cpu_system}", safe(cpuSystemString))
                .replace("{uptime}", safe(uptime))
                .replace("{ping}", String.valueOf(ping))
                .replace("{health}", String.valueOf(health))
                .replace("{maxhealth}", String.valueOf(maxHealth))
                .replace("{food}", String.valueOf(food))
                .replace("{level}", String.valueOf(level))
                .replace("{gamemode}", safe(gamemode))
                .replace("{fly}", isFlying ? "true" : "false")
                .replace("{x}", safe(x))
                .replace("{y}", safe(y))
                .replace("{z}", safe(z))
                .replace("{rank}", safe(rankName))
                .replace("{rankPrefix}", safe(rankPrefix))
                .replace("{rankExpires}", safe(rankExpires))
                .replace("{rankTimeLeft}", safe(rankTimeLeft))
                .replace("{prefix}", safe(customPrefix))
                .replace("{nameColor}", safe(nameColor))
                .replace("{world_players}", String.valueOf(worldPlayers))
                .replace("{cash}", safe(cashString))
                .replace("{cashSymbol}", safe(cashSymbol))
                .replace("{cashCirc}", safe(cashCirc))
                .replace("{worldName}", safe(worldName))
                .replace("{world}", safe(worldName))
                .replace("{mute}", safe(muteString))
                .replace("{muteUntil}", safe(muteUntilString))
                .replace("{motdLine1}", text.contains("{motdLine1}") ? safe(MotdFormatter.getMotdLine(1)) : "")
                .replace("{motdLine2}", text.contains("{motdLine2}") ? safe(MotdFormatter.getMotdLine(2)) : "")
                .replace("{day}", String.valueOf(today.getDayOfMonth()))
                .replace("{month}", String.valueOf(today.getMonthValue()))
                .replace("{year}", String.valueOf(today.getYear()))
                .replace("{hh}", now.format(DateTimeFormatter.ofPattern("HH")))
                .replace("{mm}", now.format(DateTimeFormatter.ofPattern("mm")))
                .replace("{ss}", now.format(DateTimeFormatter.ofPattern("ss")));
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Löst alle <switch sek=X>Text1</s>Text2</s> Tags auf.
     */
    private static String parseSwitch(String text) {
        Matcher matcher = SWITCH_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String secondsStr = matcher.group(1);
            if (secondsStr == null) {
                secondsStr = matcher.group(2);
            }

            int seconds = 5;
            if (secondsStr != null) {
                try {
                    seconds = Math.max(1, Integer.parseInt(secondsStr.trim()));
                } catch (NumberFormatException ignored) {}
            }

            String content = matcher.group(3);
            if (content == null) {
                content = matcher.group(4);
            }
            if (content == null) content = "";

            String trimmedContent = content.endsWith("</s>") ? content.substring(0, content.length() - 4) : content;
            String[] items = trimmedContent.split("</s>", -1);
            if (items.length == 0) {
                matcher.appendReplacement(sb, "");
                continue;
            }

            long currentSeconds = System.currentTimeMillis() / 1000L;
            int index = (int) ((currentSeconds / seconds) % items.length);
            if (index < 0) index = 0;
            String selected = items[index];

            matcher.appendReplacement(sb, Matcher.quoteReplacement(selected));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Löst alle <if var op val>Wahr</if = false>Falsch</if> Tags auf.
     */
    private static String parseIf(String text) {
        Matcher matcher = IF_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String left = cleanOperand(matcher.group(1));
            String op = matcher.group(2).trim();
            String right = cleanOperand(matcher.group(3));
            String trueText = matcher.group(4) != null ? matcher.group(4) : "";
            String falseText = matcher.group(5) != null ? matcher.group(5) : "";

            boolean conditionMet = evaluateCondition(left, op, right);
            String result = conditionMet ? trueText : falseText;

            matcher.appendReplacement(sb, Matcher.quoteReplacement(result));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String cleanOperand(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // Anführungszeichen entfernen, falls vorhanden
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private static boolean evaluateCondition(String left, String op, String right) {
        // 1. Wahrheitswerte (Boolean)
        boolean leftIsBool = left.equalsIgnoreCase("true") || left.equalsIgnoreCase("false");
        boolean rightIsBool = right.equalsIgnoreCase("true") || right.equalsIgnoreCase("false");

        if (leftIsBool || rightIsBool) {
            boolean leftVal = Boolean.parseBoolean(left);
            boolean rightVal = Boolean.parseBoolean(right);
            return switch (op) {
                case "=", "==" -> leftVal == rightVal;
                case "!=" -> leftVal != rightVal;
                default -> false;
            };
        }

        // 2. Zahlenvergleich (Double / Long / Währungsformate wie 1.250,50 oder 10000)
        Double leftNum = parseNumber(left);
        Double rightNum = parseNumber(right);
        if (leftNum != null && rightNum != null) {
            return switch (op) {
                case "=", "==" -> Math.abs(leftNum - rightNum) < 0.0001;
                case "!=" -> Math.abs(leftNum - rightNum) >= 0.0001;
                case "<" -> leftNum < rightNum;
                case ">" -> leftNum > rightNum;
                case "<=" -> leftNum <= rightNum;
                case ">=" -> leftNum >= rightNum;
                default -> false;
            };
        }

        // 3. String-Vergleich
        return switch (op) {
            case "=", "==" -> left.equalsIgnoreCase(right);
            case "!=" -> !left.equalsIgnoreCase(right);
            default -> false;
        };
    }

    private static Double parseNumber(String str) {
        if (str == null || str.isBlank()) return null;
        String s = str.trim().replace("%", "");
        try {
            // Deutsches vs. Englisches Format behandeln
            if (s.contains(".") && s.contains(",")) {
                if (s.indexOf('.') < s.indexOf(',')) {
                    // Deutsch: 1.000,50 -> 1000.50
                    s = s.replace(".", "").replace(",", ".");
                } else {
                    // Englisch: 1,000.50 -> 1000.50
                    s = s.replace(",", "");
                }
            } else if (s.contains(",")) {
                s = s.replace(",", ".");
            } else {
                // Falls nur Punkte vorhanden sind (z.B. 1.000.000)
                int firstDot = s.indexOf('.');
                int lastDot = s.lastIndexOf('.');
                if (firstDot != lastDot) {
                    s = s.replace(".", "");
                }
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatCentering(String text) {
        String clean = text.replace("<center>", "").replace("</c>", "");
        int pixelWidth = MotdFormatter.getPixelWidth(clean);
        int padding = Math.max(0, (SCOREBOARD_TARGET_PIXELS - pixelWidth) / 2);
        int spacesCount = padding / SPACE_PIXELS;
        return " ".repeat(spacesCount) + clean;
    }
}
