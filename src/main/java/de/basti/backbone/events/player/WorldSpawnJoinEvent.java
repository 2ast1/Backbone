package de.basti.backbone.events.player;

import de.basti.backbone.util.locations.SpawnManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Event-Klasse für automatischen Teleport zum Spawnpunkt beim Weltenwechsel (PlayerChangedWorldEvent).
 * Greift nur, wenn für die Zielwelt in locations/spawns.yml spawn-on-join-world auf true steht.
 */
public class WorldSpawnJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (de.basti.backbone.util.DataManager.getConfig().contains("spawn.enabled")
                && !de.basti.backbone.util.DataManager.getConfigBoolean("spawn.enabled")) {
            return;
        }
        Player player = event.getPlayer();
        World currentWorld = player.getWorld();

        if (SpawnManager.isSpawnOnJoin(currentWorld)) {
            Location spawn = SpawnManager.getWorldSpawn(currentWorld);
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
    }
}
