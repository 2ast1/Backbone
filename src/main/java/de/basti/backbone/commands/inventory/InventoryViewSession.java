package de.basti.backbone.commands.inventory;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.inventory.OfflinePlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Session holder for the interactive /inventory GUI.
 * Manages layout for online and offline players including armor slots,
 * offhand, cursor item, and advanced UI slots (crafting, anvil, enchanting, smithing).
 */
public class InventoryViewSession implements InventoryHolder {

    public static final NamespacedKey PLACEHOLDER_KEY = Backbone.getInstance() != null
            ? new NamespacedKey(Backbone.getInstance(), "gui_placeholder")
            : NamespacedKey.fromString("backbone:gui_placeholder");

    private final UUID targetUuid;
    private final String targetName;
    private final boolean offline;
    private final boolean advancedPerm;
    private final Inventory inventory;
    private boolean modified = false;
    private boolean readOnly = false;

    public InventoryViewSession(UUID targetUuid, String targetName, boolean offline, boolean advancedPerm) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.offline = offline;
        this.advancedPerm = advancedPerm;
        this.inventory = Bukkit.createInventory(this, 54, Lang.get("inventory.gui.title", "player", targetName));
        render();
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

    public boolean isOffline() {
        return offline;
    }

    public boolean hasAdvancedPerm() {
        return advancedPerm;
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

    /**
     * Builds the GUI layout.
     */
    public void render() {
        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        OfflinePlayerData data;

        ItemStack[] storage;
        ItemStack[] armor;
        ItemStack offhand;
        ItemStack cursor = null;

        if (onlineTarget != null && onlineTarget.isOnline()) {
            storage = onlineTarget.getInventory().getStorageContents();
            armor = onlineTarget.getInventory().getArmorContents(); // 0=boots, 1=leggings, 2=chestplate, 3=helmet
            offhand = onlineTarget.getInventory().getItemInOffHand();
            InventoryView view = onlineTarget.getOpenInventory();
            if (view != null) {
                cursor = view.getCursor();
            }
        } else {
            data = OfflineInventoryManager.loadPlayerData(targetUuid);
            if (data.isReadOnly()) {
                this.readOnly = true;
            }
            storage = data.getStorageContents();
            armor = data.getArmorContents();
            offhand = data.getOffHand();
        }

        // ==========================================
        // Rows 1-3 (Slots 0-26): Main inventory (Player slots 9-35)
        // ==========================================
        for (int i = 0; i < 27; i++) {
            int playerSlot = i + 9;
            ItemStack stack = (storage != null && playerSlot < storage.length) ? storage[playerSlot] : null;
            inventory.setItem(i, stack);
        }

        // ==========================================
        // Row 4 (Slots 27-35): Hotbar (Player slots 0-8)
        // ==========================================
        for (int i = 0; i < 9; i++) {
            ItemStack stack = (storage != null && i < storage.length) ? storage[i] : null;
            inventory.setItem(27 + i, stack);
        }

        // ==========================================
        // Row 5 (Slots 36-44): Armor, Offhand, Cursor & Info
        // ==========================================
        // Slot 36: Helmet (Armor Index 3)
        ItemStack helmet = (armor != null && armor.length > 3) ? armor[3] : null;
        inventory.setItem(36, (helmet != null && !helmet.getType().isAir()) ? helmet : createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-helmet")));

        // Slot 37: Chestplate (Armor Index 2)
        ItemStack chest = (armor != null && armor.length > 2) ? armor[2] : null;
        inventory.setItem(37, (chest != null && !chest.getType().isAir()) ? chest : createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-chestplate")));

        // Slot 38: Leggings (Armor Index 1)
        ItemStack legs = (armor != null && armor.length > 1) ? armor[1] : null;
        inventory.setItem(38, (legs != null && !legs.getType().isAir()) ? legs : createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-leggings")));

        // Slot 39: Boots (Armor Index 0)
        ItemStack boots = (armor != null && armor.length > 0) ? armor[0] : null;
        inventory.setItem(39, (boots != null && !boots.getType().isAir()) ? boots : createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-boots")));

        // Slot 40: Offhand
        inventory.setItem(40, (offhand != null && !offhand.getType().isAir()) ? offhand : createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-offhand")));

        // Slot 41: Player's cursor item
        if (cursor != null && !cursor.getType().isAir()) {
            ItemStack cursorDisplay = cursor.clone();
            ItemMeta meta = cursorDisplay.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(Lang.get("inventory.gui.cursor-lore"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
                cursorDisplay.setItemMeta(meta);
            }
            inventory.setItem(41, cursorDisplay);
        } else {
            String cursorText = offline ? Lang.get("inventory.gui.cursor-offline") : Lang.get("inventory.gui.cursor-empty");
            inventory.setItem(41, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, cursorText));
        }

        // Slot 42: Separator
        inventory.setItem(42, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE, "§8|"));

        // Slot 43: Player head with info
        inventory.setItem(43, createPlayerInfoHead(onlineTarget));

        // Slot 44: Separator / UI info
        inventory.setItem(44, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE, Lang.get("inventory.gui.row6-info")));

        // ==========================================
        // Row 6 (Slots 45-53): Temporary / Open UI slots
        // Requires backbone.inventory.advanced
        // ==========================================
        renderAdvancedRow(onlineTarget);
    }

    private void renderAdvancedRow(Player onlineTarget) {
        if (!advancedPerm) {
            for (int i = 45; i <= 53; i++) {
                inventory.setItem(i, createPlaceholder(Material.RED_STAINED_GLASS_PANE, Lang.get("inventory.gui.advanced-locked-title"), Lang.get("inventory.gui.advanced-locked-lore")));
            }
            return;
        }

        if (offline || onlineTarget == null || !onlineTarget.isOnline()) {
            for (int i = 45; i <= 53; i++) {
                inventory.setItem(i, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.temp-ui-offline")));
            }
            return;
        }

        InventoryView view = onlineTarget.getOpenInventory();
        Inventory top = (view != null) ? view.getTopInventory() : null;
        InventoryType type = (top != null) ? top.getType() : null;

        if (top == null || type == null || type == InventoryType.CRAFTING && isInventoryEmpty(top)) {
            for (int i = 45; i <= 53; i++) {
                inventory.setItem(i, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.temp-ui-none")));
            }
            return;
        }

        switch (type) {
            case CRAFTING -> {
                inventory.setItem(45, createPlaceholder(Material.CRAFTING_TABLE, Lang.get("inventory.gui.crafting-2x2")));
                inventory.setItem(46, wrapPreviewItem(top.getItem(1)));
                inventory.setItem(47, wrapPreviewItem(top.getItem(2)));
                inventory.setItem(48, wrapPreviewItem(top.getItem(3)));
                inventory.setItem(49, wrapPreviewItem(top.getItem(4)));
                inventory.setItem(50, createPlaceholder(Material.ARROW, "§8➔"));
                inventory.setItem(51, wrapPreviewItem(top.getItem(0))); // Result
                inventory.setItem(52, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-"));
                inventory.setItem(53, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-"));
            }
            case WORKBENCH -> {
                inventory.setItem(45, createPlaceholder(Material.CRAFTING_TABLE, Lang.get("inventory.gui.workbench")));
                for (int slot = 1; slot <= 6; slot++) {
                    inventory.setItem(45 + slot, wrapPreviewItem(top.getItem(slot)));
                }
                inventory.setItem(52, createPlaceholder(Material.ARROW, "§8➔"));
                inventory.setItem(53, wrapPreviewItem(top.getItem(0))); // Result
            }
            case ANVIL -> {
                inventory.setItem(45, createPlaceholder(Material.ANVIL, Lang.get("inventory.gui.anvil")));
                inventory.setItem(46, wrapPreviewItem(top.getItem(0)));
                inventory.setItem(47, wrapPreviewItem(top.getItem(1)));
                inventory.setItem(48, createPlaceholder(Material.ARROW, "§8➔"));
                inventory.setItem(49, wrapPreviewItem(top.getItem(2)));
                for (int i = 50; i <= 53; i++) {
                    inventory.setItem(i, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-"));
                }
            }
            case ENCHANTING -> {
                inventory.setItem(45, createPlaceholder(Material.ENCHANTING_TABLE, Lang.get("inventory.gui.enchanting")));
                inventory.setItem(46, wrapPreviewItem(top.getItem(0)));
                inventory.setItem(47, wrapPreviewItem(top.getItem(1)));
                for (int i = 48; i <= 53; i++) {
                    inventory.setItem(i, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-"));
                }
            }
            case SMITHING -> {
                inventory.setItem(45, createPlaceholder(Material.SMITHING_TABLE, Lang.get("inventory.gui.smithing")));
                inventory.setItem(46, wrapPreviewItem(top.getItem(0)));
                inventory.setItem(47, wrapPreviewItem(top.getItem(1)));
                inventory.setItem(48, wrapPreviewItem(top.getItem(2)));
                inventory.setItem(49, createPlaceholder(Material.ARROW, "§8➔"));
                inventory.setItem(50, wrapPreviewItem(top.getItem(3)));
                for (int i = 51; i <= 53; i++) {
                    inventory.setItem(i, createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-"));
                }
            }
            default -> {
                inventory.setItem(45, createPlaceholder(Material.CHEST, Lang.get("inventory.gui.container", "type", type.name())));
                for (int i = 0; i < 8; i++) {
                    ItemStack containerItem = (i < top.getSize()) ? top.getItem(i) : null;
                    inventory.setItem(46 + i, wrapPreviewItem(containerItem));
                }
            }
        }
    }

    private ItemStack wrapPreviewItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, "§8-");
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null && PLACEHOLDER_KEY != null) {
            meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack createPlayerInfoHead(Player onlineTarget) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
            meta.setDisplayName("§e" + targetName);

            List<String> lore = new ArrayList<>();
            if (onlineTarget != null && onlineTarget.isOnline()) {
                lore.add(Lang.get("inventory.gui.head.status-online"));
                lore.add(Lang.get("inventory.gui.head.gamemode", "gamemode", onlineTarget.getGameMode().name()));
                double maxHealth = onlineTarget.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                        ? onlineTarget.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                        : onlineTarget.getHealth();
                lore.add(Lang.get("inventory.gui.head.health",
                        "health", String.format(Locale.US, "%.1f", onlineTarget.getHealth()),
                        "maxhealth", String.format(Locale.US, "%.1f", maxHealth)));
                lore.add(Lang.get("inventory.gui.head.hunger", "food", String.valueOf(onlineTarget.getFoodLevel())));
                lore.add(Lang.get("inventory.gui.head.level",
                        "level", String.valueOf(onlineTarget.getLevel()),
                        "exp", String.valueOf(Math.round(onlineTarget.getExp() * 100))));
                Location loc = onlineTarget.getLocation();
                lore.add(Lang.get("inventory.gui.head.location",
                        "world", loc.getWorld() != null ? loc.getWorld().getName() : "world",
                        "x", String.valueOf(loc.getBlockX()),
                        "y", String.valueOf(loc.getBlockY()),
                        "z", String.valueOf(loc.getBlockZ())));
            } else {
                lore.add(Lang.get("inventory.gui.head.status-offline"));
                lore.add(Lang.get("inventory.gui.head.uuid", "uuid", targetUuid.toString()));
            }

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack createPlaceholder(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PLACEHOLDER_KEY, PersistentDataType.BYTE);
    }
}
