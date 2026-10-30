package de.basti.backbone.ranks;

/**
 * Result-Objekt für Rang-Kaufversuche.
 */
public final class PurchaseResult {

    public enum Status {
        SUCCESS,
        SHOP_DISABLED,
        ECONOMY_UNAVAILABLE,
        RANK_NOT_FOUND,
        NOT_PURCHASABLE,
        INVALID_OFFER,
        ALREADY_OWNS_RANK,
        ALREADY_OWNS_PERMANENT,
        CANNOT_DOWNGRADE,
        NOT_AN_UPGRADE,
        INSUFFICIENT_FUNDS,
        TRANSACTION_FAILED,
        ASSIGNMENT_FAILED
    }

    private final Status status;
    private final Rank targetRank;
    private final Rank previousRank;
    private final RankOffer offer;
    private final double pricePaid;
    private final double balanceAfter;
    private final boolean extendedDuration;
    private final String langKey;

    public PurchaseResult(Status status, Rank targetRank, Rank previousRank, RankOffer offer,
                          double pricePaid, double balanceAfter, boolean extendedDuration, String langKey) {
        this.status = status;
        this.targetRank = targetRank;
        this.previousRank = previousRank;
        this.offer = offer;
        this.pricePaid = pricePaid;
        this.balanceAfter = balanceAfter;
        this.extendedDuration = extendedDuration;
        this.langKey = langKey;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public Rank getTargetRank() {
        return targetRank;
    }

    public Rank getPreviousRank() {
        return previousRank;
    }

    public RankOffer getOffer() {
        return offer;
    }

    public double getPricePaid() {
        return pricePaid;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public boolean isExtendedDuration() {
        return extendedDuration;
    }

    public String getLangKey() {
        return langKey;
    }

    public static PurchaseResult success(Rank target, Rank prev, RankOffer offer, double price, double balAfter, boolean extended) {
        return new PurchaseResult(Status.SUCCESS, target, prev, offer, price, balAfter, extended,
                extended ? "rank.buy.extended-success" : "rank.buy.success");
    }

    public static PurchaseResult failure(Status status, Rank target, String langKey) {
        return new PurchaseResult(status, target, null, null, 0.0, 0.0, false, langKey);
    }
}
