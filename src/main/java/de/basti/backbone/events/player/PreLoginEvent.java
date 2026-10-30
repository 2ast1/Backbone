package de.basti.backbone.events.player;

import de.basti.backbone.util.automod.punish.BanUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

/**
 * Überwacht das asynchrone Pre-Login (AsyncPlayerPreLoginEvent)
 * und blockiert gebannte Spieler.
 */
public class PreLoginEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        String name = e.getName();

        // Prüfen, ob der Spieler gebannt ist (prüft auch automatischen Ablauf bei Temp-Bans)
        if (BanUtil.isBanned(uuid)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, BanUtil.getBanMessage(uuid, name));
            return;
        }

        UUID resolved = BanUtil.resolveBannedUuid(name);
        if (resolved != null && BanUtil.isBanned(resolved)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, BanUtil.getBanMessage(resolved, name));
        }
    }
}
