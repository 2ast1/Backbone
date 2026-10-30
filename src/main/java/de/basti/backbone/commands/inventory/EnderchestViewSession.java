package de.basti.backbone.commands.inventory;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.inventory.OfflinePlayerData;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Session holder for the offline Enderchest view.
 */
public class EnderchestViewSession implements InventoryHolder {

    private final UUID targetUuid;
    private final String targetName;
    private final Inventory inventory;
    private boolean modified = false;
    private boolean readOnly = false;

    public EnderchestViewSession(UUID targetUuid, String targetName) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.inventory = Bukkit.createInventory(this, 27, Lang.get("enderchest.gui-title", "player", targetName));

        // Load offline data
        OfflinePlayerData data = OfflineInventoryManager.loadPlayerData(targetUuid);
        if (data.isReadOnly()) {
            this.readOnly = true;
        }
        ItemStack[] contents = data.getEnderChestContents();
        if (contents != null) {
            for (int i = 0; i < Math.min(contents.length, 27); i++) {
                inventory.setItem(i, contents[i]);
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isModified() {
        return modified;
    }

    public void markModified() {
        if (!readOnly) {
            this.modified = true;
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
