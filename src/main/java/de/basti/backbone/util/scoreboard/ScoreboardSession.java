package de.basti.backbone.util.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.List;

/**
 * Verwaltet ein individuelles, flimmerfreies Bukkit-Scoreboard für einen Spieler.
 * Nutzt Teams und statische Farbcode-Einträge, um flüssige Aktualisierungen ohne Flackern zu garantieren.
 */
public class ScoreboardSession {

    private static final int MAX_LINES = 15;
    // 15 eindeutige, unsichtbare Einträge für die Zeilen (Scores 15 bis 1)
    private static final String[] LINE_ENTRIES = new String[MAX_LINES];

    static {
        for (int i = 0; i < MAX_LINES; i++) {
            LINE_ENTRIES[i] = ChatColor.values()[i].toString() + ChatColor.RESET;
        }
    }

    private final Player player;
    private Scoreboard board;
    private Objective objective;
    private ScoreboardTemplate currentTemplate;
    private int tickCount = 0;

    public ScoreboardSession(Player player) {
        this.player = player;
        initBoard();
    }

    private void initBoard() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        this.board = manager.getNewScoreboard();
        this.objective = board.registerNewObjective("bb_sb", Criteria.DUMMY, "Backbone");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Teams für jede der 15 Zeilen initialisieren
        for (int i = 0; i < MAX_LINES; i++) {
            String teamName = "bb_l_" + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            String entry = LINE_ENTRIES[i];
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }

        player.setScoreboard(board);
    }

    public Player getPlayer() {
        return player;
    }

    public ScoreboardTemplate getCurrentTemplate() {
        return currentTemplate;
    }

    public void setCurrentTemplate(ScoreboardTemplate template) {
        this.currentTemplate = template;
        this.tickCount = 0;
        if (template != null) {
            update();
        } else {
            clearBoard();
        }
    }

    /**
     * Wird in jedem Scheduler-Tick aufgerufen. Aktualisiert das Scoreboard
     * entsprechend der im Template definierten updateIntervalTicks.
     */
    public void tick() {
        if (currentTemplate == null || !currentTemplate.isEnabled()) {
            return;
        }

        tickCount++;
        if (tickCount % currentTemplate.getUpdateIntervalTicks() == 0) {
            update();
        }
    }

    /**
     * Führt eine sofortige Aktualisierung aller Zeilen und des Titels durch.
     */
    public void update() {
        if (currentTemplate == null || !player.isOnline()) {
            return;
        }

        if (board == null || objective == null) {
            initBoard();
            if (board == null || objective == null) return;
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        // 1. Titel parsen und aktualisieren
        String parsedTitle = ScoreboardParser.parse(currentTemplate.getTitle(), player);
        if (parsedTitle == null || parsedTitle.isEmpty()) {
            parsedTitle = " ";
        } else if (parsedTitle.length() > 128) {
            parsedTitle = parsedTitle.substring(0, 128);
        }
        objective.setDisplayName(parsedTitle);

        // 2. Zeilen parsen und setzen
        List<String> rawLines = currentTemplate.getLines();
        int lineCount = Math.min(rawLines.size(), MAX_LINES);

        for (int i = 0; i < lineCount; i++) {
            String raw = rawLines.get(i);
            String parsed = ScoreboardParser.parse(raw, player);

            String teamName = "bb_l_" + i;
            Team team = board.getTeam(teamName);
            String entry = LINE_ENTRIES[i];

            if (team != null) {
                // Präfix der Zeile setzen (unterstützt bis zu 128 Zeichen in modernen Minecraft-Versionen)
                if (parsed.length() > 128) {
                    parsed = parsed.substring(0, 128);
                }
                team.setPrefix(parsed);
                team.setSuffix("");
            }

            // Score-Nummer von oben nach unten (z.B. Zeile 1 = Score 15)
            int scoreValue = lineCount - i;
            Score score = objective.getScore(entry);
            score.setScore(scoreValue);
        }

        // Nicht genutzte Zeilen aus dem Scoreboard entfernen
        for (int i = lineCount; i < MAX_LINES; i++) {
            String entry = LINE_ENTRIES[i];
            board.resetScores(entry);
        }
    }

    public void clearBoard() {
        if (board != null) {
            for (int i = 0; i < MAX_LINES; i++) {
                board.resetScores(LINE_ENTRIES[i]);
            }
        }
        if (objective != null) {
            objective.setDisplayName("");
        }
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    public void destroy() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {}
        }
        if (board != null) {
            for (int i = 0; i < MAX_LINES; i++) {
                Team team = board.getTeam("bb_l_" + i);
                if (team != null) {
                    try {
                        team.unregister();
                    } catch (IllegalStateException ignored) {}
                }
            }
        }
    }
}
