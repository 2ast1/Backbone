package de.basti.backbone.ranks;

import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;

import java.util.*;

/**
 * Repräsentiert ein Rang-Objekt mit Namen (fungiert als ID), Präfix, Einzelfarbe,
 * Standarddauer (permanent oder zeitbasiert), Vorgänger-/Nachfolger-Verkettung (Chain),
 * Timestamps, globalen Berechtigungen, welt-spezifischen Berechtigungen und Kaufangeboten.
 */
public final class Rank {

    private final String name;
    private final String prefix;
    private final String color;
    private final long defaultDuration; // -1 = permanent, >0 = Dauer in Millisekunden
    private final String previousRank;  // Name des Vorgänger-Rangs (Chain / Demote), null falls keiner
    private final String nextRank;      // Name des Nachfolger-Rangs (Chain / Promote), null falls keiner
    private final long created;
    private final long lastChange;
    private final List<String> permissions;
    private final Map<String, List<String>> worldPermissions;
    private final List<RankOffer> offers;

    public Rank(String name, String prefix, String color, long defaultDuration,
                String previousRank, String nextRank,
                long created, long lastChange,
                List<String> permissions, Map<String, List<String>> worldPermissions,
                List<RankOffer> offers) {
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.prefix = prefix != null ? prefix : "";
        this.color = color != null && !color.isBlank() ? color : "§7";
        this.defaultDuration = (defaultDuration <= 0 && defaultDuration != -1) ? -1 : defaultDuration;
        this.previousRank = (previousRank != null && !previousRank.isBlank()) ? previousRank.trim() : null;
        this.nextRank = (nextRank != null && !nextRank.isBlank()) ? nextRank.trim() : null;
        this.created = created > 0 ? created : System.currentTimeMillis();
        this.lastChange = lastChange > 0 ? lastChange : this.created;
        this.permissions = permissions != null ? Collections.unmodifiableList(new ArrayList<>(permissions)) : Collections.emptyList();

        if (worldPermissions != null && !worldPermissions.isEmpty()) {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : worldPermissions.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey().toLowerCase(Locale.ROOT), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
                }
            }
            this.worldPermissions = Collections.unmodifiableMap(copy);
        } else {
            this.worldPermissions = Collections.emptyMap();
        }

        this.offers = offers != null ? Collections.unmodifiableList(new ArrayList<>(offers)) : Collections.emptyList();
    }

    public Rank(String name, String prefix, String color, long created, long lastChange, List<String> permissions) {
        this(name, prefix, color, -1, null, null, created, lastChange, permissions, Collections.emptyMap(), Collections.emptyList());
    }

    public String getName() {
        return name;
    }

    /**
     * Gibt den Namen zurück (als Rückwärtskompatibilität, da Name nun die ID ist).
     */
    public String getId() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getColor() {
        return color;
    }

    public long getDefaultDuration() {
        return defaultDuration;
    }

    public boolean isDefaultPermanent() {
        return defaultDuration == -1;
    }

    public String getFormattedDefaultDuration() {
        if (isDefaultPermanent()) {
            return Lang.get("common.permanent");
        }
        return DurationUtil.formatDuration(defaultDuration);
    }

    public String getPreviousRank() {
        return previousRank;
    }

    public boolean hasPreviousRank() {
        return previousRank != null && !previousRank.isBlank();
    }

    public String getNextRank() {
        return nextRank;
    }

    public boolean hasNextRank() {
        return nextRank != null && !nextRank.isBlank();
    }

    public long getCreated() {
        return created;
    }

    public long getLastChange() {
        return lastChange;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public Map<String, List<String>> getWorldPermissions() {
        return worldPermissions;
    }

    public List<String> getPermissionsForWorld(String world) {
        if (world == null || world.isBlank()) return Collections.emptyList();
        List<String> perms = worldPermissions.get(world.trim().toLowerCase(Locale.ROOT));
        return perms != null ? perms : Collections.emptyList();
    }

    public List<RankOffer> getOffers() {
        return offers;
    }

    public boolean isPurchasable() {
        return !offers.isEmpty();
    }

    /**
     * Rückwärtskompatibilität: Gibt den Preis des ersten Angebots zurück oder -1.0 falls nicht kaufbar.
     */
    public double getPrice() {
        return isPurchasable() ? offers.get(0).getPrice() : -1.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rank rank = (Rank) o;
        return name.equalsIgnoreCase(rank.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return "Rank{" +
                "name='" + name + '\'' +
                ", color='" + color + '\'' +
                ", duration=" + defaultDuration +
                ", prev=" + previousRank +
                ", next=" + nextRank +
                ", perms=" + permissions.size() +
                ", offers=" + offers.size() +
                '}';
    }
}
