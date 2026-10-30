package de.basti.backbone.events.inventory;

import de.basti.backbone.commands.inventory.EnderchestViewSession;
import de.basti.backbone.commands.inventory.InventoryViewSession;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.inventory.OfflinePlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Event-Klasse für das Schließen von Inventaren (InventoryCloseEvent).
 * Speichert Änderungen an Online- und Offline-Inventaren sowie Offline-Enderchests.
 */
public class InventoryCloseEvent implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        Inventory inv = event.getInventory();

        // 1. /inventory GUI geschlossen
        if (inv.getHolder() instanceof InventoryViewSession session) {
            if (!session.isReadOnly() && session.isModified()) {
                InventoryClickEvent.applyGuiChangesToTarget(session);
            }
        }

        // 2. /enderchest Offline-GUI geschlossen
        if (inv.getHolder() instanceof EnderchestViewSession session) {
            if (session.isReadOnly() || !session.isModified()) {
                return;
            }
            UUID targetUuid = session.getTargetUuid();
            ItemStack[] contents = new ItemStack[27];
            for (int i = 0; i < 27; i++) {
                contents[i] = inv.getItem(i);
            }
            org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(targetUuid);
            if (online != null && online.isOnline()) {
                online.getEnderChest().setContents(contents);
                return;
            }
            OfflinePlayerData data = OfflineInventoryManager.loadPlayerData(targetUuid);
            System.arraycopy(contents, 0, data.getEnderChestContents(), 0, 27);
            OfflineInventoryManager.saveOfflineData(targetUuid, data, true);
        }

        // 3. /trash Mülleimer geschlossen -> Inhalt dauerhaft vernichten
        if (inv.getHolder() instanceof de.basti.backbone.commands.inventory.TrashHolder) {
            inv.clear();
        }
    }
}
