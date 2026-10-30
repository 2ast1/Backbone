package de.basti.backbone.events.player;

import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Event-Klasse für Kicks von Spielern (PlayerKickEvent).
 * Sichert Inventare und bereinigt Permissions sauber.
 */
public class KickEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        de.basti.backbone.util.SetupWizardManager.removeSession(player);

        // Inventar und Enderchest für Offline-Zugriff sichern
        OfflineInventoryManager.savePlayer(player);

        // PermissionAttachment deregistrieren & RAM freigeben
        if (RankManager.getPermissionService() != null) {
            RankManager.getPermissionService().removeAttachment(player);
        }
    }
}
