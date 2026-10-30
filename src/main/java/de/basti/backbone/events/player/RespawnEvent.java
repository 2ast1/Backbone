package de.basti.backbone.events.player;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.PrefixManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Event-Klasse für das Respawn von Spielern (PlayerRespawnEvent).
 * Aktualisiert Tabliste und Präfixe nach dem Respawn.
 */
public class RespawnEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Backbone plugin = Backbone.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    PrefixManager.updateTabList(player);
                }
            }, 1L);
        }
    }
}
