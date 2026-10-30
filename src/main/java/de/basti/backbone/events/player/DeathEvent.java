package de.basti.backbone.events.player;

import de.basti.backbone.util.locations.DeathManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Event-Listener für Spielertode zur Speicherung des letzten Todesortes (/back).
 */
public class DeathEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DeathManager.setLastDeathLocation(player.getUniqueId(), player.getLocation());
    }
}
