package de.basti.backbone.ranks;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zentraler Manager für alle Ränge und Berechtigungen.
 * Verwaltet die 'ranks.yml', das In-Memory-Caching, Validierung von Farbcodes und Namen,
 * temporäre Rangabläufe, Rang-Verkettung (Predecessor / Successor) sowie Zuweisungen an Spieler.
 */
public class RankManager {

    public static final String FOLDER = "";
    public static final String FILE = "ranks.yml";

    // Regex für eine exakt einzelne Minecraft-Standardfarbe (&0-9, &a-f oder §0-9, §a-f)
    private static final Pattern SINGLE_COLOR_PATTERN = Pattern.compile("^[&§]([0-9a-fA-F])$");
    // Regex für saubere Rangnamen: nur Buchstaben, Zahlen, Umlaute, Bindestrich und Unterstrich (KEINE Leerzeichen!)
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9äöüÄÖÜß_-]{1,32}$");

    private static final Map<String, Rank> rankCache = new ConcurrentHashMap<>();
    private static PermissionService permissionService;
    private static Backbone plugin;
    private static BukkitTask expiryTask;
    private static boolean initialized = false;

    // =========================================================================
    // INITIALISIERUNG & LIFECYCLE
    // =========================================================================

    public static void init(Backbone backboneInstance) {
        plugin = backboneInstance;
        permissionService = new PermissionService(backboneInstance);

        // Sicherstellen, dass ranks.yml existiert
        DataManager.createFile(FOLDER, FILE);

        // Migration prüfen & Ränge aus ranks.yml in den RAM-Cache laden
        migrateAndLoadAllRanks();

        initialized = true;

        // Falls das Rangsystem aktiv ist, bestehende Online-Spieler versorgen
        if (isRanksEnabled()) {
            permissionService.updateAll();
        }

        // Expiry-Timer starten (alle 30 Sekunden)
        startExpiryTask();
    }

    public static PermissionService getPermissionService() {
        return permissionService;
    }

    public static void shutdown() {
        stopExpiryTask();
        if (permissionService != null) {
            permissionService.unregisterAll();
        }
        rankCache.clear();
    }

    private static void startExpiryTask() {
        stopExpiryTask();
        if (plugin != null && Bukkit.getServer() != null) {
            try {
                expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!isRanksEnabled()) return;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        checkAndHandleExpiration(online.getUniqueId(), true);
                    }
                }, 20L * 30, 20L * 30);
            } catch (Throwable ignored) {}
        }
    }

    private static void stopExpiryTask() {
        if (expiryTask != null) {
            try {
                expiryTask.cancel();
            } catch (Throwable ignored) {}
            expiryTask = null;
        }
    }

    /**
     * Lädt die ranks.yml frisch von der Festplatte und aktualisiert alle Online-Spieler.
     */
    public static void reload() {
        DataManager.reloadFile(FOLDER, FILE);
        migrateAndLoadAllRanks();

        if (isRanksEnabled() && permissionService != null) {
            permissionService.updateAll();
        } else if (permissionService != null) {
            permissionService.unregisterAll();
        }
    }

    // =========================================================================
    // MIGRATION & LADEN
    // =========================================================================

    private static void migrateAndLoadAllRanks() {
        rankCache.clear();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        Set<String> keys = cfg.getKeys(false);

        boolean needsSave = false;

        for (String key : keys) {
            // Prüfung auf altes Schema mit "name:" Unterfeld
            String rawName = cfg.getString(key + ".name");
            String effectiveName = key;
            if (rawName != null && !rawName.isBlank()) {
                String sanitized = rawName.replaceAll("[^a-zA-Z0-9äöüÄÖÜß_-]", "").trim();
                if (!sanitized.isBlank() && !sanitized.equalsIgnoreCase(key) && !cfg.contains(sanitized)) {
                    // Migration: Abschnitt verschieben
                    effectiveName = sanitized;
                }
            }

            // Altes "price:" Attribut migrieren zu "offers:" Liste
            if (cfg.contains(key + ".price") && !cfg.contains(key + ".offers")) {
                double oldPrice = cfg.getDouble(key + ".price", -1.0);
                if (oldPrice >= 0.0) {
                    List<Map<String, Object>> offerList = new ArrayList<>();
                    Map<String, Object> singleOffer = new LinkedHashMap<>();
                    singleOffer.put("price", oldPrice);
                    offerList.add(singleOffer);
                    cfg.set(key + ".offers", offerList);
                }
                cfg.set(key + ".price", null);
                needsSave = true;
            }

            // Altes "name:" Feld entfernen
            if (cfg.contains(key + ".name")) {
                cfg.set(key + ".name", null);
                needsSave = true;
            }

            // Falls Schlüssel umbenannt werden musste
            if (!effectiveName.equals(key)) {
                var sec = cfg.getConfigurationSection(key);
                if (sec != null) {
                    cfg.set(effectiveName, sec);
                    cfg.set(key, null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            DataManager.saveConfiguration(FOLDER, FILE, cfg);
            cfg = DataManager.getConfiguration(FOLDER, FILE);
        }

        // Nun alle Ränge in den Cache laden
        for (String key : cfg.getKeys(false)) {
            String lowerKey = key.toLowerCase(Locale.ROOT);
            String prefix = cfg.getString(key + ".prefix", "");
            String color = cfg.getString(key + ".color", "§7");

            // Dauer parsen
            long duration = -1;
            Object rawDur = cfg.get(key + ".duration");
            if (rawDur instanceof Number n) {
                duration = n.longValue();
            } else if (rawDur instanceof String s) {
                if (DurationUtil.isPermanent(s)) {
                    duration = -1;
                } else if (DurationUtil.isDurationSyntax(s)) {
                    duration = DurationUtil.parseDuration(s);
                }
            }

            String previousRank = cfg.getString(key + ".previous", cfg.getString(key + ".predecessor"));
            String nextRank = cfg.getString(key + ".next", cfg.getString(key + ".successor"));
            long created = cfg.getLong(key + ".created", System.currentTimeMillis());
            long lastChange = cfg.getLong(key + ".lastchange", created);
            List<String> perms = cfg.getStringList(key + ".permissions");

            // Welt-Berechtigungen
            Map<String, List<String>> worldPerms = new LinkedHashMap<>();
            String worldPermSectionPath = key + ".world-permissions";
            if (cfg.contains(worldPermSectionPath) && cfg.isConfigurationSection(worldPermSectionPath)) {
                var sec = cfg.getConfigurationSection(worldPermSectionPath);
                if (sec != null) {
                    for (String wKey : sec.getKeys(false)) {
                        List<String> wList = cfg.getStringList(worldPermSectionPath + "." + wKey);
                        worldPerms.put(wKey.toLowerCase(Locale.ROOT), wList);
                    }
                }
            }

            // Kaufangebote laden
            List<RankOffer> offers = new ArrayList<>();
            List<?> rawOffers = cfg.getList(key + ".offers");
            if (rawOffers != null) {
                for (Object o : rawOffers) {
                    if (o instanceof Map<?, ?> m) {
                        RankOffer offer = RankOffer.fromMap(m);
                        if (offer != null) {
                            offers.add(offer);
                        }
                    }
                }
            }

            String normalizedColor = normalizeSingleColor(color);
            if (normalizedColor == null) {
                normalizedColor = "§7";
            }

            Rank rank = new Rank(key, prefix, normalizedColor, duration, previousRank, nextRank,
                    created, lastChange, perms, worldPerms, offers);
            rankCache.put(lowerKey, rank);
        }
    }

    // =========================================================================
    // VALIDIERUNG & FARBNORMALISIERUNG
    // =========================================================================

    public static boolean isValidRankName(String name) {
        if (name == null || name.isBlank()) return false;
        return VALID_NAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Rückwärtskompatibilität: Alias für isValidRankName.
     */
    public static boolean isValidRankId(String id) {
        return isValidRankName(id);
    }

    public static boolean isValidSingleColor(String input) {
        if (input == null || input.isBlank()) return false;
        return SINGLE_COLOR_PATTERN.matcher(input.trim()).matches();
    }

    public static String normalizeSingleColor(String input) {
        if (input == null || input.isBlank()) return null;
        Matcher matcher = SINGLE_COLOR_PATTERN.matcher(input.trim());
        if (matcher.matches()) {
            return "§" + matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // =========================================================================
    // CONFIG-EINSTELLUNGEN
    // =========================================================================

    public static boolean isRanksEnabled() {
        return DataManager.getConfigBoolean("ranks.enabled");
    }

    public static void setRanksEnabled(boolean enabled) {
        DataManager.setConfig("ranks.enabled", enabled);
        if (permissionService != null) {
            if (enabled) {
                permissionService.updateAll();
            } else {
                permissionService.unregisterAll();
            }
        }
        PrefixManager.updateAllTabLists();
        de.basti.backbone.util.CommandRegistryManager.syncCommands();
        de.basti.backbone.util.vault.VaultManager.reload();
    }

    public static boolean isUseRankPrefixEnabled() {
        return DataManager.getConfigBoolean("ranks.use-rank-prefix");
    }

    public static String getDefaultRankName() {
        String name = DataManager.getConfigString("ranks.default-rank");
        return (name != null) ? name.trim() : "";
    }

    public static String getDefaultRankId() {
        return getDefaultRankName();
    }

    public static Rank getDefaultRank() {
        String defaultName = getDefaultRankName();
        if (defaultName.isBlank()) return null;
        return getRank(defaultName);
    }

    public static boolean isDefaultRank(String rankName) {
        if (rankName == null) return false;
        String def = getDefaultRankName();
        return !def.isBlank() && def.equalsIgnoreCase(rankName.trim());
    }

    public static boolean setDefaultRank(String rankName) {
        if (rankName == null || rankName.isBlank()) {
            DataManager.setConfig("ranks.default-rank", "");
            return true;
        }
        Rank rank = getRank(rankName);
        if (rank == null) {
            return false;
        }

        DataManager.setConfig("ranks.default-rank", rank.getName());

        if (permissionService != null && isRanksEnabled()) {
            permissionService.updateAll();
        }
        PrefixManager.updateAllTabLists();
        return true;
    }

    // =========================================================================
    // RANG CRUD-OPERATIONEN
    // =========================================================================

    public static Rank getRank(String name) {
        if (name == null) return null;
        return rankCache.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean exists(String name) {
        return getRank(name) != null;
    }

    public static Collection<Rank> getAllRanks() {
        return Collections.unmodifiableCollection(rankCache.values());
    }

    public static List<String> getAllRankNames() {
        List<String> list = new ArrayList<>();
        for (Rank r : rankCache.values()) {
            list.add(r.getName());
        }
        return list;
    }

    public static List<String> getAllRankIds() {
        return getAllRankNames();
    }

    /**
     * Erstellt einen neuen permanenten Rang.
     */
    public static synchronized Rank createRank(String name, String colorInput, String prefix) {
        return createRank(name, colorInput, -1, prefix);
    }

    /**
     * Rückwärtskompatibilität für alte 4-String Aufrufe (id, name, color, prefix).
     */
    public static synchronized Rank createRank(String legacyId, String name, String colorInput, String prefix) {
        String eff = (legacyId != null && !legacyId.isBlank()) ? legacyId : name;
        return createRank(eff, colorInput, -1, prefix);
    }

    /**
     * Erstellt einen neuen Rang mit Standarddauer.
     *
     * @param name            Rangname (dient als ID, keine Leerzeichen)
     * @param colorInput      Minecraft-Farbe (&0-9, &a-f)
     * @param defaultDuration Standarddauer in Millisekunden (-1 = permanent)
     * @param prefix          Präfix
     * @return Das erstellte Rank-Objekt
     */
    public static synchronized Rank createRank(String name, String colorInput, long defaultDuration, String prefix) {
        String cleanName = (name != null) ? name.trim() : "";
        if (!isValidRankName(cleanName)) {
            throw new IllegalArgumentException(Lang.get("rank.validation.invalid-name"));
        }
        if (exists(cleanName)) {
            throw new IllegalArgumentException(Lang.get("rank.validation.already-exists", "name", cleanName));
        }

        String normalizedColor = normalizeSingleColor(colorInput);
        if (normalizedColor == null) {
            throw new IllegalArgumentException(Lang.get("rank.validation.invalid-color"));
        }

        long now = System.currentTimeMillis();
        String safePrefix = prefix != null ? prefix : "";
        long safeDuration = (defaultDuration <= 0 && defaultDuration != -1) ? -1 : defaultDuration;
        List<String> emptyPerms = new ArrayList<>();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(cleanName + ".prefix", safePrefix);
        cfg.set(cleanName + ".color", normalizedColor);
        if (safeDuration > 0) {
            cfg.set(cleanName + ".duration", safeDuration);
        } else {
            cfg.set(cleanName + ".duration", "perm");
        }
        cfg.set(cleanName + ".created", now);
        cfg.set(cleanName + ".lastchange", now);
        cfg.set(cleanName + ".permissions", emptyPerms);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank rank = new Rank(cleanName, safePrefix, normalizedColor, safeDuration, null, null,
                now, now, emptyPerms, Collections.emptyMap(), Collections.emptyList());
        rankCache.put(cleanName.toLowerCase(Locale.ROOT), rank);

        // Der allererste erstellte Rang wird automatisch Standard-Rang
        if (rankCache.size() == 1 || getDefaultRankName().isBlank() || getDefaultRank() == null) {
            setDefaultRank(cleanName);
        }

        return rank;
    }

    /**
     * Benennt einen Rang vollständig um (inklusive aller Verweise in playerdata.yml und Chains).
     */
    public static synchronized boolean setRankName(String oldName, String newName) {
        Rank rank = getRank(oldName);
        if (rank == null) return false;

        String cleanNew = (newName != null) ? newName.trim() : "";
        if (!isValidRankName(cleanNew)) {
            throw new IllegalArgumentException(Lang.get("rank.validation.invalid-name"));
        }
        if (!rank.getName().equalsIgnoreCase(cleanNew) && exists(cleanNew)) {
            throw new IllegalArgumentException(Lang.get("rank.validation.already-exists", "name", cleanNew));
        }

        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);

        // Alten Abschnitt kopieren und entfernen
        var sec = cfg.getConfigurationSection(rank.getName());
        if (sec != null) {
            cfg.set(cleanNew, sec);
            cfg.set(rank.getName(), null);
        }
        cfg.set(cleanNew + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        // Cache bereinigen
        rankCache.remove(rank.getName().toLowerCase(Locale.ROOT));
        Rank updated = new Rank(cleanNew, rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(cleanNew.toLowerCase(Locale.ROOT), updated);

        // Verweise in anderen Rängen (Chain previous/next) aktualisieren
        for (Rank other : getAllRanks()) {
            boolean mod = false;
            String prev = other.getPreviousRank();
            String next = other.getNextRank();
            if (prev != null && prev.equalsIgnoreCase(rank.getName())) {
                setRankPrevious(other.getName(), cleanNew);
                mod = true;
            }
            if (next != null && next.equalsIgnoreCase(rank.getName())) {
                setRankNext(other.getName(), cleanNew);
                mod = true;
            }
        }

        // playerdata.yml aktualisieren (rank und rank_fallback)
        FileConfiguration pData = DataManager.getConfiguration("", "playerdata.yml");
        boolean modPData = false;
        for (String uKey : pData.getKeys(false)) {
            if (rank.getName().equalsIgnoreCase(pData.getString(uKey + ".rank"))) {
                pData.set(uKey + ".rank", cleanNew);
                modPData = true;
            }
            if (rank.getName().equalsIgnoreCase(pData.getString(uKey + ".rank_fallback"))) {
                pData.set(uKey + ".rank_fallback", cleanNew);
                modPData = true;
            }
        }
        if (modPData) {
            DataManager.saveConfiguration("", "playerdata.yml", pData);
        }

        // Standard-Rang anpassen falls nötig
        if (getDefaultRankName().equalsIgnoreCase(rank.getName())) {
            setDefaultRank(cleanNew);
        }

        PrefixManager.updateAllTabLists();
        return true;
    }

    /**
     * Löscht einen Rang vollständig.
     */
    public static synchronized boolean deleteRank(String name) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        String canonicalName = rank.getName();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(canonicalName, null);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        rankCache.remove(canonicalName.toLowerCase(Locale.ROOT));

        // Verweise in Chains anderer Ränge bereinigen
        for (Rank other : getAllRanks()) {
            if (canonicalName.equalsIgnoreCase(other.getPreviousRank())) {
                setRankPrevious(other.getName(), null);
            }
            if (canonicalName.equalsIgnoreCase(other.getNextRank())) {
                setRankNext(other.getName(), null);
            }
        }

        // Dangling Rank-Referenzen in playerdata.yml bereinigen
        FileConfiguration pData = DataManager.getConfiguration("", "playerdata.yml");
        boolean modifiedPData = false;
        for (String uKey : pData.getKeys(false)) {
            if (canonicalName.equalsIgnoreCase(pData.getString(uKey + ".rank"))) {
                // Fallback nutzen falls vorhanden
                String fallback = pData.getString(uKey + ".rank_fallback");
                if (fallback != null && !fallback.equalsIgnoreCase(canonicalName) && exists(fallback)) {
                    pData.set(uKey + ".rank", fallback);
                    pData.set(uKey + ".rank_expires", -1L);
                    pData.set(uKey + ".rank_fallback", null);
                } else {
                    pData.set(uKey + ".rank", null);
                    pData.set(uKey + ".rank_assigned_at", null);
                    pData.set(uKey + ".rank_expires", null);
                    pData.set(uKey + ".rank_fallback", null);
                }
                modifiedPData = true;
            }
            if (canonicalName.equalsIgnoreCase(pData.getString(uKey + ".rank_fallback"))) {
                pData.set(uKey + ".rank_fallback", null);
                modifiedPData = true;
            }
        }
        if (modifiedPData) {
            DataManager.saveConfiguration("", "playerdata.yml", pData);
        }

        // Falls der gelöschte Rang der Standard-Rang war
        if (getDefaultRankName().equalsIgnoreCase(canonicalName)) {
            if (!rankCache.isEmpty()) {
                String next = rankCache.values().iterator().next().getName();
                setDefaultRank(next);
            } else {
                setDefaultRank("");
            }
        }

        if (permissionService != null && isRanksEnabled()) {
            permissionService.updateAll();
        }
        PrefixManager.updateAllTabLists();
        return true;
    }

    public static synchronized boolean setRankColor(String name, String colorInput) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        String normalizedColor = normalizeSingleColor(colorInput);
        if (normalizedColor == null) {
            throw new IllegalArgumentException(Lang.get("rank.validation.invalid-color"));
        }

        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(rank.getName() + ".color", normalizedColor);
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), rank.getPrefix(), normalizedColor, rank.getDefaultDuration(),
                rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);

        PrefixManager.updateAllTabLists();
        return true;
    }

    public static synchronized boolean setRankPrefix(String name, String prefix) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        String safePrefix = prefix != null ? prefix : "";
        long now = System.currentTimeMillis();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(rank.getName() + ".prefix", safePrefix);
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), safePrefix, rank.getColor(), rank.getDefaultDuration(),
                rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);

        PrefixManager.updateAllTabLists();
        return true;
    }

    public static synchronized boolean setRankDuration(String name, long durationMillis) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        long safeDuration = (durationMillis <= 0 && durationMillis != -1) ? -1 : durationMillis;
        long now = System.currentTimeMillis();

        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        if (safeDuration > 0) {
            cfg.set(rank.getName() + ".duration", safeDuration);
        } else {
            cfg.set(rank.getName() + ".duration", "perm");
        }
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), safeDuration,
                rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);
        return true;
    }

    public static synchronized boolean setRankPrevious(String name, String previousRankName) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        String safePrev = (previousRankName != null && !previousRankName.isBlank() && !previousRankName.equalsIgnoreCase("none") && !previousRankName.equalsIgnoreCase("off"))
                ? previousRankName.trim() : null;
        if (safePrev != null && !exists(safePrev)) {
            return false;
        }

        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(rank.getName() + ".previous", safePrev);
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                safePrev, rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);
        return true;
    }

    public static synchronized boolean setRankNext(String name, String nextRankName) {
        Rank rank = getRank(name);
        if (rank == null) return false;

        String safeNext = (nextRankName != null && !nextRankName.isBlank() && !nextRankName.equalsIgnoreCase("none") && !nextRankName.equalsIgnoreCase("off"))
                ? nextRankName.trim() : null;
        if (safeNext != null && !exists(safeNext)) {
            return false;
        }

        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        cfg.set(rank.getName() + ".next", safeNext);
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                rank.getPreviousRank(), safeNext, rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), rank.getOffers());
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);
        return true;
    }

    // =========================================================================
    // PERMISSIONS
    // =========================================================================

    public static synchronized boolean addPermission(String name, String permission) {
        return addPermission(name, permission, null);
    }

    public static synchronized boolean addPermission(String name, String permission, Collection<String> worlds) {
        Rank rank = getRank(name);
        if (rank == null || permission == null || permission.isBlank()) return false;

        String cleanPerm = permission.trim();
        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        boolean modified = false;

        List<String> newGlobalPerms = new ArrayList<>(rank.getPermissions());
        Map<String, List<String>> newWorldPerms = new LinkedHashMap<>(rank.getWorldPermissions());

        if (worlds == null || worlds.isEmpty()) {
            boolean found = newGlobalPerms.stream().anyMatch(p -> p.equalsIgnoreCase(cleanPerm));
            if (!found) {
                newGlobalPerms.add(cleanPerm);
                cfg.set(rank.getName() + ".permissions", newGlobalPerms);
                modified = true;
            }
        } else {
            for (String w : worlds) {
                if (w == null || w.isBlank()) continue;
                String worldKey = w.trim().toLowerCase(Locale.ROOT);
                List<String> list = new ArrayList<>(newWorldPerms.getOrDefault(worldKey, Collections.emptyList()));
                boolean found = list.stream().anyMatch(p -> p.equalsIgnoreCase(cleanPerm));
                if (!found) {
                    list.add(cleanPerm);
                    newWorldPerms.put(worldKey, Collections.unmodifiableList(list));
                    cfg.set(rank.getName() + ".world-permissions." + worldKey, list);
                    modified = true;
                }
            }
        }

        if (modified) {
            cfg.set(rank.getName() + ".lastchange", now);
            DataManager.saveConfiguration(FOLDER, FILE, cfg);

            Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                    rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                    newGlobalPerms, newWorldPerms, rank.getOffers());
            rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);

            if (permissionService != null && isRanksEnabled()) {
                permissionService.updateAll();
            }
        }
        return modified;
    }

    public static synchronized boolean removePermission(String name, String permission) {
        return removePermission(name, permission, null);
    }

    public static synchronized boolean removePermission(String name, String permission, Collection<String> worlds) {
        Rank rank = getRank(name);
        if (rank == null || permission == null || permission.isBlank()) return false;

        String cleanPerm = permission.trim();
        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        boolean modified = false;

        List<String> newGlobalPerms = new ArrayList<>(rank.getPermissions());
        Map<String, List<String>> newWorldPerms = new LinkedHashMap<>(rank.getWorldPermissions());

        if (worlds == null || worlds.isEmpty()) {
            boolean removed = newGlobalPerms.removeIf(p -> p.equalsIgnoreCase(cleanPerm));
            if (removed) {
                cfg.set(rank.getName() + ".permissions", newGlobalPerms);
                modified = true;
            }
        } else {
            for (String w : worlds) {
                if (w == null || w.isBlank()) continue;
                String worldKey = w.trim().toLowerCase(Locale.ROOT);
                List<String> list = new ArrayList<>(newWorldPerms.getOrDefault(worldKey, Collections.emptyList()));
                boolean removed = list.removeIf(p -> p.equalsIgnoreCase(cleanPerm));
                if (removed) {
                    if (list.isEmpty()) {
                        newWorldPerms.remove(worldKey);
                        cfg.set(rank.getName() + ".world-permissions." + worldKey, null);
                        String basePath = rank.getName() + ".world-permissions";
                        var sec = cfg.getConfigurationSection(basePath);
                        if (sec != null && sec.getKeys(false).isEmpty()) {
                            cfg.set(basePath, null);
                        }
                    } else {
                        newWorldPerms.put(worldKey, Collections.unmodifiableList(list));
                        cfg.set(rank.getName() + ".world-permissions." + worldKey, list);
                    }
                    modified = true;
                }
            }
        }

        if (modified) {
            cfg.set(rank.getName() + ".lastchange", now);
            DataManager.saveConfiguration(FOLDER, FILE, cfg);

            Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                    rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                    newGlobalPerms, newWorldPerms, rank.getOffers());
            rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);

            if (permissionService != null && isRanksEnabled()) {
                permissionService.updateAll();
            }
        }
        return modified;
    }

    // =========================================================================
    // SHOP & ANGEBOTE (OFFERS)
    // =========================================================================

    public static synchronized boolean addOffer(String name, RankOffer offer) {
        Rank rank = getRank(name);
        if (rank == null || offer == null) return false;

        List<RankOffer> newOffers = new ArrayList<>(rank.getOffers());
        newOffers.add(offer);
        return saveOffers(rank, newOffers);
    }

    public static synchronized boolean removeOffer(String name, int index) {
        Rank rank = getRank(name);
        if (rank == null) return false;
        List<RankOffer> newOffers = new ArrayList<>(rank.getOffers());
        if (index < 0 || index >= newOffers.size()) return false;
        newOffers.remove(index);
        return saveOffers(rank, newOffers);
    }

    public static synchronized boolean clearOffers(String name) {
        Rank rank = getRank(name);
        if (rank == null) return false;
        return saveOffers(rank, Collections.emptyList());
    }

    public static synchronized boolean setRankPrice(String name, double price) {
        Rank rank = getRank(name);
        if (rank == null) return false;
        if (price < 0.0 || !Double.isFinite(price)) {
            return clearOffers(name);
        }
        // Setzt genau ein Angebot mit der Standarddauer des Rangs
        RankOffer single = new RankOffer(price, null);
        return saveOffers(rank, List.of(single));
    }

    public static synchronized boolean removeRankPrice(String name) {
        return clearOffers(name);
    }

    private static synchronized boolean saveOffers(Rank rank, List<RankOffer> offers) {
        long now = System.currentTimeMillis();
        FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RankOffer o : offers) {
            serialized.add(o.toMap());
        }

        if (serialized.isEmpty()) {
            cfg.set(rank.getName() + ".offers", null);
        } else {
            cfg.set(rank.getName() + ".offers", serialized);
        }
        cfg.set(rank.getName() + ".lastchange", now);
        DataManager.saveConfiguration(FOLDER, FILE, cfg);

        Rank updated = new Rank(rank.getName(), rank.getPrefix(), rank.getColor(), rank.getDefaultDuration(),
                rank.getPreviousRank(), rank.getNextRank(), rank.getCreated(), now,
                rank.getPermissions(), rank.getWorldPermissions(), offers);
        rankCache.put(rank.getName().toLowerCase(Locale.ROOT), updated);
        return true;
    }

    // =========================================================================
    // RANG-KETTEN (CHAIN / PROMOTE / DEMOTE)
    // =========================================================================

    /**
     * Prüft, ob 'higher' weiter oben in der Kette (über 'next') von 'lower' aus erreichbar ist.
     */
    public static boolean isHigherInChain(Rank higher, Rank lower) {
        if (higher == null || lower == null) return false;
        if (higher.equals(lower)) return false;

        Set<String> visited = new HashSet<>();
        Rank cur = lower;
        while (cur != null && cur.hasNextRank() && visited.add(cur.getName().toLowerCase(Locale.ROOT))) {
            Rank next = getRank(cur.getNextRank());
            if (next != null && next.equals(higher)) {
                return true;
            }
            cur = next;
        }
        return false;
    }

    /**
     * Prüft, ob 'lower' weiter unten in der Kette (über 'previous') von 'higher' aus erreichbar ist.
     */
    public static boolean isLowerInChain(Rank lower, Rank higher) {
        if (lower == null || higher == null) return false;
        if (lower.equals(higher)) return false;

        Set<String> visited = new HashSet<>();
        Rank cur = higher;
        while (cur != null && cur.hasPreviousRank() && visited.add(cur.getName().toLowerCase(Locale.ROOT))) {
            Rank prev = getRank(cur.getPreviousRank());
            if (prev != null && prev.equals(lower)) {
                return true;
            }
            cur = prev;
        }
        return false;
    }

    public static synchronized boolean promotePlayer(UUID uuid, Long overrideDuration) {
        if (uuid == null) return false;
        Rank current = getRankOfPlayer(uuid);
        if (current == null || !current.hasNextRank()) return false;

        Rank next = getRank(current.getNextRank());
        if (next == null) return false;

        return setPlayerRank(uuid, next.getName(), overrideDuration);
    }

    public static synchronized boolean demotePlayer(UUID uuid, Long overrideDuration) {
        if (uuid == null) return false;
        Rank current = getRankOfPlayer(uuid);
        if (current == null || !current.hasPreviousRank()) return false;

        Rank prev = getRank(current.getPreviousRank());
        if (prev == null) return false;

        return setPlayerRank(uuid, prev.getName(), overrideDuration);
    }

    // =========================================================================
    // SPIELER-RANG ZUWEISUNG (PLAYERDATA.YML)
    // =========================================================================

    public static synchronized boolean setPlayerRank(UUID uuid, String rankName) {
        return setPlayerRank(uuid, rankName, null);
    }

    /**
     * Weist einem Spieler einen Rang zu (mit optionaler Dauer-Überschreibung).
     *
     * @param uuid                   Spieler-UUID
     * @param rankName               Name des Zielrangs
     * @param overrideDurationMillis Überschreibende Dauer (null = Standarddauer des Rangs, -1 = permanent, >0 = ms)
     * @return true bei Erfolg
     */
    public static synchronized boolean setPlayerRank(UUID uuid, String rankName, Long overrideDurationMillis) {
        if (uuid == null || rankName == null) return false;
        Rank rank = getRank(rankName);
        if (rank == null) return false;

        long now = System.currentTimeMillis();
        long duration = (overrideDurationMillis != null) ? overrideDurationMillis : rank.getDefaultDuration();

        if (duration <= 0 && duration != -1) {
            duration = -1;
        }

        String currentAssigned = getPlayerAssignedRankName(uuid);
        long currentExpires = DataManager.getLong("", "playerdata.yml", uuid + ".rank_expires");

        if (duration == -1) {
            // Permanent: Fallback wird nicht mehr gebraucht
            DataManager.set("", "playerdata.yml", uuid + ".rank", rank.getName());
            DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", now);
            DataManager.set("", "playerdata.yml", uuid + ".rank_expires", -1L);
            DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", null);
        } else {
            // Temporärer Rang:
            if (currentAssigned != null && currentAssigned.equalsIgnoreCase(rank.getName()) && currentExpires > 0) {
                // Spieler hat denselben Rang bereits temporär -> Zeit verlängern!
                long base = Math.max(now, currentExpires);
                long newExpires = base + duration;
                DataManager.set("", "playerdata.yml", uuid + ".rank_expires", newExpires);
            } else {
                // Neuer temporärer Rang: Falls der bisherige Rang permanent war und nicht Default ist -> als Fallback sichern
                Rank currentRank = getRankOfPlayer(uuid);
                String existingFallback = DataManager.getString("", "playerdata.yml", uuid + ".rank_fallback");
                if (currentExpires == -1 && currentRank != null && !isDefaultRank(currentRank.getName())) {
                    DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", currentRank.getName());
                } else if (existingFallback == null || existingFallback.isBlank()) {
                    // Falls noch kein Fallback existiert, aber Spieler aktuell einen permanenten Rang besitzt
                    if (currentRank != null && !isDefaultRank(currentRank.getName()) && currentExpires <= 0) {
                        DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", currentRank.getName());
                    }
                }

                DataManager.set("", "playerdata.yml", uuid + ".rank", rank.getName());
                DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", now);
                DataManager.set("", "playerdata.yml", uuid + ".rank_expires", now + duration);
            }
        }

        Player online = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
        if (online != null && online.isOnline()) {
            PlayerResolver.recordPlayer(online);
            if (permissionService != null && isRanksEnabled()) {
                permissionService.applyPermissions(online);
            }
            PrefixManager.updateTabList(online);
        }
        return true;
    }

    public static synchronized void removePlayerRank(UUID uuid) {
        if (uuid == null) return;
        DataManager.set("", "playerdata.yml", uuid + ".rank", null);
        DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", null);
        DataManager.set("", "playerdata.yml", uuid + ".rank_expires", null);
        DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", null);

        Player online = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
        if (online != null && online.isOnline()) {
            if (permissionService != null && isRanksEnabled()) {
                permissionService.applyPermissions(online);
            }
            PrefixManager.updateTabList(online);
        }
    }

    /**
     * Prüft, ob der Rang eines Spielers abgelaufen ist und führt den Fallback aus.
     */
    public static synchronized boolean checkAndHandleExpiration(UUID uuid, boolean notifyIfOnline) {
        if (uuid == null) return false;

        String assigned = DataManager.getString("", "playerdata.yml", uuid + ".rank");
        if (assigned == null || assigned.isBlank()) return false;

        long expires = DataManager.getLong("", "playerdata.yml", uuid + ".rank_expires");
        if (expires <= 0) {
            // -1 oder nicht gesetzt = permanent
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < expires) {
            return false;
        }

        // Rang ist ABGELAUFEN!
        String expiredRankName = assigned;
        String fallback = DataManager.getString("", "playerdata.yml", uuid + ".rank_fallback");
        Rank fallbackRank = (fallback != null && !fallback.isBlank()) ? getRank(fallback) : null;

        if (fallbackRank != null) {
            DataManager.set("", "playerdata.yml", uuid + ".rank", fallbackRank.getName());
            DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", now);
            DataManager.set("", "playerdata.yml", uuid + ".rank_expires", -1L);
            DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", null);
        } else {
            DataManager.set("", "playerdata.yml", uuid + ".rank", null);
            DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", null);
            DataManager.set("", "playerdata.yml", uuid + ".rank_expires", null);
            DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", null);
        }

        Player online = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
        if (online != null && online.isOnline()) {
            if (permissionService != null && isRanksEnabled()) {
                permissionService.applyPermissions(online);
            }
            PrefixManager.updateTabList(online);

            if (notifyIfOnline) {
                Rank active = getRankOfPlayer(uuid);
                String activeName = (active != null) ? active.getName() : Lang.get("common.none");
                online.sendMessage(Lang.getPrefix() + Lang.get("rank.expired-notify",
                        "rank", expiredRankName,
                        "current", activeName));
            }
        }

        return true;
    }

    public static String getPlayerAssignedRankName(UUID uuid) {
        if (uuid == null) return null;
        checkAndHandleExpiration(uuid, false);
        return DataManager.getString("", "playerdata.yml", uuid + ".rank");
    }

    public static String getPlayerAssignedRankId(UUID uuid) {
        return getPlayerAssignedRankName(uuid);
    }

    public static long getPlayerExpiresAt(UUID uuid) {
        if (uuid == null) return -1;
        checkAndHandleExpiration(uuid, false);
        return DataManager.getLong("", "playerdata.yml", uuid + ".rank_expires");
    }

    public static boolean isPlayerRankPermanent(UUID uuid) {
        long exp = getPlayerExpiresAt(uuid);
        return exp <= 0;
    }

    public static String getPlayerFallbackRankName(UUID uuid) {
        if (uuid == null) return null;
        return DataManager.getString("", "playerdata.yml", uuid + ".rank_fallback");
    }

    public static long getPlayerRemainingMillis(UUID uuid) {
        if (uuid == null) return 0;
        checkAndHandleExpiration(uuid, false);
        long exp = getPlayerExpiresAt(uuid);
        if (exp <= 0) return -1;
        return Math.max(0, exp - System.currentTimeMillis());
    }

    public static String getFormattedPlayerRemainingTime(UUID uuid) {
        if (uuid == null) return Lang.get("duration.zero");
        long rem = getPlayerRemainingMillis(uuid);
        if (rem == -1) return Lang.get("common.permanent");
        if (rem == 0) return Lang.get("duration.zero");
        return DurationUtil.formatDuration(rem);
    }

    public static Rank getRankOfPlayer(UUID uuid) {
        if (uuid == null) return getDefaultRank();

        checkAndHandleExpiration(uuid, false);

        String assignedName = DataManager.getString("", "playerdata.yml", uuid + ".rank");
        if (assignedName != null && !assignedName.isBlank()) {
            Rank rank = getRank(assignedName);
            if (rank != null) {
                return rank;
            } else {
                // Auto-Heal dangling reference
                DataManager.set("", "playerdata.yml", uuid + ".rank", null);
                DataManager.set("", "playerdata.yml", uuid + ".rank_assigned_at", null);
                DataManager.set("", "playerdata.yml", uuid + ".rank_expires", null);
                DataManager.set("", "playerdata.yml", uuid + ".rank_fallback", null);
            }
        }

        return getDefaultRank();
    }

    public static Rank getRankOfPlayer(Player player) {
        if (player == null) return getDefaultRank();
        return getRankOfPlayer(player.getUniqueId());
    }
}
