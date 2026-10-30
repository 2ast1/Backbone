package de.basti.backbone.events.player;

import de.basti.backbone.commands.maintenance.MaintenanceCommand;
import de.basti.backbone.util.DataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Überwacht das Spieler-Login (AsyncPlayerPreLoginEvent) und prüft,
 * ob der Wartungsmodus aktiv ist und der Spieler berechtigt ist beizutreten.
 */
public class LoginEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!DataManager.getConfigBoolean("server.maintenance")) {
            return;
        }

        if (!MaintenanceCommand.hasMaintenancePermission(event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MaintenanceCommand.getMaintenanceKickMessage());
        }
    }
}
