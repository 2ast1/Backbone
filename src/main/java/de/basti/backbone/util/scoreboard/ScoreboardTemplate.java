package de.basti.backbone.util.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repräsentiert eine geladene Scoreboard-Konfiguration aus scoreboards/<datei>.yml.
 */
public class ScoreboardTemplate {

    private final String id;
    private final String name;
    private final boolean enabled;
    private final int priority;
    private final List<String> worlds;
    private final int updateIntervalTicks;
    private final String title;
    private final List<String> lines;

    public ScoreboardTemplate(String id, String name, boolean enabled, int priority, List<String> worlds, int updateIntervalTicks, String title, List<String> lines) {
        this.id = id;
        this.name = (name != null && !name.isBlank()) ? name : id;
        this.enabled = enabled;
        this.priority = priority;
        this.worlds = (worlds != null) ? new ArrayList<>(worlds) : List.of("*");
        this.updateIntervalTicks = Math.max(1, updateIntervalTicks);
        this.title = (title != null) ? title : "&9&lBACKBONE";
        this.lines = (lines != null) ? new ArrayList<>(lines) : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getWorlds() {
        return Collections.unmodifiableList(worlds);
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Prüft, ob dieses Scoreboard für die angegebene Welt aktiviert ist.
     */
    public boolean matchesWorld(String worldName) {
        if (!enabled) return false;
        if (worldName == null || worldName.isBlank()) return true;
        for (String w : worlds) {
            if (w.equalsIgnoreCase("*") || w.equalsIgnoreCase("all") || w.equalsIgnoreCase(worldName.trim())) {
                return true;
            }
        }
        return false;
    }
}
