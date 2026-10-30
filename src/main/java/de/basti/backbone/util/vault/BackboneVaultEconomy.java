package de.basti.backbone.util.vault;

import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.economy.EconomyManager;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Vault Economy-Provider für Backbone.
 * Verbindet das interne Wirtschaftssystem ({@link EconomyManager}) mit der Vault-API,
 * sodass externe Plugins (Shops, Jobs, Auktionshäuser etc.) nahtlos auf Spielerguthaben zugreifen können.
 */
public class BackboneVaultEconomy extends AbstractEconomy {

    private static final EconomyResponse BANK_NOT_SUPPORTED = new EconomyResponse(
            0.0, 0.0, ResponseType.NOT_IMPLEMENTED, "Backbone Economy does not support bank accounts."
    );

    @Override
    public boolean isEnabled() {
        return EconomyManager.isEconomyEnabled();
    }

    @Override
    public String getName() {
        return "Backbone";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return EconomyManager.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return EconomyManager.getCurrencyName();
    }

    @Override
    public String currencyNameSingular() {
        return EconomyManager.getCurrencyName();
    }

    // =========================================================================
    // ACCOUNT & BALANCE METHODEN (OFFLINEPLAYER & STRING)
    // =========================================================================

    private UUID resolve(OfflinePlayer player) {
        if (player == null) return null;
        if (player.getUniqueId() != null) {
            return player.getUniqueId();
        }
        return PlayerResolver.resolveUuid(player.getName());
    }

    private UUID resolve(String playerName) {
        return PlayerResolver.resolveUuid(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        UUID uuid = resolve(player);
        return uuid != null && EconomyManager.hasAccount(uuid);
    }

    @Override
    public boolean hasAccount(String playerName) {
        UUID uuid = resolve(playerName);
        return uuid != null && EconomyManager.hasAccount(uuid);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        UUID uuid = resolve(player);
        if (uuid == null) return 0.0;
        return EconomyManager.getBalance(uuid);
    }

    @Override
    public double getBalance(String playerName) {
        UUID uuid = resolve(playerName);
        if (uuid == null) return 0.0;
        return EconomyManager.getBalance(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        UUID uuid = resolve(player);
        if (uuid == null) return false;
        return EconomyManager.hasBalance(uuid, amount);
    }

    @Override
    public boolean has(String playerName, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        UUID uuid = resolve(playerName);
        if (uuid == null) return false;
        return EconomyManager.hasBalance(uuid, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // =========================================================================
    // TRANSAKTIONEN (WITHDRAW & DEPOSIT)
    // =========================================================================

    private EconomyResponse withdrawByUuid(UUID uuid, double amount) {
        if (uuid == null) {
            return new EconomyResponse(amount, 0.0, ResponseType.FAILURE, "Player not found.");
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.FAILURE, "Cannot withdraw negative funds.");
        }
        if (amount == 0.0) {
            return new EconomyResponse(0.0, EconomyManager.getBalance(uuid), ResponseType.SUCCESS, null);
        }
        if (!EconomyManager.removeBalance(uuid, amount)) {
            return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.FAILURE, "Insufficient funds.");
        }
        return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.SUCCESS, null);
    }

    private EconomyResponse depositByUuid(UUID uuid, double amount) {
        if (uuid == null) {
            return new EconomyResponse(amount, 0.0, ResponseType.FAILURE, "Player not found.");
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.FAILURE, "Cannot deposit negative funds.");
        }
        if (amount == 0.0) {
            return new EconomyResponse(0.0, EconomyManager.getBalance(uuid), ResponseType.SUCCESS, null);
        }
        if (!EconomyManager.addBalance(uuid, amount)) {
            return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.FAILURE, "Deposit exceeds maximum balance or is invalid.");
        }
        return new EconomyResponse(amount, EconomyManager.getBalance(uuid), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawByUuid(resolve(player), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawByUuid(resolve(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositByUuid(resolve(player), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositByUuid(resolve(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        UUID uuid = resolve(player);
        if (uuid == null) return false;
        if (player != null && player.getName() != null) {
            PlayerResolver.recordPlayer(uuid, player.getName());
        }
        return EconomyManager.createAccount(uuid);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = resolve(playerName);
        if (uuid == null && playerName != null && !playerName.isBlank()) {
            try {
                if (org.bukkit.Bukkit.getServer() != null) {
                    uuid = org.bukkit.Bukkit.getOfflinePlayer(playerName).getUniqueId();
                }
            } catch (Throwable ignored) {}
        }
        if (uuid == null) return false;
        if (playerName != null && !playerName.isBlank()) {
            PlayerResolver.recordPlayer(uuid, playerName);
        }
        return EconomyManager.createAccount(uuid);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // =========================================================================
    // BANK-STUBS (NICHT UNTERSTÜTZT)
    // =========================================================================

    @Override
    public EconomyResponse createBank(String name, String player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
