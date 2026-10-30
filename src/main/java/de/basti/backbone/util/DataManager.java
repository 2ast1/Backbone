package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.automod.chat.MessageReview;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {

    private static JavaPlugin plugin;

    // Cache im Arbeitsspeicher (RAM), damit Dateien nur einmal von der Festplatte gelesen werden müssen
    private static final Map<String, FileConfiguration> configCache = new ConcurrentHashMap<>();

    /**
     * Initialisiert den DataManager und erstellt die Standard config.yml, falls noch nicht vorhanden.
     */
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        if (plugin != null && !plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        reloadAll();
        createDefaultConfig();
    }

    private static JavaPlugin getPlugin() {
        if (plugin == null) {
            if (Backbone.getInstance() != null) {
                plugin = Backbone.getInstance();
            } else {
                try {
                    plugin = JavaPlugin.getPlugin(Backbone.class);
                } catch (Exception ignored) {}
            }
        }
        return plugin;
    }

    private static String normalizeFileName(String fileName) {
        return fileName.contains(".") ? fileName : fileName + ".yml";
    }

    private static String getCacheKey(String folderPath, String fileName) {
        String cleanFolder = (folderPath == null) ? "" : folderPath.trim();
        return cleanFolder + "/" + normalizeFileName(fileName);
    }

    // =========================================================================
    // ORDNER & DATEIEN VERWALTUNG (MIT RAM-CACHE)
    // =========================================================================

    /**
     * Wandelt die Punkt-Notation (z.B. "playerdata.locations") in einen echten Ordnerpfad um.
     */
    public static File getFolder(String folderPath) {
        JavaPlugin p = getPlugin();
        File dataFolder = (p != null) ? p.getDataFolder() : new File("plugins/Backbone");
        if (folderPath == null || folderPath.isEmpty()) {
            return dataFolder;
        }
        String relativePath = folderPath.replace(".", File.separator);
        return new File(dataFolder, relativePath);
    }

    public static boolean isUninstalled() {
        JavaPlugin p = getPlugin();
        if (p instanceof Backbone bb) {
            return bb.isPluginUninstalled();
        }
        return false;
    }

    /**
     * Erstellt einen Ordner (inklusive Unterordner mit "." getrennt, z.B. "playerdata.locations").
     */
    public static File createFolder(String folderPath) {
        if (isUninstalled()) {
            return getFolder(folderPath);
        }
        File folder = getFolder(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    /**
     * Gibt das File-Objekt zurück (fügt automatisch .yml an, falls keine Dateiendung angegeben wurde).
     */
    public static File getFile(String folderPath, String fileName) {
        return new File(getFolder(folderPath), normalizeFileName(fileName));
    }

    /**
     * Erstellt eine Datei in dem angegebenen Ordner (und erstellt den Ordner gleich mit, falls nötig).
     */
    public static File createFile(String folderPath, String fileName) {
        if (isUninstalled()) {
            return getFile(folderPath, fileName);
        }
        createFolder(folderPath);
        File file = getFile(folderPath, fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Backbone.warnMsg("Could not create file: " + file.getPath() + " (" + e.getMessage() + ")");
            }
        }
        return file;
    }

    /**
     * Prüft, ob eine Datei existiert (entweder bereits im RAM-Cache oder auf der Festplatte).
     */
    public static boolean fileExists(String folderPath, String fileName) {
        if (configCache.containsKey(getCacheKey(folderPath, fileName))) {
            return true;
        }
        return getFile(folderPath, fileName).exists();
    }

    /**
     * Gibt die FileConfiguration aus dem Arbeitsspeicher (RAM) zurück.
     * Falls sie noch nicht im RAM ist, wird sie einmalig von der Festplatte geladen und gecacht.
     */
    public static synchronized FileConfiguration getConfiguration(String folderPath, String fileName) {
        if ((folderPath == null || folderPath.isEmpty()) && normalizeFileName(fileName).equalsIgnoreCase("config.yml")) {
            if (getPlugin() != null) {
                return getConfig();
            }
        }
        String cacheKey = getCacheKey(folderPath, fileName);
        return configCache.computeIfAbsent(cacheKey, k -> {
            File file = createFile(folderPath, fileName);
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    /**
     * Speichert eine FileConfiguration im RAM-Cache und auf die Festplatte.
     */
    public static synchronized void saveConfiguration(String folderPath, String fileName, FileConfiguration config) {
        if (isUninstalled()) {
            return;
        }
        if ((folderPath == null || folderPath.isEmpty()) && normalizeFileName(fileName).equalsIgnoreCase("config.yml")) {
            saveConfig();
            return;
        }
        String cacheKey = getCacheKey(folderPath, fileName);
        configCache.put(cacheKey, config);
        File file = createFile(folderPath, fileName);
        try {
            config.save(file);
        } catch (IOException e) {
            Backbone.warnMsg("Could not save file: " + file.getPath() + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Speichert Daten auf die Festplatte (z.B. bei onDisable).
     * Wichtig: Reine Konfigurationsdateien (config.yml, automod/config.yml, badwords.yml)
     * werden NICHT überschrieben, um manuelle Änderungen von Server-Administratoren nicht zu vernichten.
     */
    public static synchronized void saveAll() {
        if (isUninstalled()) {
            return;
        }

        for (Map.Entry<String, FileConfiguration> entry : configCache.entrySet()) {
            String key = entry.getKey();
            int slashIndex = key.indexOf('/');
            String folderPath = (slashIndex != -1) ? key.substring(0, slashIndex) : "";
            String fileName = (slashIndex != -1) ? key.substring(slashIndex + 1) : key;

            // Keine Konfigurationsdateien beim Herunterfahren überschreiben
            if (isConfigFile(folderPath, fileName)) {
                continue;
            }

            // Falls die Datei auf der Festplatte zwischenzeitlich gelöscht wurde, nicht wiederherstellen
            File file = getFile(folderPath, fileName);
            if (!file.exists()) {
                continue;
            }

            saveConfiguration(folderPath, fileName, entry.getValue());
        }
    }

    private static boolean isConfigFile(String folderPath, String fileName) {
        String cleanFile = normalizeFileName(fileName).toLowerCase();
        String cleanFolder = folderPath != null ? folderPath.toLowerCase().trim() : "";

        // Haupt-config.yml
        if (cleanFolder.isEmpty() && cleanFile.equals("config.yml")) {
            return true;
        }
        // automod/config.yml und automod/badwords.yml
        if (cleanFolder.equals("automod") && (cleanFile.equals("config.yml") || cleanFile.equals("badwords.yml"))) {
            return true;
        }
        // languages/*.lang
        if (cleanFolder.equals("languages") || cleanFile.endsWith(".lang")) {
            return true;
        }
        return false;
    }

    /**
     * Lädt eine bestimmte Datei frisch von der Festplatte in den Arbeitsspeicher (falls sie manuell bearbeitet wurde).
     */
    public static synchronized void reloadFile(String folderPath, String fileName) {
        if ((folderPath == null || folderPath.isEmpty()) && normalizeFileName(fileName).equalsIgnoreCase("config.yml")) {
            reloadConfig();
            return;
        }
        String cacheKey = getCacheKey(folderPath, fileName);
        File file = createFile(folderPath, fileName);
        configCache.put(cacheKey, YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Leert den Cache und lädt alle Dateien bei Bedarf frisch von der Festplatte.
     */
    public static synchronized void reloadAll() {
        reloadConfig();
        configCache.clear();
    }

    /**
     * Entfernt eine einzelne Datei gezielt aus dem RAM-Cache (nachdem sie gespeichert wurde).
     */
    public static synchronized void evictFromCache(String folderPath, String fileName) {
        configCache.remove(getCacheKey(folderPath, fileName));
    }

    /**
     * Gibt die Anzahl der aktuell im RAM gecachten YAML-Dateien zurück.
     */
    public static synchronized int getCachedFileCount() {
        return configCache.size();
    }

    /**
     * Gibt die Gesamtanzahl aller im RAM-Cache gehaltenen YAML-Schlüssel zurück.
     */
    public static synchronized int getCachedKeyCount() {
        int totalKeys = 0;
        for (FileConfiguration cfg : configCache.values()) {
            try {
                totalKeys += cfg.getKeys(true).size();
            } catch (Exception ignored) {}
        }
        return totalKeys;
    }

    /**
     * Lagert nicht zwingend im RAM benötigte Dateien auf die Festplatte aus und leert den RAM-Cache.
     *
     * @param aggressive Wenn true, werden sämtliche gecachten Dateien nach dem Speichern aus dem RAM entfernt.
     *                   Wenn false, werden vor allem Offline-Inventare und selten genutzte Dateien ausgelagert.
     * @return Anzahl der aus dem RAM ausgelagerten Dateien
     */
    public static synchronized int compactCache(boolean aggressive) {
        if (isUninstalled()) {
            return 0;
        }
        saveAll();

        if (aggressive) {
            int count = configCache.size();
            configCache.clear();
            return count;
        }

        int evicted = 0;
        var iterator = configCache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String key = entry.getKey();
            if (key.startsWith("playerdata.inventories/") || key.startsWith("locations/") || key.startsWith("automod/playerdata")) {
                iterator.remove();
                evicted++;
            }
        }
        return evicted;
    }

    // =========================================================================
    // WERTE SPEICHERN (SET)
    // =========================================================================

    /**
     * Speichert einen beliebigen Wert (String, Int, Long, Boolean, Location, Inventory, MessageReview, etc.) im RAM und in der Datei.
     * Beispiel: DataManager.set("playerdata.locations", "hubs.yml", "Testwert", value);
     */
    public static synchronized void set(String folder, String file, String key, Object value) {
        if (value instanceof Inventory inventory) {
            setInventory(folder, file, key, inventory);
            return;
        }
        if (value instanceof MessageReview review) {
            setMessageReview(folder, file, key, review);
            return;
        }

        FileConfiguration cfg = getConfiguration(folder, file);
        cfg.set(key, value);
        saveConfiguration(folder, file, cfg);
    }

    /**
     * Prüft direkt im Arbeitsspeicher, ob ein Schlüssel in der angegebenen Datei existiert.
     */
    public static synchronized boolean contains(String folder, String file, String key) {
        if (!fileExists(folder, file)) return false;
        return getConfiguration(folder, file).contains(key);
    }

    // =========================================================================
    // WERTE LESEN (GET - DIREKT AUS DEM ARBEITSSPEICHER)
    // =========================================================================

    public static synchronized Object get(String folder, String file, String key) {
        return getConfiguration(folder, file).get(key);
    }

    public static synchronized String getString(String folder, String file, String key) {
        return getConfiguration(folder, file).getString(key);
    }

    public static synchronized int getInt(String folder, String file, String key) {
        return getConfiguration(folder, file).getInt(key);
    }

    public static synchronized long getLong(String folder, String file, String key) {
        return getConfiguration(folder, file).getLong(key);
    }

    public static synchronized double getDouble(String folder, String file, String key) {
        return getConfiguration(folder, file).getDouble(key);
    }

    public static synchronized boolean getBoolean(String folder, String file, String key) {
        return getConfiguration(folder, file).getBoolean(key);
    }

    public static synchronized Location getLocation(String folder, String file, String key) {
        return getConfiguration(folder, file).getLocation(key);
    }

    public static synchronized List<String> getStringList(String folder, String file, String key) {
        return getConfiguration(folder, file).getStringList(key);
    }

    public static synchronized List<?> getList(String folder, String file, String key) {
        return getConfiguration(folder, file).getList(key);
    }

    // =========================================================================
    // INVENTORY SPEICHERN & LADEN
    // =========================================================================

    /**
     * Speichert ein komplettes Inventory (Größe + Items) unter dem angegebenen Schlüssel.
     */
    public static synchronized void setInventory(String folder, String file, String key, Inventory inventory) {
        FileConfiguration cfg = getConfiguration(folder, file);
        if (inventory == null) {
            cfg.set(key, null);
        } else {
            cfg.set(key + ".size", inventory.getSize());
            cfg.set(key + ".contents", inventory.getContents());
        }
        saveConfiguration(folder, file, cfg);
    }

    /**
     * Lädt ein Inventory aus dem Arbeitsspeicher (bzw. beim ersten Mal aus der Datei).
     */
    public static synchronized Inventory getInventory(String folder, String file, String key) {
        return getInventory(folder, file, key, "Inventory");
    }

    /**
     * Lädt ein Inventory mit eigenem Titel.
     */
    @SuppressWarnings("unchecked")
    public static synchronized Inventory getInventory(String folder, String file, String key, String title) {
        FileConfiguration cfg = getConfiguration(folder, file);
        if (!cfg.contains(key + ".size")) {
            return null;
        }

        int size = cfg.getInt(key + ".size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        Object rawContents = cfg.get(key + ".contents");
        if (rawContents instanceof List<?> list) {
            ItemStack[] contents = ((List<ItemStack>) list).toArray(new ItemStack[0]);
            inv.setContents(contents);
        } else if (rawContents instanceof ItemStack[] array) {
            inv.setContents(array);
        }

        return inv;
    }

    // =========================================================================
    // MESSAGE REVIEW SPEICHERN & LADEN (AUTOMOD)
    // =========================================================================

    /**
     * Speichert ein {@link MessageReview}-Objekt übersichtlich strukturiert unter dem angegebenen Schlüssel.
     */
    public static synchronized void setMessageReview(String folder, String file, String key, MessageReview review) {
        FileConfiguration cfg = getConfiguration(folder, file);
        if (review == null) {
            cfg.set(key, null);
        } else {
            cfg.set(key + ".player-uuid", review.getPlayerUuid() != null ? review.getPlayerUuid().toString() : null);
            cfg.set(key + ".player-name", review.getPlayerName());
            cfg.set(key + ".message", review.getMessage());
            cfg.set(key + ".timestamp", review.getTimestamp());
            cfg.set(key + ".formatted-time", review.getFormattedTimestamp());
            cfg.set(key + ".description", review.getDescription());
            cfg.set(key + ".strikes", review.getStrikes());
        }
        saveConfiguration(folder, file, cfg);
    }

    /**
     * Lädt ein {@link MessageReview}-Objekt aus dem Arbeitsspeicher bzw. der angegebenen Datei.
     */
    public static synchronized MessageReview getMessageReview(String folder, String file, String key) {
        FileConfiguration cfg = getConfiguration(folder, file);
        if (!cfg.contains(key + ".message") && !cfg.contains(key + ".timestamp")) {
            return null;
        }

        String uuidString = cfg.getString(key + ".player-uuid");
        UUID playerUuid = null;
        if (uuidString != null && !uuidString.isBlank()) {
            try {
                playerUuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
                // Ungültige UUID ignorieren
            }
        }

        String playerName = cfg.getString(key + ".player-name", Lang.getRaw("common.unknown"));
        String message = cfg.getString(key + ".message", "");
        long timestamp = cfg.getLong(key + ".timestamp", System.currentTimeMillis());
        String description = cfg.getString(key + ".description", "");
        int strikes = cfg.getInt(key + ".strikes", 0);

        return new MessageReview(playerUuid, playerName, message, timestamp, description, strikes);
    }

    // =========================================================================
    // STANDARD CONFIG.YML VERWALTUNG
    // =========================================================================

    /**
     * Erstellt die normale config.yml im Plugin-Ordner und setzt die Standardwerte per Code.
     * Existiert die Datei bereits, werden nur fehlende Einträge ergänzt und bestehende Werte
     * sowie manuelle Anpassungen von Server-Administratoren nicht überschrieben.
     */
    public static synchronized void createDefaultConfig() {
        JavaPlugin p = getPlugin();
        if (p != null && !p.getDataFolder().exists()) {
            p.getDataFolder().mkdirs();
        }
        createFolder("");
        File configFile = getFile("", "config.yml");
        boolean fileExisted = configFile.exists();
        FileConfiguration cfg = getConfig();

        boolean modified = !fileExisted;

        // Migration von altem server.motd String zu server.motd.normal
        if (cfg.contains("server.motd") && cfg.isString("server.motd")) {
            String oldMotd = cfg.getString("server.motd");
            cfg.set("server.motd", null);
            cfg.set("server.motd.normal", oldMotd);
            modified = true;
        }
        // Migration von altem server.maintenance-motd zu server.motd.maintenance.text
        if (cfg.contains("server.maintenance-motd")) {
            String oldMaintMotd = cfg.getString("server.maintenance-motd");
            cfg.set("server.maintenance-motd", null);
            cfg.set("server.motd.maintenance.text", oldMaintMotd);
            modified = true;
        }

        if (!cfg.contains("server.name")) { cfg.set("server.name", "My Minecraft Server"); modified = true; }
        if (!cfg.contains("server.backbone.language")) { cfg.set("server.backbone.language", "englisch.lang"); modified = true; }
        if (!cfg.contains("server.backbone.setup-completed")) { cfg.set("server.backbone.setup-completed", false); modified = true; }
        if (!cfg.contains("server.join")) { cfg.set("server.join", "{pluginPrefix} &e{playername} &7joined the server!"); modified = true; }
        if (!cfg.contains("server.leave")) { cfg.set("server.leave", "{pluginPrefix} &e{playername} &7left the server!"); modified = true; }
        if (!cfg.contains("server.motd.normal")) { cfg.set("server.motd.normal", "A Minecraft Server"); modified = true; }
        if (!cfg.contains("server.motd.maintenance.change")) { cfg.set("server.motd.maintenance.change", true); modified = true; }
        if (!cfg.contains("server.motd.maintenance.text")) { cfg.set("server.motd.maintenance.text", "&cThe server is currently in maintenance mode!\n&7We will be back soon!"); modified = true; }
        if (!cfg.contains("server.maintenance")) { cfg.set("server.maintenance", false); modified = true; }
        if (!cfg.contains("server.maintenance-message")) { cfg.set("server.maintenance-message", "&cThe server is currently in maintenance mode!\n&7Please try again later."); modified = true; }
        if (!cfg.contains("chat.color")) { cfg.set("chat.color", true); modified = true; }
        if (!cfg.contains("chat.format")) { cfg.set("chat.format", "{playername} | {msg}"); modified = true; }

        if (!cfg.contains("ranks.enabled")) { cfg.set("ranks.enabled", false); modified = true; }
        if (!cfg.contains("ranks.default-rank")) { cfg.set("ranks.default-rank", ""); modified = true; }
        if (!cfg.contains("ranks.use-rank-prefix")) { cfg.set("ranks.use-rank-prefix", true); modified = true; }

        if (!cfg.contains("prefix.enabled")) { cfg.set("prefix.enabled", true); modified = true; }
        if (!cfg.contains("namecolor.enabled")) { cfg.set("namecolor.enabled", true); modified = true; }
        if (!cfg.contains("essentials.enabled")) { cfg.set("essentials.enabled", true); modified = true; }

        // Lobby, Warps, Homes, Spawn & Economy
        if (!cfg.contains("lobby.enabled")) { cfg.set("lobby.enabled", true); modified = true; }
        if (!cfg.contains("lobby.teleport-on-join")) { cfg.set("lobby.teleport-on-join", false); modified = true; }
        if (!cfg.contains("warps.enabled")) { cfg.set("warps.enabled", true); modified = true; }
        if (!cfg.contains("warps.disabled-worlds")) { cfg.set("warps.disabled-worlds", new ArrayList<String>()); modified = true; }
        if (!cfg.contains("homes.enabled")) { cfg.set("homes.enabled", true); modified = true; }
        if (!cfg.contains("homes.max-homes")) { cfg.set("homes.max-homes", 5); modified = true; }
        if (!cfg.contains("homes.disabled-worlds")) { cfg.set("homes.disabled-worlds", new ArrayList<String>()); modified = true; }
        if (!cfg.contains("spawn.enabled")) { cfg.set("spawn.enabled", true); modified = true; }
        if (!cfg.contains("economy.enabled")) { cfg.set("economy.enabled", true); modified = true; }
        if (!cfg.contains("economy.starting-balance")) { cfg.set("economy.starting-balance", 1000.0); modified = true; }
        if (!cfg.contains("economy.currency-symbol")) { cfg.set("economy.currency-symbol", "$"); modified = true; }
        if (!cfg.contains("economy.currency-name")) { cfg.set("economy.currency-name", "Coins"); modified = true; }

        // Scoreboard-System
        if (!cfg.contains("scoreboard.enabled")) { cfg.set("scoreboard.enabled", true); modified = true; }
        if (!cfg.contains("scoreboard.toggle.allow-player-toggle")) { cfg.set("scoreboard.toggle.allow-player-toggle", true); modified = true; }
        if (!cfg.contains("scoreboard.toggle.disabled-worlds")) { cfg.set("scoreboard.toggle.disabled-worlds", new ArrayList<String>()); modified = true; }

        // RAM & Performance
        if (!cfg.contains("performance.ram-mode")) { cfg.set("performance.ram-mode", "BALANCED"); modified = true; }

        // Automatische Migration: Falls noch der alte "{status}"-Platzhalter in chat.format steht
        String currentFormat = cfg.getString("chat.format");
        if (currentFormat != null && currentFormat.contains("{status}")) {
            String cleaned = currentFormat.replace("{status} ", "").replace("{status}", "").replace("{message}", "{msg}").trim();
            if (cleaned.isEmpty() || cleaned.equals("|")) {
                cleaned = "{playername} | {msg}";
            }
            cfg.set("chat.format", cleaned);
            modified = true;
        }

        if (modified) {
            saveConfig();
        }
    }

    /**
     * Gibt die normale config.yml zurück (wird von Bukkit automatisch im RAM gehalten).
     */
    public static synchronized FileConfiguration getConfig() {
        JavaPlugin p = getPlugin();
        if (p != null) {
            return p.getConfig();
        }
        String cacheKey = getCacheKey("", "config.yml");
        return configCache.computeIfAbsent(cacheKey, k -> {
            File file = createFile("", "config.yml");
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    /**
     * Speichert die normale config.yml.
     */
    public static synchronized void saveConfig() {
        if (isUninstalled()) {
            return;
        }
        JavaPlugin p = getPlugin();
        if (p != null) {
            p.saveConfig();
        }
    }

    /**
     * Lädt die normale config.yml neu von der Festplatte in den Arbeitsspeicher.
     */
    public static synchronized void reloadConfig() {
        JavaPlugin p = getPlugin();
        if (p != null) {
            p.reloadConfig();
        }
    }

    /**
     * Setzt einen Wert direkt in der normalen config.yml im RAM und speichert sie.
     */
    public static synchronized void setConfig(String key, Object value) {
        if (value instanceof Inventory inventory) {
            setInventory("", "config.yml", key, inventory);
            return;
        }
        if (value instanceof MessageReview review) {
            setMessageReview("", "config.yml", key, review);
            return;
        }
        getConfig().set(key, value);
        saveConfig();
    }

    public static synchronized String getConfigString(String key) {
        return getConfig().getString(key);
    }

    public static synchronized int getConfigInt(String key) {
        return getConfig().getInt(key);
    }

    public static synchronized long getConfigLong(String key) {
        return getConfig().getLong(key);
    }

    public static synchronized boolean getConfigBoolean(String key) {
        return getConfig().getBoolean(key);
    }

    public static synchronized Location getConfigLocation(String key) {
        return getConfig().getLocation(key);
    }

    public static synchronized Inventory getConfigInventory(String key) {
        return getInventory("", "config.yml", key);
    }

    public static synchronized MessageReview getConfigMessageReview(String key) {
        return getMessageReview("", "config.yml", key);
    }
}
