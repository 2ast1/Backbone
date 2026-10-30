package de.basti.backbone.util.automod.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltet Verwarnungen (Warns) von Spielern.
 */
public final class WarnManager {

    private WarnManager() {}

    /**
     * Verwarnt einen Spieler, dokumentiert die Verwarnung in der Historie und benachrichtigt ihn, falls online.
     */
    public static void warnPlayer(UUID targetUuid, String targetName, String reason, String operator) {
        if (targetUuid == null) return;

        HistoryUtil.addEntry(targetUuid, "WARN", reason, operator, "-");

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(AutoMod.getPrefix() + " " + Lang.get("punish.warn.received", "operator", operator, "reason", reason));
        }
    }

    /**
     * Gibt alle Verwarnungen eines Spielers zurück.
     */
    public static List<HistoryUtil.HistoryEntry> getWarnings(UUID targetUuid) {
        if (targetUuid == null) return List.of();

        List<HistoryUtil.HistoryEntry> history = HistoryUtil.getHistory(targetUuid);
        List<HistoryUtil.HistoryEntry> warns = new ArrayList<>();
        for (HistoryUtil.HistoryEntry entry : history) {
            if ("WARN".equalsIgnoreCase(entry.action())) {
                warns.add(entry);
            }
        }
        return warns;
    }
}
