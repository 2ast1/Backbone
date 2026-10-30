package de.basti.backbone.events.server;

import de.basti.backbone.commands.maintenance.MaintenanceCommand;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.MotdFormatter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Event listener for server list ping (ServerListPingEvent).
 * Manages the normal MOTD and dynamic maintenance MOTD,
 * including text centering (&lt;center&gt;) and placeholders ({players}, {slots}, {TPS}, RAM, etc.).
 */
public class ServerListPingEvent implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onServerListPing(org.bukkit.event.server.ServerListPingEvent event) {
        boolean maintenance = DataManager.getConfigBoolean("server.maintenance");
        String motdText;

        if (maintenance) {
            boolean shouldChangeMotd = DataManager.getBoolean("", "config.yml", "server.motd.maintenance.change");
            if (!DataManager.contains("", "config.yml", "server.motd.maintenance.change")) {
                shouldChangeMotd = true;
            }

            if (shouldChangeMotd) {
                String sessionMsg = MaintenanceCommand.getSessionMessage();
                if (sessionMsg != null && !sessionMsg.isBlank()) {
                    motdText = sessionMsg;
                } else {
                    motdText = DataManager.getString("", "config.yml", "server.motd.maintenance.text");
                    if (motdText == null || motdText.isBlank()) {
                        motdText = Lang.getRaw("maintenance.default-motd");
                    }
                }
            } else {
                motdText = getNormalMotd();
            }
        } else {
            motdText = getNormalMotd();
        }

        String formatted = MotdFormatter.format(motdText);
        event.setMotd(formatted);

        try {
            for (java.util.Iterator<org.bukkit.entity.Player> it = event.iterator(); it.hasNext(); ) {
                org.bukkit.entity.Player p = it.next();
                if (de.basti.backbone.util.HideManager.isHidden(p)) {
                    it.remove();
                }
            }
        } catch (UnsupportedOperationException ignored) {}
    }

    private String getNormalMotd() {
        String normal = DataManager.getString("", "config.yml", "server.motd.normal");
        if (normal == null || normal.isBlank()) {
            return "A Minecraft Server";
        }
        return normal;
    }
}
