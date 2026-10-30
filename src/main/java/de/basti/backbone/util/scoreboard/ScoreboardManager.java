package de.basti.backbone.util.scoreboard;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentraler Manager für das Scoreboard-System.
 * Verwaltet das Laden aller Scoreboard-Dateien, Prioritäten, Welten-Zuordnungen,
 * den Scheduler-Tick-Loop und Spieler-Sitzungen.
 */
public class ScoreboardManager {

    private static final String FOLDER = "scoreboards";
    private static Backbone plugin;
    private static final Map<String, ScoreboardTemplate> templates = new ConcurrentHashMap<>();
    private static final Map<UUID, ScoreboardSession> sessions = new ConcurrentHashMap<>();
    private static BukkitTask updateTask;
    private static boolean initialized = false;

    public static void init(Backbone instance) {
        plugin = instance;
        createFolderAndExample();
        loadAllTemplates();

        // Bestehende Sessions für Online-Spieler anlegen
        for (Player p : Bukkit.getOnlinePlayers()) {
            setupPlayer(p);
        }

        // Zentraler 1-Tick Updater
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, ScoreboardManager::tickAll, 1L, 1L);
        initialized = true;
    }

    public static void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (ScoreboardSession session : sessions.values()) {
            session.destroy();
        }
        sessions.clear();
        templates.clear();
        initialized = false;
    }

    public static void reload() {
        loadAllTemplates();
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerBoard(p);
        }
    }

    private static void tickAll() {
        for (ScoreboardSession session : sessions.values()) {
            session.tick();
        }
    }

    public static void loadAllTemplates() {
        templates.clear();
        File folder = DataManager.getFolder(FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            createDefaultExample();
            files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        }

        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

                String name = cfg.getString("name", id);
                boolean enabled = cfg.getBoolean("enabled", true);
                int priority = cfg.getInt("priority", 0);
                List<String> worlds = cfg.getStringList("worlds");
                if (worlds.isEmpty() && cfg.isString("worlds")) {
                    worlds = List.of(cfg.getString("worlds"));
                } else if (worlds.isEmpty() && cfg.isString("world")) {
                    worlds = List.of(cfg.getString("world"));
                } else if (worlds.isEmpty()) {
                    worlds = List.of("*");
                }
                int interval = cfg.getInt("update-interval-ticks", 20);
                String title = cfg.getString("title", "&9&lBACKBONE");
                List<String> lines = cfg.getStringList("lines");
                if (lines.isEmpty() && cfg.isString("lines")) {
                    lines = List.of(cfg.getString("lines"));
                }

                ScoreboardTemplate template = new ScoreboardTemplate(id, name, enabled, priority, worlds, interval, title, lines);
                templates.put(id.toLowerCase(Locale.ROOT), template);
            }
        }
    }

    public static ScoreboardTemplate getBestTemplate(Player player) {
        if (!isScoreboardSystemEnabled()) {
            return null;
        }

        if (isPlayerScoreboardDisabled(player)) {
            return null;
        }

        String worldName = (player.getWorld() != null) ? player.getWorld().getName() : "";
        ScoreboardTemplate best = null;

        for (ScoreboardTemplate tmpl : templates.values()) {
            if (tmpl.matchesWorld(worldName)) {
                if (best == null || tmpl.getPriority() > best.getPriority()) {
                    best = tmpl;
                }
            }
        }

        return best;
    }

    public static void setupPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        ScoreboardSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new ScoreboardSession(player));
        ScoreboardTemplate best = getBestTemplate(player);
        session.setCurrentTemplate(best);
    }

    public static void removePlayer(Player player) {
        if (player == null) return;
        ScoreboardSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.destroy();
        }
    }

    public static void updatePlayerBoard(Player player) {
        if (player == null || !player.isOnline()) return;
        ScoreboardSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            setupPlayer(player);
            return;
        }
        ScoreboardTemplate best = getBestTemplate(player);
        session.setCurrentTemplate(best);
    }

    public static Collection<ScoreboardTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    // ==========================================
    // Config- und Toggle-Methoden
    // ==========================================

    public static boolean isScoreboardSystemEnabled() {
        if (!DataManager.getConfig().contains("scoreboard.enabled")) {
            return true;
        }
        return DataManager.getConfigBoolean("scoreboard.enabled");
    }

    public static boolean isPlayerToggleAllowed(Player player) {
        if (DataManager.getConfig().contains("scoreboard.toggle.allow-player-toggle")
                && !DataManager.getConfigBoolean("scoreboard.toggle.allow-player-toggle")) {
            return false;
        }
        if (player == null || player.getWorld() == null) return true;

        List<String> disabledWorlds = DataManager.getStringList("", "config.yml", "scoreboard.toggle.disabled-worlds");
        if (disabledWorlds == null || disabledWorlds.isEmpty()) {
            return true;
        }
        String currentWorld = player.getWorld().getName();
        for (String w : disabledWorlds) {
            if (w.equalsIgnoreCase(currentWorld)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPlayerScoreboardDisabled(Player player) {
        if (player == null) return false;
        return DataManager.getBoolean("", "playerdata.yml", player.getUniqueId() + ".scoreboard_disabled");
    }

    public static void setPlayerScoreboardDisabled(Player player, boolean disabled) {
        if (player == null) return;
        DataManager.set("", "playerdata.yml", player.getUniqueId() + ".scoreboard_disabled", disabled);
        updatePlayerBoard(player);
    }

    // ==========================================
    // example.yml Vorlage erstellen
    // ==========================================

    private static void createFolderAndExample() {
        File folder = DataManager.getFolder(FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File exampleFile = new File(folder, "example.yml");
        if (!exampleFile.exists()) {
            createDefaultExample();
        }
    }

    private static void createDefaultExample() {
        File folder = DataManager.getFolder(FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File example = new File(folder, "example.yml");
        if (example.exists()) return;

        FileConfiguration cfg = new YamlConfiguration();
        cfg.options().setHeader(List.of(
                "===========================================================",
                "              Backbone Scoreboard Configuration            ",
                "===========================================================",
                "This scoreboard serves as a template and documentation of all options.",
                "By default, 'enabled: false' is set so that it is not active right away.",
                "",
                "Available Placeholders:",
                "  -- Performance & System --",
                "  {TPS}                         -> Server TPS (e.g. 20.0)",
                "  {cpu}                         -> Server process CPU load in % (e.g. 4.5)",
                "  {cpu_system}                  -> Total system CPU load in %",
                "  {freemb}, {usedmb}, {maxmb}   -> RAM statistics in MB",
                "  {uptime}                      -> Server uptime (e.g. 3h 24m)",
                "",
                "  -- Player & Status --",
                "  {playername}, {playeruuid}    -> Name and UUID of the player",
                "  {ping}                        -> Player latency/ping in ms (e.g. 25)",
                "  {health}, {maxhealth}         -> Current and maximum health points",
                "  {food}, {level}               -> Food level (0-20) and experience level",
                "  {gamemode}, {fly}             -> Gamemode and flight status (true/false)",
                "  {x}, {y}, {z}                 -> Block coordinates of the player",
                "  {rank}, {rankPrefix}          -> Name and prefix of the Backbone rank",
                "  {playerPrefix}, {prefix}      -> Custom player prefix",
                "  {nameColor}                   -> Custom player name color",
                "  {pluginPrefix}                -> Backbone plugin prefix",
                "  {mute}, {muteUntil}           -> Mute status (true/false) and expiration date",
                "",
                "  -- Server & World --",
                "  {slots}, {players}, {online}  -> Max slots and players online",
                "  {worldName}, {world_players}  -> Current world and players in the same world",
                "  {motdLine1}, {motdLine2}      -> First and second line of the server MOTD",
                "  {hh}, {mm}, {ss}              -> Standardized two-digit time (e.g. 14:05:09)",
                "  {day}, {month}, {year}        -> Date (e.g. 24, 10, 2026)",
                "",
                "  -- Economy --",
                "  {cash}, {cashSymbol}          -> Balance and currency symbol",
                "  {cashCirc}                    -> Total money in circulation",
                "",
                "Special Formatting Tags:",
                "  <center>Text</c>              -> Centers lines (Title is centered automatically by Minecraft!)",
                "  <switch sec=30>A</s>B</s>     -> Switches between A and B every 30 seconds",
                "  <if condition>Yes<else>No</if> -> Conditional display (<, >, <=, >=, =, !=, supports <else> and </if = false>)",
                "==========================================================="
        ));
        cfg.set("name", "Example Scoreboard");
        cfg.set("enabled", false);
        cfg.set("priority", 0);
        cfg.set("worlds", List.of("*"));
        cfg.set("update-interval-ticks", 20);
        cfg.set("title", "&9&lBACKBONE");

        List<String> lines = List.of(
                "&7&m---------------------",
                "&fPlayer: &a{playername}",
                "<if {rank} != \"\">&fRank: &e{rank}</if>",
                "&fMoney: &e{cash} {cashSymbol}",
                "<if {cash} >= 10000>&6★ VIP Status<else>&7Normal Status</if>",
                "",
                "&fStatus: <if {mute} = true>&cMuted ({muteUntil})<else>&aActive</if>",
                "<switch sec=10>&fWorld: &b{worldName}</s>&fTPS: &e{TPS}</s>&fPing: &a{ping}ms</s>",
                "<switch sec=15>&fOnline: &a{players}/{slots}</s>&fRAM: &e{usedmb}/{maxmb} MB</s>&fCPU: &e{cpu}%</s>",
                "",
                "&7&o{hh}:{mm}:{ss}",
                "&7&m---------------------"
        );
        cfg.set("lines", lines);

        try {
            cfg.save(example);
        } catch (IOException e) {
            Backbone.warnMsg("Could not create example.yml: " + e.getMessage());
        }
    }
}
