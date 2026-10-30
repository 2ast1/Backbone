package de.basti.backbone.util.economy;

import de.basti.backbone.util.vault.VaultManager;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Universal economy bridge that delegates transactions either to Backbone's internal
 * {@link EconomyManager} or to an external Vault economy provider if Backbone economy is disabled.
 */
public final class EconomyBridge {

    private EconomyBridge() {}

    /**
     * Checks if any economy provider (internal or external Vault) is available and active.
     */
    public static boolean isAvailable() {
        if (EconomyManager.isEconomyEnabled()) {
            return true;
        }
        return VaultManager.hasExternalEconomy();
    }

    /**
     * Returns the name of the active provider ("Backbone", "Vault" or "None").
     */
    public static String getProviderName() {
        if (EconomyManager.isEconomyEnabled()) {
            return "Backbone";
        }
        if (VaultManager.hasExternalEconomy()) {
            return "Vault";
        }
        return "None";
    }

    /**
     * Returns the formatted representation of an amount (e.g. "100.00 $").
     */
    public static String format(double amount) {
        if (EconomyManager.isEconomyEnabled()) {
            return EconomyManager.format(amount);
        }
        String symbol = VaultManager.getExternalCurrencySymbol();
        return String.format(java.util.Locale.GERMANY, "%,.2f %s", amount, symbol != null ? symbol : "$");
    }

    /**
     * Returns the balance of a player.
     */
    public static double getBalance(UUID uuid) {
        if (uuid == null) return 0.0;
        if (EconomyManager.isEconomyEnabled()) {
            return EconomyManager.getBalance(uuid);
        }
        Double external = VaultManager.getExternalBalance(uuid);
        return external != null && Double.isFinite(external) ? external : 0.0;
    }

    public static double getBalance(Player player) {
        return player != null ? getBalance(player.getUniqueId()) : 0.0;
    }

    /**
     * Checks if a player has at least the specified amount of funds.
     */
    public static boolean hasBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0) return false;
        return getBalance(uuid) >= amount;
    }

    public static boolean hasBalance(Player player, double amount) {
        return player != null && hasBalance(player.getUniqueId(), amount);
    }

    /**
     * Withdraws money from a player's account.
     *
     * @return true if successful, false if insufficient funds or transaction error.
     */
    public static boolean withdraw(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0.01) return false;
        if (EconomyManager.isEconomyEnabled()) {
            return EconomyManager.removeBalance(uuid, amount);
        }
        return VaultManager.withdrawExternal(uuid, amount);
    }

    public static boolean withdraw(Player player, double amount) {
        return player != null && withdraw(player.getUniqueId(), amount);
    }

    /**
     * Deposits money into a player's account.
     *
     * @return true if successful, false on error.
     */
    public static boolean deposit(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0.01) return false;
        if (EconomyManager.isEconomyEnabled()) {
            return EconomyManager.addBalance(uuid, amount);
        }
        return VaultManager.depositExternal(uuid, amount);
    }

    public static boolean deposit(Player player, double amount) {
        return player != null && deposit(player.getUniqueId(), amount);
    }
}
