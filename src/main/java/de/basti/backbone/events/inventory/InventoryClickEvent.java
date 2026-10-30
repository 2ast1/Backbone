package de.basti.backbone.events.inventory;

import de.basti.backbone.Backbone;
import de.basti.backbone.commands.inventory.EnderchestViewSession;
import de.basti.backbone.commands.inventory.InventoryViewSession;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.inventory.OfflinePlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Event-Klasse für Klick-Interaktionen in Inventaren (InventoryClickEvent).
 * Verwaltet Klick-Berechtigungen in /inventory und /enderchest GUIs.
 */
public class InventoryClickEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof InventoryViewSession session) {
            handleSessionClick(event, session);
        } else if (event.getInventory().getHolder() instanceof EnderchestViewSession ecSession) {
            if (ecSession.isReadOnly()) {
                event.setCancelled(true);
                return;
            }
            ecSession.markModified();
        }
    }

    private void handleSessionClick(org.bukkit.event.inventory.InventoryClickEvent event, InventoryViewSession session) {
        if (session.isReadOnly()) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();

        // COLLECT_TO_CURSOR (Doppelklick) im /inventory-GUI vollständig unterbinden,
        // um Item-Duplizierung aus Preview-Slots (45-53) oder Platzhalter-Slots zu verhindern
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // Klicks außerhalb des oberen 54-Slot GUIs (z.B. eigenes Inventar)
        if (rawSlot >= 54 || rawSlot < 0) {
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    Inventory top = event.getView().getTopInventory();
                    boolean hasSpace = false;
                    for (int s = 0; s < 36; s++) {
                        ItemStack item = top.getItem(s);
                        if (item == null || item.getType().isAir() || (item.isSimilar(clicked) && item.getAmount() < item.getMaxStackSize())) {
                            hasSpace = true;
                            break;
                        }
                    }
                    if (!hasSpace) {
                        event.setCancelled(true);
                        return;
                    }
                }
                session.markModified();
                syncLater(session);
            }
            return;
        }

        // Klicks auf Trenner, Spielerkopf oder Mauszeiger-Vorschau blockieren
        if (rawSlot == 41 || rawSlot == 42 || rawSlot == 43 || rawSlot == 44) {
            event.setCancelled(true);
            return;
        }

        // Klick auf Slot 45: Live-Ansicht des geöffneten Behälters
        if (rawSlot == 45) {
            event.setCancelled(true);
            if (!session.isOffline() && session.hasAdvancedPerm()) {
                Player onlineTarget = Bukkit.getPlayer(session.getTargetUuid());
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    Inventory top = onlineTarget.getOpenInventory().getTopInventory();
                    if (top != null
                            && top.getType() != InventoryType.CRAFTING
                            && top.getType() != InventoryType.CREATIVE
                            && event.getWhoClicked() instanceof Player viewer) {
                        Bukkit.getScheduler().runTask(Backbone.getInstance(), () -> {
                            if (viewer.isOnline() && onlineTarget.isOnline()) {
                                viewer.openInventory(top);
                            }
                        });
                    }
                }
            }
            return;
        }

        // Reihe 6 (45-53): Preview-Slots sperren
        if (rawSlot >= 45 && rawSlot <= 53) {
            event.setCancelled(true);
            return;
        }

        // Rüstungs- und Schildhand-Slots (36-40)
        if (rawSlot >= 36 && rawSlot <= 40) {
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();

            if (InventoryViewSession.isPlaceholder(current)) {
                if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
                    event.setCancelled(true);
                    return;
                }
                if (cursor != null && !cursor.getType().isAir() && !InventoryViewSession.isPlaceholder(cursor)) {
                    event.setCancelled(true);
                    if (rawSlot >= 36 && rawSlot <= 39) {
                        // Rüstungs-Slots: Maximal 1 Item abziehen und setzen
                        ItemStack single = cursor.clone();
                        single.setAmount(1);
                        event.getInventory().setItem(rawSlot, single);
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                            event.getWhoClicked().setItemOnCursor(cursor);
                        } else {
                            event.getWhoClicked().setItemOnCursor(null);
                        }
                    } else { // Slot 40: Offhand
                        if (event.isRightClick() && cursor.getAmount() > 1) {
                            ItemStack single = cursor.clone();
                            single.setAmount(1);
                            event.getInventory().setItem(rawSlot, single);
                            cursor.setAmount(cursor.getAmount() - 1);
                            event.getWhoClicked().setItemOnCursor(cursor);
                        } else {
                            event.getInventory().setItem(rawSlot, cursor.clone());
                            event.getWhoClicked().setItemOnCursor(null);
                        }
                    }
                    session.markModified();
                    syncLater(session);
                } else {
                    event.setCancelled(true);
                }
                return;
            }
        }

        session.markModified();
        syncLater(session);
    }

    private void syncLater(InventoryViewSession session) {
        Bukkit.getScheduler().runTask(Backbone.getInstance(), () -> {
            applyGuiChangesToTarget(session);
        });
    }

    public static synchronized void applyGuiChangesToTarget(InventoryViewSession session) {
        if (!session.isModified()) {
            return;
        }

        Inventory inv = session.getInventory();

        ItemStack[] storage = new ItemStack[36];
        // Slots 27-35 im GUI = Hotbar (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(27 + i);
            storage[i] = cleanItem(stack);
        }
        // Slots 0-26 im GUI = Hauptinventar (9-35)
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inv.getItem(i);
            storage[9 + i] = cleanItem(stack);
        }

        // Rüstung (0=Boots, 1=Leggings, 2=Chestplate, 3=Helmet)
        ItemStack[] armor = new ItemStack[4];
        armor[3] = cleanItem(inv.getItem(36)); // Helm
        armor[2] = cleanItem(inv.getItem(37)); // Brustplatte
        armor[1] = cleanItem(inv.getItem(38)); // Beinschiene
        armor[0] = cleanItem(inv.getItem(39)); // Stiefel

        ItemStack offhand = cleanItem(inv.getItem(40));

        // Platzhalter in leeren Rüstungs-/Offhand-Slots wiederherstellen
        if (armor[3] == null) inv.setItem(36, InventoryViewSession.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-helmet")));
        if (armor[2] == null) inv.setItem(37, InventoryViewSession.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-chestplate")));
        if (armor[1] == null) inv.setItem(38, InventoryViewSession.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-leggings")));
        if (armor[0] == null) inv.setItem(39, InventoryViewSession.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-boots")));
        if (offhand == null) inv.setItem(40, InventoryViewSession.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE, Lang.get("inventory.gui.no-offhand")));

        Player online = Bukkit.getPlayer(session.getTargetUuid());
        if (online != null && online.isOnline()) {
            online.getInventory().setStorageContents(storage);
            online.getInventory().setArmorContents(armor);
            online.getInventory().setItemInOffHand(offhand);
            online.updateInventory();
            return;
        }

        // Für Offline-Spieler speichern
        OfflinePlayerData data = OfflineInventoryManager.loadPlayerData(session.getTargetUuid());
        System.arraycopy(storage, 0, data.getStorageContents(), 0, 36);
        System.arraycopy(armor, 0, data.getArmorContents(), 0, 4);
        data.setOffHand(offhand);
        OfflineInventoryManager.saveOfflineData(session.getTargetUuid(), data, true);
    }

    private static ItemStack cleanItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || InventoryViewSession.isPlaceholder(stack)) {
            return null;
        }
        return stack;
    }
}
