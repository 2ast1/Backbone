package de.basti.backbone.util.inventory;

import de.basti.backbone.util.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltet das Speichern, Laden und Synchronisieren von Spieler-Inventaren
 * und Enderchests für Online- und Offline-Spieler.
 */
public class OfflineInventoryManager {

    private static final String FOLDER = "playerdata.inventories";

    /**
     * Sichert das komplette Inventar und die Enderchest eines Online-Spielers.
     * Typischerweise beim Verlassen des Servers (QuitEvent) oder Herunterfahren.
     */
    public static synchronized void savePlayer(Player player) {
        if (player == null) return;
        if (DataManager.isUninstalled()) return;
        UUID uuid = player.getUniqueId();
        String file = uuid + ".yml";

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, file);
        cfg.set("storage", Arrays.asList(player.getInventory().getStorageContents()));
        cfg.set("armor", Arrays.asList(player.getInventory().getArmorContents()));
        cfg.set("offhand", player.getInventory().getItemInOffHand());
        cfg.set("enderchest", Arrays.asList(player.getEnderChest().getContents()));
        cfg.set("has_offline_changes", false);
        cfg.set("last_saved", System.currentTimeMillis());

        DataManager.saveConfiguration(FOLDER, file, cfg);
        if (de.basti.backbone.util.RamManager.isLightMode()) {
            DataManager.evictFromCache(FOLDER, file);
        }
    }

    /**
     * Lädt die Inventardaten eines Spielers (auch offline).
     * Sucht zuerst in Backbone's YAML-Speicher und greift bei Bedarf nahtlos auf die vanilla
     * playerdata/<uuid>.dat Datei zurück.
     */
    @SuppressWarnings("unchecked")
    public static synchronized OfflinePlayerData loadPlayerData(UUID uuid) {
        if (uuid == null) {
            return new OfflinePlayerData();
        }

        // Falls der Spieler aktuell online ist, Live-Snapshot verwenden
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            return new OfflinePlayerData(
                    online.getInventory().getStorageContents(),
                    online.getInventory().getArmorContents(),
                    online.getInventory().getItemInOffHand(),
                    online.getEnderChest().getContents()
            );
        }

        String file = uuid + ".yml";
        if (DataManager.fileExists(FOLDER, file)) {
            FileConfiguration cfg = DataManager.getConfiguration(FOLDER, file);
            if (cfg.contains("storage")) {
                ItemStack[] storage = toArray((List<ItemStack>) cfg.get("storage"), 36);
                ItemStack[] armor = toArray((List<ItemStack>) cfg.get("armor"), 4);
                ItemStack offhand = cfg.getItemStack("offhand");
                ItemStack[] enderChest = toArray((List<ItemStack>) cfg.get("enderchest"), 27);
                if (de.basti.backbone.util.RamManager.isLightMode()) {
                    DataManager.evictFromCache(FOLDER, file);
                }
                return new OfflinePlayerData(storage, armor, offhand, enderChest);
            }
        }

        // Fallback: Aus vanilla playerdata .dat Datei laden (nicht sofort als YAML speichern,
        // damit keine unvollständigen NBT-Daten ohne echte Admin-Änderung persistiert werden)
        File datFile = getVanillaDatFile(uuid);
        if (datFile != null && datFile.exists()) {
            OfflinePlayerData fromDat = NbtReader.readFromFile(datFile);
            if (fromDat != null) {
                fromDat.setReadOnly(true);
                return fromDat;
            }
        }

        return new OfflinePlayerData();
    }

    /**
     * Speichert modifizierte Inventardaten eines Offline-Spielers.
     */
    public static synchronized void saveOfflineData(UUID uuid, OfflinePlayerData data, boolean markModified) {
        if (uuid == null || data == null || data.isReadOnly()) return;
        String file = uuid + ".yml";

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, file);
        cfg.set("storage", Arrays.asList(data.getStorageContents()));
        cfg.set("armor", Arrays.asList(data.getArmorContents()));
        cfg.set("offhand", data.getOffHand());
        cfg.set("enderchest", Arrays.asList(data.getEnderChestContents()));
        if (markModified) {
            cfg.set("has_offline_changes", true);
        }
        cfg.set("last_saved", System.currentTimeMillis());

        DataManager.saveConfiguration(FOLDER, file, cfg);
        if (de.basti.backbone.util.RamManager.isLightMode()) {
            DataManager.evictFromCache(FOLDER, file);
        }
    }

    /**
     * Prüft beim Betreten des Servers (JoinEvent), ob das Inventar des Spielers
     * offline bearbeitet wurde, und wendet Änderungen sofort an.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void applyPendingChanges(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String file = uuid + ".yml";

        if (!DataManager.fileExists(FOLDER, file)) return;
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, file);

        if (cfg.getBoolean("has_offline_changes", false)) {
            if (cfg.contains("storage")) {
                ItemStack[] storage = toArray((List<ItemStack>) cfg.get("storage"), 36);
                player.getInventory().setStorageContents(storage);
            }
            if (cfg.contains("armor")) {
                ItemStack[] armor = toArray((List<ItemStack>) cfg.get("armor"), 4);
                player.getInventory().setArmorContents(armor);
            }
            if (cfg.contains("offhand")) {
                player.getInventory().setItemInOffHand(cfg.getItemStack("offhand"));
            }
            if (cfg.contains("enderchest")) {
                ItemStack[] ec = toArray((List<ItemStack>) cfg.get("enderchest"), 27);
                player.getEnderChest().setContents(ec);
            }

            cfg.set("has_offline_changes", false);
            DataManager.saveConfiguration(FOLDER, file, cfg);
        }
        if (de.basti.backbone.util.RamManager.isLightMode()) {
            DataManager.evictFromCache(FOLDER, file);
        }
    }

    private static ItemStack[] toArray(List<ItemStack> list, int size) {
        ItemStack[] array = new ItemStack[size];
        if (list == null) return array;
        for (int i = 0; i < Math.min(list.size(), size); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static File getVanillaDatFile(UUID uuid) {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return null;
        World mainWorld = worlds.get(0);
        File worldFolder = mainWorld.getWorldFolder();
        return new File(worldFolder, "playerdata" + File.separator + uuid + ".dat");
    }
}
