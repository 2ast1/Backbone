package de.basti.backbone.ranks;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.economy.EconomyBridge;

import java.util.*;

/**
 * Service für den Kauf von Rängen mit Ingame-Währung (Coins / Vault).
 * Unterstützt mehrere Kaufangebote pro Rang, Rang-Ketten-Prüfungen (kein Downgrade)
 * und Verlängerung von temporären Rängen bei erneutem Kauf.
 */
public final class RankShop {

    private RankShop() {}

    /**
     * Prüft, ob der Rang-Shop in der config.yml aktiviert ist.
     */
    public static boolean isShopEnabled() {
        return DataManager.getConfig().getBoolean("ranks.buy.enabled", true);
    }

    /**
     * Schaltet den Rang-Shop an oder aus.
     */
    public static void setShopEnabled(boolean enabled) {
        DataManager.setConfig("ranks.buy.enabled", enabled);
    }

    /**
     * Gibt den Upgrade-Modus zurück ("FULL" oder "DIFFERENCE").
     */
    public static String getUpgradeMode() {
        String mode = DataManager.getConfigString("ranks.buy.upgrade-mode");
        return (mode != null && mode.equalsIgnoreCase("DIFFERENCE")) ? "DIFFERENCE" : "FULL";
    }

    /**
     * Prüft, ob ein Rang käuflich ist (mindestens ein Angebot hinterlegt).
     */
    public static boolean isPurchasable(Rank rank) {
        return rank != null && rank.isPurchasable();
    }

    /**
     * Gibt eine Liste aller käuflichen Ränge sortiert nach dem günstigsten Angebot zurück.
     */
    public static List<Rank> getPurchasableRanks() {
        List<Rank> list = new ArrayList<>();
        for (Rank rank : RankManager.getAllRanks()) {
            if (isPurchasable(rank)) {
                list.add(rank);
            }
        }
        list.sort(Comparator.comparingDouble(Rank::getPrice));
        return Collections.unmodifiableList(list);
    }

    /**
     * Berechnet den Preis für das erste Angebot eines Rangs.
     */
    public static double getPriceForPlayer(UUID uuid, Rank targetRank) {
        if (!isPurchasable(targetRank) || targetRank.getOffers().isEmpty()) {
            return -1.0;
        }
        return getPriceForPlayer(uuid, targetRank, targetRank.getOffers().get(0));
    }

    /**
     * Berechnet den Preis für ein spezifisches Angebot unter Berücksichtigung des Upgrade-Modus (DIFFERENCE).
     * Der Differenzmodus gilt nur, wenn sowohl der aktuelle Rang als auch das Zielangebot permanent sind.
     */
    public static double getPriceForPlayer(UUID uuid, Rank targetRank, RankOffer offer) {
        if (targetRank == null || offer == null) {
            return -1.0;
        }

        if (uuid == null) {
            return offer.getPrice();
        }

        if ("DIFFERENCE".equalsIgnoreCase(getUpgradeMode())) {
            Rank current = RankManager.getRankOfPlayer(uuid);
            boolean currentIsPerm = RankManager.isPlayerRankPermanent(uuid);
            boolean offerIsPerm = offer.isPermanent(targetRank);

            if (currentIsPerm && offerIsPerm && current != null && current.isPurchasable()) {
                double diff = offer.getPrice() - current.getPrice();
                return Math.max(0.0, Math.round(diff * 100.0) / 100.0);
            }
        }

        return offer.getPrice();
    }

    public static PurchaseResult canPurchase(UUID uuid, String rankName) {
        return canPurchase(uuid, rankName, 0);
    }

    /**
     * Prüft die Kaufberechtigung für ein spezifisches Angebot (0-basierter Index).
     */
    public static PurchaseResult canPurchase(UUID uuid, String rankName, int offerIndex) {
        if (!isShopEnabled()) {
            return PurchaseResult.failure(PurchaseResult.Status.SHOP_DISABLED, null, "rank.buy.disabled");
        }

        if (!EconomyBridge.isAvailable()) {
            return PurchaseResult.failure(PurchaseResult.Status.ECONOMY_UNAVAILABLE, null, "rank.buy.no-economy");
        }

        if (rankName == null || rankName.isBlank()) {
            return PurchaseResult.failure(PurchaseResult.Status.RANK_NOT_FOUND, null, "rank.not-exists");
        }

        Rank target = RankManager.getRank(rankName.trim());
        if (target == null) {
            return PurchaseResult.failure(PurchaseResult.Status.RANK_NOT_FOUND, null, "rank.not-exists");
        }

        if (!target.isPurchasable() || target.getOffers().isEmpty()) {
            return PurchaseResult.failure(PurchaseResult.Status.NOT_PURCHASABLE, target, "rank.buy.not-for-sale");
        }

        if (offerIndex < 0 || offerIndex >= target.getOffers().size()) {
            return PurchaseResult.failure(PurchaseResult.Status.INVALID_OFFER, target, "rank.buy.invalid-offer");
        }

        RankOffer offer = target.getOffers().get(offerIndex);
        Rank current = RankManager.getRankOfPlayer(uuid);
        boolean currentIsPerm = RankManager.isPlayerRankPermanent(uuid);
        boolean offerIsPerm = offer.isPermanent(target);

        // 1. Bereits permanent im Besitz?
        if (current != null && current.equals(target)) {
            if (currentIsPerm) {
                return PurchaseResult.failure(PurchaseResult.Status.ALREADY_OWNS_RANK, target, "rank.buy.already-owned-permanent");
            }
            // Temporärer Rang: Bei erneutem Kauf wird die Zeit verlängert
        }

        // 2. Kette prüfen: Darf keinen niedrigeren Rang in der Kette kaufen (z.B. VIP kaufen wenn man schon Premium hat)
        if (current != null && RankManager.isLowerInChain(target, current)) {
            return PurchaseResult.failure(PurchaseResult.Status.CANNOT_DOWNGRADE, target, "rank.buy.cannot-downgrade");
        }

        String fallbackName = RankManager.getPlayerFallbackRankName(uuid);
        if (fallbackName != null) {
            Rank fallback = RankManager.getRank(fallbackName);
            if (fallback != null && RankManager.isLowerInChain(target, fallback)) {
                return PurchaseResult.failure(PurchaseResult.Status.CANNOT_DOWNGRADE, target, "rank.buy.cannot-downgrade");
            }
        }

        // 3. Upgrade-Modus DIFFERENCE
        if ("DIFFERENCE".equalsIgnoreCase(getUpgradeMode()) && current != null && currentIsPerm && offerIsPerm && current.isPurchasable()) {
            if (offer.getPrice() <= current.getPrice()) {
                return PurchaseResult.failure(PurchaseResult.Status.NOT_AN_UPGRADE, target, "rank.buy.not-an-upgrade");
            }
        }

        // 4. Guthaben prüfen
        double price = getPriceForPlayer(uuid, target, offer);
        if (!EconomyBridge.hasBalance(uuid, price)) {
            return PurchaseResult.failure(PurchaseResult.Status.INSUFFICIENT_FUNDS, target, "rank.buy.insufficient-funds");
        }

        boolean isExtension = (current != null && current.equals(target) && !currentIsPerm);
        return PurchaseResult.success(target, current, offer, price, EconomyBridge.getBalance(uuid), isExtension);
    }

    public static synchronized PurchaseResult purchase(UUID uuid, String rankName) {
        return purchase(uuid, rankName, 0);
    }

    /**
     * Führt den Kauf eines spezifischen Angebots aus.
     */
    public static synchronized PurchaseResult purchase(UUID uuid, String rankName, int offerIndex) {
        PurchaseResult check = canPurchase(uuid, rankName, offerIndex);
        if (!check.isSuccess()) {
            return check;
        }

        Rank target = check.getTargetRank();
        Rank prev = check.getPreviousRank();
        RankOffer offer = check.getOffer();
        double price = check.getPricePaid();

        if (price > 0.0) {
            boolean charged = EconomyBridge.withdraw(uuid, price);
            if (!charged) {
                return PurchaseResult.failure(PurchaseResult.Status.TRANSACTION_FAILED, target, "rank.buy.transaction-failed");
            }
        }

        Long dur = offer.hasCustomDuration() ? offer.getDurationMillis() : target.getDefaultDuration();
        boolean assigned = RankManager.setPlayerRank(uuid, target.getName(), dur);
        if (!assigned) {
            if (price > 0.0) {
                EconomyBridge.deposit(uuid, price);
            }
            return PurchaseResult.failure(PurchaseResult.Status.ASSIGNMENT_FAILED, target, "rank.buy.assignment-failed");
        }

        double balAfter = EconomyBridge.getBalance(uuid);
        return PurchaseResult.success(target, prev, offer, price, balAfter, check.isExtendedDuration());
    }
}
