package de.basti.backbone.ranks;

import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.economy.EconomyBridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Repräsentiert ein Kaufangebot für einen Rang (Preis + Dauer).
 * Ist duration null, greift die Standarddauer des Rangs.
 * Ist duration == -1, ist dieses spezifische Angebot permanent.
 * Ist duration > 0, ist es eine zeitlich begrenzte Dauer in Millisekunden.
 */
public final class RankOffer {

    private final double price;
    private final Long durationMillis; // null = Standarddauer des Rangs, -1 = permanent, >0 = Millisekunden

    public RankOffer(double price, Long durationMillis) {
        this.price = (Double.isFinite(price) && price >= 0.0) ? Math.round(price * 100.0) / 100.0 : 0.0;
        this.durationMillis = (durationMillis != null && durationMillis <= 0 && durationMillis != -1) ? null : durationMillis;
    }

    public double getPrice() {
        return price;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public boolean hasCustomDuration() {
        return durationMillis != null;
    }

    public boolean isPermanent(Rank rank) {
        if (durationMillis != null) {
            return durationMillis == -1;
        }
        return rank != null && rank.isDefaultPermanent();
    }

    public long getEffectiveDuration(Rank rank) {
        if (durationMillis != null) {
            return durationMillis;
        }
        return rank != null ? rank.getDefaultDuration() : -1;
    }

    public String getFormattedDuration(Rank rank) {
        if (isPermanent(rank)) {
            return Lang.get("common.permanent");
        }
        long eff = getEffectiveDuration(rank);
        return eff > 0 ? DurationUtil.formatDuration(eff) : Lang.get("common.permanent");
    }

    public String format(Rank rank) {
        return EconomyBridge.format(price) + " §8(§e" + getFormattedDuration(rank) + "§8)";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("price", price);
        if (durationMillis != null) {
            if (durationMillis == -1) {
                map.put("duration", "perm");
            } else {
                map.put("duration", durationMillis);
            }
        }
        return map;
    }

    public static RankOffer fromMap(Map<?, ?> map) {
        if (map == null) return null;

        double price = 0.0;
        Object rawPrice = map.get("price");
        if (rawPrice instanceof Number n) {
            price = n.doubleValue();
        } else if (rawPrice instanceof String s) {
            try {
                price = Double.parseDouble(s.trim().replace(",", "."));
            } catch (NumberFormatException ignored) {}
        }

        Long duration = null;
        Object rawDur = map.get("duration");
        if (rawDur instanceof Number n) {
            long val = n.longValue();
            duration = (val <= 0 && val != -1) ? null : val;
        } else if (rawDur instanceof String s) {
            String clean = s.trim();
            if (DurationUtil.isPermanent(clean)) {
                duration = -1L;
            } else if (DurationUtil.isDurationSyntax(clean)) {
                long parsed = DurationUtil.parseDuration(clean);
                duration = parsed > 0 ? parsed : null;
            }
        }

        return new RankOffer(price, duration);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RankOffer rankOffer = (RankOffer) o;
        return Double.compare(rankOffer.price, price) == 0 && Objects.equals(durationMillis, rankOffer.durationMillis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, durationMillis);
    }

    @Override
    public String toString() {
        return "RankOffer{" +
                "price=" + price +
                ", durationMillis=" + durationMillis +
                '}';
    }
}
