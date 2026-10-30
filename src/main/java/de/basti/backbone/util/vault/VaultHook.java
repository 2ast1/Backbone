package de.basti.backbone.util.vault;

import de.basti.backbone.Backbone;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.economy.EconomyManager;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Interne Implementierungsklasse für die direkte Kommunikation mit der Vault-API.
 * Wird ausschließlich über {@link VaultManager} geladen, wenn das Vault-Plugin auf dem Server aktiv ist,
 * um {@link NoClassDefFoundError} auf Servern ohne Vault sicher auszuschließen.
 */
final class VaultHook {

    private static BackboneVaultEconomy economyProvider;
    private static BackboneVaultPermission permissionProvider;
    private static BackboneVaultChat chatProvider;

    private static boolean economyRegistered = false;
    private static boolean permissionRegistered = false;
    private static boolean chatRegistered = false;

    private VaultHook() {
    }

    static synchronized void syncServices(Backbone plugin) {
        if (plugin == null || Bukkit.getServer() == null) return;
        ServicesManager sm = Bukkit.getServicesManager();
        if (sm == null) return;

        if (economyProvider == null) {
            economyProvider = new BackboneVaultEconomy();
        }
        if (permissionProvider == null) {
            permissionProvider = new BackboneVaultPermission(plugin);
        }
        if (chatProvider == null) {
            chatProvider = new BackboneVaultChat(permissionProvider);
        }

        List<String> activeHooks = new ArrayList<>();

        // 1. Economy-Provider synchronisieren
        if (EconomyManager.isEconomyEnabled()) {
            if (!economyRegistered) {
                sm.register(Economy.class, economyProvider, plugin, ServicePriority.Normal);
                economyRegistered = true;
            }
            activeHooks.add("Economy");
        } else if (economyRegistered) {
            sm.unregister(Economy.class, economyProvider);
            economyRegistered = false;
        }

        // 2. Permission-Provider (Ränge & Rechte) synchronisieren
        if (RankManager.isRanksEnabled()) {
            if (!permissionRegistered) {
                sm.register(Permission.class, permissionProvider, plugin, ServicePriority.Normal);
                permissionRegistered = true;
            }
            activeHooks.add("Permission");
        } else if (permissionRegistered) {
            sm.unregister(Permission.class, permissionProvider);
            permissionRegistered = false;
        }

        // 3. Chat-Provider (Präfixe) synchronisieren
        if (PrefixManager.isPrefixEnabled() || RankManager.isRanksEnabled()) {
            if (!chatRegistered) {
                sm.register(Chat.class, chatProvider, plugin, ServicePriority.Normal);
                chatRegistered = true;
            }
            activeHooks.add("Chat");
        } else if (chatRegistered) {
            sm.unregister(Chat.class, chatProvider);
            chatRegistered = false;
        }

        if (!activeHooks.isEmpty()) {
            Backbone.infoMsg("Vault integration active (" + String.join(", ", activeHooks) + ").");
        }
    }

    static synchronized void unregisterAll() {
        try {
            if (Bukkit.getServer() != null) {
                ServicesManager sm = Bukkit.getServicesManager();
                if (sm != null) {
                    if (economyRegistered && economyProvider != null) {
                        sm.unregister(Economy.class, economyProvider);
                    }
                    if (permissionRegistered && permissionProvider != null) {
                        sm.unregister(Permission.class, permissionProvider);
                    }
                    if (chatRegistered && chatProvider != null) {
                        sm.unregister(Chat.class, chatProvider);
                    }
                }
            }
        } finally {
            economyProvider = null;
            permissionProvider = null;
            chatProvider = null;

            economyRegistered = false;
            permissionRegistered = false;
            chatRegistered = false;
        }
    }

    static boolean isEconomyProvidedByBackbone() {
        return economyRegistered;
    }

    static boolean isPermissionProvidedByBackbone() {
        return permissionRegistered;
    }

    static boolean isChatProvidedByBackbone() {
        return chatRegistered;
    }

    // =========================================================================
    // EXTERNE VAULT-PROVIDER AUSLESEN (FALLBACK / CONSUMER)
    // =========================================================================

    private static Economy findExternalEconomy() {
        if (Bukkit.getServer() == null || Bukkit.getServicesManager() == null) return null;
        Collection<RegisteredServiceProvider<Economy>> regs = Bukkit.getServicesManager().getRegistrations(Economy.class);
        if (regs != null) {
            for (RegisteredServiceProvider<Economy> reg : regs) {
                if (reg != null && reg.getProvider() != null && !(reg.getProvider() instanceof BackboneVaultEconomy)) {
                    if (reg.getProvider().isEnabled()) {
                        return reg.getProvider();
                    }
                }
            }
        }
        return null;
    }

    private static Permission findExternalPermission() {
        if (Bukkit.getServer() == null || Bukkit.getServicesManager() == null) return null;
        Collection<RegisteredServiceProvider<Permission>> regs = Bukkit.getServicesManager().getRegistrations(Permission.class);
        if (regs != null) {
            for (RegisteredServiceProvider<Permission> reg : regs) {
                if (reg != null && reg.getProvider() != null && !(reg.getProvider() instanceof BackboneVaultPermission)) {
                    if (reg.getProvider().isEnabled()) {
                        return reg.getProvider();
                    }
                }
            }
        }
        return null;
    }

    private static Chat findExternalChat() {
        if (Bukkit.getServer() == null || Bukkit.getServicesManager() == null) return null;
        Collection<RegisteredServiceProvider<Chat>> regs = Bukkit.getServicesManager().getRegistrations(Chat.class);
        if (regs != null) {
            for (RegisteredServiceProvider<Chat> reg : regs) {
                if (reg != null && reg.getProvider() != null && !(reg.getProvider() instanceof BackboneVaultChat)) {
                    if (reg.getProvider().isEnabled()) {
                        return reg.getProvider();
                    }
                }
            }
        }
        return null;
    }

    static boolean hasExternalEconomy() {
        return findExternalEconomy() != null;
    }

    static Double getExternalBalance(UUID uuid) {
        if (uuid == null) return null;
        Economy eco = findExternalEconomy();
        if (eco == null) return null;
        try {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return eco.getBalance(op);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean withdrawExternal(UUID uuid, double amount) {
        if (uuid == null || amount < 0.01) return false;
        Economy eco = findExternalEconomy();
        if (eco == null) return false;
        try {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            var resp = eco.withdrawPlayer(op, amount);
            return resp != null && resp.transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean depositExternal(UUID uuid, double amount) {
        if (uuid == null || amount < 0.01) return false;
        Economy eco = findExternalEconomy();
        if (eco == null) return false;
        try {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            var resp = eco.depositPlayer(op, amount);
            return resp != null && resp.transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Double getExternalBalance(Player player) {
        if (player == null) return null;
        Economy eco = findExternalEconomy();
        if (eco == null) return null;
        try {
            return eco.getBalance(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String getExternalCurrencySymbol() {
        Economy eco = findExternalEconomy();
        if (eco == null) return null;
        try {
            String name = eco.currencyNamePlural();
            if (name == null || name.isBlank()) {
                name = eco.currencyNameSingular();
            }
            return (name != null && !name.isBlank()) ? name : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String getExternalRankName(Player player) {
        if (player == null) return "";
        Permission perm = findExternalPermission();
        if (perm == null || !perm.hasGroupSupport()) return "";
        try {
            String group = perm.getPrimaryGroup(player);
            return group != null ? group : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String getExternalRankPrefix(Player player) {
        if (player == null) return "";
        Chat chat = findExternalChat();
        if (chat == null) return "";
        try {
            String worldName = player.getWorld() != null ? player.getWorld().getName() : null;
            String group = getExternalRankName(player);
            if (group != null && !group.isBlank()) {
                String groupPrefix = chat.getGroupPrefix(worldName, group);
                if (groupPrefix != null && !groupPrefix.isBlank()) {
                    return PrefixManager.formatColors(groupPrefix);
                }
            }
            String playerPrefix = chat.getPlayerPrefix(player);
            return playerPrefix != null ? PrefixManager.formatColors(playerPrefix) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String getExternalPlayerPrefix(Player player) {
        if (player == null) return "";
        Chat chat = findExternalChat();
        if (chat == null) return "";
        try {
            String prefix = chat.getPlayerPrefix(player);
            return prefix != null ? PrefixManager.formatColors(prefix) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
