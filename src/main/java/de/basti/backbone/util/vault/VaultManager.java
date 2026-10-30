package de.basti.backbone.util.vault;

import de.basti.backbone.Backbone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Sicherer Manager für die Vault-Integration von Backbone.
 * <p>
 * Diese Klasse verwendet in ihren öffentlichen Methoden-Signaturen keinerlei Klassen aus {@code net.milkbowl.vault.*},
 * sodass Backbone auch ohne installiertes Vault-Plugin komplett fehlerfrei (ohne {@link NoClassDefFoundError}) läuft.
 * <p>
 * Ist Vault auf dem Server installiert, übernimmt Backbone zwei Aufgaben:
 * <ul>
 *   <li><b>Provider:</b> Stellt Backbones Economy-, Rang/Permission- und Chat/Präfix-System für andere Plugins bereit.</li>
 *   <li><b>Consumer (Fallback):</b> Liest Daten von externen Vault-Plugins (z.B. LuckPerms, EssentialsX) für Scoreboard und Chat aus,
 *       falls das jeweilige Backbone-Modul in der {@code config.yml} deaktiviert ist.</li>
 * </ul>
 */
public final class VaultManager {

    private static Backbone plugin;
    private static boolean vaultAvailable = false;

    private VaultManager() {
    }

    /**
     * Initialisiert die Vault-Integration beim Serverstart und registriert aktive Backbone-Module als Vault-Provider.
     */
    public static void init(Backbone backboneInstance) {
        plugin = backboneInstance;
        vaultAvailable = checkVaultInstalled();

        if (!vaultAvailable) {
            return;
        }

        try {
            VaultHook.syncServices(plugin);
        } catch (Throwable t) {
            vaultAvailable = false;
            Backbone.warnMsg("Could not initialize Vault integration: " + t.getMessage());
        }
    }

    /**
     * Synchronisiert die registrierten Vault-Services neu (z.B. nach {@code /backbone reload} oder Modul-Änderungen).
     */
    public static void reload() {
        vaultAvailable = checkVaultInstalled();
        if (!vaultAvailable || plugin == null) {
            return;
        }
        try {
            VaultHook.syncServices(plugin);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Meldet alle von Backbone registrierten Vault-Provider beim Herunterfahren sauber ab.
     */
    public static void shutdown() {
        if (!vaultAvailable) {
            plugin = null;
            return;
        }
        try {
            VaultHook.unregisterAll();
        } catch (Throwable ignored) {
        }
        vaultAvailable = false;
        plugin = null;
    }

    /**
     * Gibt zurück, ob Vault auf dem Server gefunden und erfolgreich eingebunden wurde.
     */
    public static boolean isVaultAvailable() {
        return vaultAvailable;
    }

    private static boolean checkVaultInstalled() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                return false;
            }
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault == null) {
                return false;
            }
            Class.forName("net.milkbowl.vault.economy.Economy");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =========================================================================
    // FALLBACK-METHODEN FÜR SCOREBOARD & CHAT (WENN EIGENE MODULE DEAKTIVIERT SIND)
    // =========================================================================

    public static boolean hasExternalEconomy() {
        if (!vaultAvailable) return false;
        try {
            return VaultHook.hasExternalEconomy();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Double getExternalBalance(java.util.UUID uuid) {
        if (!vaultAvailable || uuid == null) return null;
        try {
            return VaultHook.getExternalBalance(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean withdrawExternal(java.util.UUID uuid, double amount) {
        if (!vaultAvailable || uuid == null) return false;
        try {
            return VaultHook.withdrawExternal(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean depositExternal(java.util.UUID uuid, double amount) {
        if (!vaultAvailable || uuid == null) return false;
        try {
            return VaultHook.depositExternal(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Double getExternalBalance(Player player) {
        if (!vaultAvailable || player == null) return null;
        try {
            return VaultHook.getExternalBalance(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getExternalCurrencySymbol() {
        if (!vaultAvailable) return null;
        try {
            return VaultHook.getExternalCurrencySymbol();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getExternalRankName(Player player) {
        if (!vaultAvailable || player == null) return "";
        try {
            return VaultHook.getExternalRankName(player);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String getExternalRankPrefix(Player player) {
        if (!vaultAvailable || player == null) return "";
        try {
            return VaultHook.getExternalRankPrefix(player);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String getExternalPlayerPrefix(Player player) {
        if (!vaultAvailable || player == null) return "";
        try {
            return VaultHook.getExternalPlayerPrefix(player);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
