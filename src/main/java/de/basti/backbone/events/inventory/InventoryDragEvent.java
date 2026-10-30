package de.basti.backbone.events.inventory;

import de.basti.backbone.Backbone;
import de.basti.backbone.commands.inventory.InventoryViewSession;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Event-Klasse für Drag-Interaktionen in Inventaren (InventoryDragEvent).
 * Verhindert das Hineinziehen von Items in gesperrte oder Trenner-Slots.
 */
public class InventoryDragEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof InventoryViewSession session) {
            if (session.isReadOnly()) {
                event.setCancelled(true);
                return;
            }
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < 54) {
                    if (rawSlot >= 41 && rawSlot <= 53) {
                        event.setCancelled(true);
                        return;
                    }
                    ItemStack item = event.getInventory().getItem(rawSlot);
                    if (InventoryViewSession.isPlaceholder(item)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            session.markModified();
            Bukkit.getScheduler().runTask(Backbone.getInstance(), () -> {
                InventoryClickEvent.applyGuiChangesToTarget(session);
            });
        } else if (event.getInventory().getHolder() instanceof de.basti.backbone.commands.inventory.EnderchestViewSession ecSession) {
            if (ecSession.isReadOnly()) {
                event.setCancelled(true);
                return;
            }
            ecSession.markModified();
        }
    }
}
