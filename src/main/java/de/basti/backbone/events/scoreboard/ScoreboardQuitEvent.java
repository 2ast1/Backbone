package de.basti.backbone.events.scoreboard;

import de.basti.backbone.util.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event-Klasse für das Bereinigen von Scoreboards beim Verlassen des Servers (PlayerQuitEvent).
 */
public class ScoreboardQuitEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            ScoreboardManager.removePlayer(player);
        }
    }
}
