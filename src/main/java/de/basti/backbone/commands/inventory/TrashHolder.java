package de.basti.backbone.commands.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

/**
 * Holder für das Mülleimer-Inventar (/trash).
 * Alle Items in diesem Inventar werden beim Schließen dauerhaft gelöscht.
 */
public class TrashHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }
}
