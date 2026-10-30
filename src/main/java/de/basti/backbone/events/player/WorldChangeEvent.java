package de.basti.backbone.events.player;

import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.PrefixManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Event-Klasse für Weltenwechsel von Spielern (PlayerChangedWorldEvent).
 * Aktualisiert Tabliste, weltspezifische Berechtigungen und Spieler-Sichtbarkeiten.
 */
public class WorldChangeEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        PrefixManager.updateTabList(player);

        if (RankManager.getPermissionService() != null && RankManager.isRanksEnabled()) {
            RankManager.getPermissionService().applyPermissions(player);
        }

        HideManager.updateVisibilityForViewer(player);
        HideManager.updateVisibilityForTarget(player);
    }
}
