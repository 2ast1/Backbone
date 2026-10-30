package de.basti.backbone.events.scoreboard;

import de.basti.backbone.util.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Event-Klasse für das Aktualisieren von Scoreboards bei Weltenwechseln (PlayerChangedWorldEvent).
 */
public class ScoreboardWorldChangeEvent implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.isOnline()) {
            ScoreboardManager.updatePlayerBoard(player);
        }
    }
}
