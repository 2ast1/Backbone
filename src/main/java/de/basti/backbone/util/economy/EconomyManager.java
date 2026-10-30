package de.basti.backbone.util.economy;

import de.basti.backbone.util.DataManager;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

/**
 * Verwaltet Spielerguthaben und Wirtschaftstransaktionen.
 * Speichert Balances sauber in playerdata.yml und unterstützt Online- sowie Offline-Spieler.
 */
public class EconomyManager {

    private static final String FOLDER = "";
    private static final String FILE = "playerdata.yml";
    public static final double MAX_BALANCE = 1_000_000_000_000.0; // 1 Billion / Trillion Obergrenze gegen Long-Overflow
    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.GERMANY))
    );
    private static volatile double cachedTotalCirculation = -1.0;
    private static volatile long lastCirculationCacheTime = 0L;
    private static final long CIRCULATION_CACHE_TTL_MS = 15_000L; // 15 Sekunden TTL-Cache

    public static boolean isEconomyEnabled() {
        return DataManager.getConfig().getBoolean("economy.enabled", true);
    }

    public static double getStartingBalance() {
        return DataManager.getDouble("", "config.yml", "economy.starting-balance");
    }

    public static String getCurrencySymbol() {
        String sym = DataManager.getString("", "config.yml", "economy.currency-symbol");
        return (sym != null && !sym.isBlank()) ? sym : "$";
    }

    public static String getCurrencyName() {
        String name = DataManager.getString("", "config.yml", "economy.currency-name");
        return (name != null && !name.isBlank()) ? name : "Coins";
    }

    /**
     * Gibt den formatierten Betrag inklusive Währungssymbol zurück (z.B. "1.250,50 $").
     */
    public static String format(double amount) {
        double safe = Double.isFinite(amount) ? amount : 0.0;
        return FORMATTER.get().format(safe) + " " + getCurrencySymbol();
    }

    public static boolean hasAccount(UUID uuid) {
        if (uuid == null) return false;
        return DataManager.contains(FOLDER, FILE, uuid + ".balance") || DataManager.contains(FOLDER, FILE, uuid + ".name");
    }

    public static synchronized boolean createAccount(UUID uuid) {
        if (uuid == null) return false;
        if (hasAccount(uuid)) return false;
        setBalance(uuid, getStartingBalance());
        return true;
    }

    public static double getBalance(UUID uuid) {
        if (uuid == null) return 0.0;
        String key = uuid + ".balance";

        if (!DataManager.contains(FOLDER, FILE, key)) {
            return getStartingBalance();
        }

        double val = DataManager.getDouble(FOLDER, FILE, key);
        return Double.isFinite(val) ? val : 0.0;
    }

    public static synchronized void setBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount)) return;
        double clamped = Math.max(0.0, Math.min(MAX_BALANCE, amount));
        double rounded = Math.round(clamped * 100.0) / 100.0;
        DataManager.set(FOLDER, FILE, uuid + ".balance", rounded);
        cachedTotalCirculation = -1.0;
    }

    public static synchronized boolean addBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0.01) return false;
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        if (roundedAmount < 0.01) return false;
        double current = getBalance(uuid);
        if (current + roundedAmount > MAX_BALANCE) {
            return false;
        }
        setBalance(uuid, current + roundedAmount);
        return true;
    }

    public static synchronized boolean removeBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0.01) return false;
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        if (roundedAmount < 0.01) return false;
        double current = getBalance(uuid);
        if (current < roundedAmount) {
            return false;
        }
        setBalance(uuid, current - roundedAmount);
        return true;
    }

    public static boolean hasBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0) return false;
        return getBalance(uuid) >= amount;
    }

    public static synchronized boolean transfer(UUID from, UUID to, double amount) {
        if (from == null || to == null || from.equals(to) || !Double.isFinite(amount) || amount < 0.01) {
            return false;
        }
        double roundedAmount = Math.round(amount * 100.0) / 100.0;
        if (roundedAmount < 0.01) return false;

        double targetBal = getBalance(to);
        if (targetBal + roundedAmount > MAX_BALANCE) {
            return false;
        }

        if (!removeBalance(from, roundedAmount)) {
            return false;
        }

        addBalance(to, roundedAmount);
        return true;
    }

    /**
     * Berechnet die gesamte Geldmenge im Umlauf (Summe aller Kontostände).
     * Nutzt einen 15-Sekunden TTL-Cache zur drastischen Schonung von CPU und Festplatten-I/O.
     */
    public static double getTotalCirculation() {
        long now = System.currentTimeMillis();
        if (cachedTotalCirculation >= 0.0 && (now - lastCirculationCacheTime) < CIRCULATION_CACHE_TTL_MS) {
            return cachedTotalCirculation;
        }
        if (!DataManager.fileExists(FOLDER, FILE)) {
            return 0.0;
        }
        org.bukkit.configuration.file.FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        double total = 0.0;
        for (String key : cfg.getKeys(false)) {
            if (cfg.contains(key + ".balance")) {
                total += cfg.getDouble(key + ".balance", 0.0);
            }
        }
        double rounded = Math.round(total * 100.0) / 100.0;
        cachedTotalCirculation = rounded;
        lastCirculationCacheTime = now;
        return rounded;
    }

    public static boolean isPayBlocked(UUID uuid) {
        if (uuid == null) return false;
        return DataManager.getBoolean(FOLDER, FILE, uuid + ".pay-blocked");
    }

    public static synchronized void setPayBlocked(UUID uuid, boolean blocked) {
        if (uuid == null) return;
        DataManager.set(FOLDER, FILE, uuid + ".pay-blocked", blocked);
    }

    public record BalanceEntry(UUID uuid, String name, double balance) {}

    public static java.util.List<BalanceEntry> getTopBalances() {
        if (!DataManager.fileExists(FOLDER, FILE)) {
            return java.util.Collections.emptyList();
        }
        org.bukkit.configuration.file.FileConfiguration cfg = DataManager.getConfiguration(FOLDER, FILE);
        java.util.List<BalanceEntry> list = new java.util.ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            if (cfg.contains(key + ".balance")) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double bal = cfg.getDouble(key + ".balance", 0.0);
                    String name = cfg.getString(key + ".name");
                    if (name == null || name.isBlank()) {
                        name = de.basti.backbone.util.PlayerResolver.getPlayerName(uuid);
                    }
                    if (name == null || name.isBlank()) {
                        name = key;
                    }
                    list.add(new BalanceEntry(uuid, name, bal));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        list.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        return list;
    }
}
