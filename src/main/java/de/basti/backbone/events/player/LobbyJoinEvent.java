package de.basti.backbone.events.player;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.locations.LobbyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Event-Klasse für automatischen Teleport in die Lobby beim Serverbeitritt (PlayerJoinEvent).
 * Greift nur, wenn in der config.yml lobby.teleport-on-join auf true steht.
 */
public class LobbyJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onLobbyJoin(PlayerJoinEvent event) {
        if (DataManager.getConfig().contains("lobby.enabled") && !DataManager.getConfigBoolean("lobby.enabled")) {
            return;
        }
        if (!LobbyManager.isTeleportOnJoin()) {
            return;
        }

        Location lobby = LobbyManager.getLobbyLocation();
        if (lobby != null) {
            Player player = event.getPlayer();
            player.teleport(lobby);
        }
    }
}
