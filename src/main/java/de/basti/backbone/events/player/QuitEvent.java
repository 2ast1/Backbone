package de.basti.backbone.events.player;

import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.scoreboard.ScoreboardParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event-Klasse für das Verlassen des Servers (PlayerQuitEvent).
 * Sichert Inventare, formatiert server.leave und bereinigt Permissions zur Vermeidung von Speicherlecks.
 */
public class QuitEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Aktive Setup-Wizard-Session beenden, falls vorhanden
        SetupWizardManager.removeSession(player);

        // Benutzerdefinierte Leave-Nachricht (server.leave in config.yml)
        boolean hiddenPlayer = de.basti.backbone.util.HideManager.isHidden(player);
        String leaveTemplate = DataManager.getConfigString("server.leave");
        if (hiddenPlayer) {
            event.setQuitMessage(null);
            if (leaveTemplate != null && !leaveTemplate.isBlank()) {
                String formattedLeave = ScoreboardParser.formatJoinLeaveMessage(leaveTemplate, player);
                Bukkit.getConsoleSender().sendMessage(formattedLeave);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player) && !SetupWizardManager.isInSetup(online) && de.basti.backbone.util.HideManager.canSee(online, player)) {
                        online.sendMessage(formattedLeave);
                    }
                }
            }
        } else if (leaveTemplate != null && !leaveTemplate.isBlank()) {
            String formattedLeave = ScoreboardParser.formatJoinLeaveMessage(leaveTemplate, player);
            if (SetupWizardManager.hasActiveSessions()) {
                event.setQuitMessage(null);
                Bukkit.getConsoleSender().sendMessage(formattedLeave);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player) && !SetupWizardManager.isInSetup(online)) {
                        online.sendMessage(formattedLeave);
                    }
                }
            } else {
                event.setQuitMessage(formattedLeave);
            }
        }

        // Falls ein Admin gerade das Live-Inventar oder die Enderchest dieses Spielers offen hat, GUI schließen
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || online.getOpenInventory() == null) continue;
            org.bukkit.inventory.Inventory top = online.getOpenInventory().getTopInventory();
            if (top != null) {
                if (top.getHolder() instanceof de.basti.backbone.commands.inventory.InventoryViewSession session
                        && player.getUniqueId().equals(session.getTargetUuid())) {
                    if (session.isModified()) {
                        de.basti.backbone.events.inventory.InventoryClickEvent.applyGuiChangesToTarget(session);
                    }
                    online.closeInventory();
                } else if (top.equals(player.getEnderChest())) {
                    online.closeInventory();
                }
            }
        }

        // Private Message Reply-Ziele bereinigen
        de.basti.backbone.util.message.PrivateMessageManager.removeReplyTarget(player.getUniqueId());

        // Inventar und Enderchest für Offline-Zugriff sichern
        OfflineInventoryManager.savePlayer(player);

        // PermissionAttachment deregistrieren & RAM freigeben
        if (RankManager.getPermissionService() != null) {
            RankManager.getPermissionService().removeAttachment(player);
        }
    }
}
