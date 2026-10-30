package de.basti.backbone.util.inventory;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Datencontainer für die Offline-Inventardaten eines Spielers.
 * Speichert Hauptinventar (36 Slots), Rüstung (4 Slots), Schildhand (Offhand)
 * und die Enderchest (27 Slots).
 */
public class OfflinePlayerData {

    private final ItemStack[] storageContents; // 0-35 (Hotbar + Hauptinventar)
    private final ItemStack[] armorContents;   // 0=Boots, 1=Leggings, 2=Chestplate, 3=Helmet
    private ItemStack offHand;
    private final ItemStack[] enderChestContents; // 0-26 (Enderchest)
    private boolean modified = false;
    private boolean readOnly = false;

    public OfflinePlayerData() {
        this.storageContents = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.offHand = null;
        this.enderChestContents = new ItemStack[27];
    }

    public OfflinePlayerData(ItemStack[] storage, ItemStack[] armor, ItemStack offHand, ItemStack[] enderChest) {
        this(storage, armor, offHand, enderChest, false);
    }

    public OfflinePlayerData(ItemStack[] storage, ItemStack[] armor, ItemStack offHand, ItemStack[] enderChest, boolean readOnly) {
        this.storageContents = storage != null ? Arrays.copyOf(storage, 36) : new ItemStack[36];
        this.armorContents = armor != null ? Arrays.copyOf(armor, 4) : new ItemStack[4];
        this.offHand = offHand;
        this.enderChestContents = enderChest != null ? Arrays.copyOf(enderChest, 27) : new ItemStack[27];
        this.readOnly = readOnly;
    }

    public ItemStack[] getStorageContents() {
        return storageContents;
    }

    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    public ItemStack getOffHand() {
        return offHand;
    }

    public void setOffHand(ItemStack offHand) {
        this.offHand = offHand;
        this.modified = true;
    }

    public ItemStack[] getEnderChestContents() {
        return enderChestContents;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
