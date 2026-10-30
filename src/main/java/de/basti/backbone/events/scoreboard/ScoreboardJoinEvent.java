package de.basti.backbone.events.scoreboard;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Event-Klasse für die Scoreboard-Initialisierung beim Serverbeitritt (PlayerJoinEvent).
 */
public class ScoreboardJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Sofort initialisieren
        ScoreboardManager.setupPlayer(player);

        // Nach 2 Ticks erneut validieren, falls andere Plugins/Events die Welt noch initialisieren
        Backbone plugin = Backbone.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    ScoreboardManager.updatePlayerBoard(player);
                }
            }, 2L);
        }
    }
}
