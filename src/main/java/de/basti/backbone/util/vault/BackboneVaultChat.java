package de.basti.backbone.util.vault;

import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Vault Chat-Provider für Backbone.
 * Verbindet das Präfix- und Rang-System ({@link PrefixManager} und {@link RankManager}) mit der Vault-Chat-API,
 * sodass externe Plugins (z.B. PlaceholderAPI, TAB, Nametag-Plugins) Spieler- und Rang-Präfixe auslesen können.
 */
public class BackboneVaultChat extends Chat {

    public BackboneVaultChat(Permission perms) {
        super(perms);
    }

    @Override
    public String getName() {
        return "Backbone";
    }

    @Override
    public boolean isEnabled() {
        return PrefixManager.isPrefixEnabled() || RankManager.isRanksEnabled();
    }

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

    // =========================================================================
    // SPIELER-PRÄFIX & SUFFIX
    // =========================================================================

    private String resolvePlayerPrefix(UUID uuid) {
        if (uuid == null) return "";
        if (PrefixManager.isPrefixEnabled()) {
            return PrefixManager.getPrefix(uuid, "");
        }
        if (RankManager.isRanksEnabled() && RankManager.isUseRankPrefixEnabled()) {
            Rank rank = RankManager.getRankOfPlayer(uuid);
            if (rank != null && rank.getPrefix() != null && !rank.getPrefix().isBlank()) {
                return PrefixManager.formatColors(rank.getPrefix());
            }
        }
        return "";
    }

    @Override
    public String getPlayerPrefix(String world, OfflinePlayer player) {
        return resolvePlayerPrefix(resolve(player));
    }

    @Override
    public String getPlayerPrefix(String world, String player) {
        return resolvePlayerPrefix(resolve(player));
    }

    @Override
    public void setPlayerPrefix(String world, OfflinePlayer player, String prefix) {
        UUID uuid = resolve(player);
        if (uuid == null) return;
        if (prefix == null || prefix.isBlank()) {
            PrefixManager.removePrefix(uuid);
        } else {
            PrefixManager.setPrefix(uuid, prefix);
        }
    }

    @Override
    public void setPlayerPrefix(String world, String player, String prefix) {
        UUID uuid = resolve(player);
        if (uuid == null) return;
        if (prefix == null || prefix.isBlank()) {
            PrefixManager.removePrefix(uuid);
        } else {
            PrefixManager.setPrefix(uuid, prefix);
        }
    }

    @Override
    public String getPlayerSuffix(String world, OfflinePlayer player) {
        return "";
    }

    @Override
    public String getPlayerSuffix(String world, String player) {
        return "";
    }

    @Override
    public void setPlayerSuffix(String world, OfflinePlayer player, String suffix) {
        // Suffixe werden in Backbone nicht separat verwaltet
    }

    @Override
    public void setPlayerSuffix(String world, String player, String suffix) {
        // Suffixe werden in Backbone nicht separat verwaltet
    }

    // =========================================================================
    // GRUPPEN- / RANG-PRÄFIX & SUFFIX
    // =========================================================================

    @Override
    public String getGroupPrefix(String world, String group) {
        if (group == null || group.isBlank()) return "";
        Rank rank = RankManager.getRank(group);
        if (rank == null) {
            for (Rank r : RankManager.getAllRanks()) {
                if (r.getName().equalsIgnoreCase(group.trim())) {
                    rank = r;
                    break;
                }
            }
        }
        if (rank == null || rank.getPrefix() == null) return "";
        return PrefixManager.formatColors(rank.getPrefix());
    }

    @Override
    public void setGroupPrefix(String world, String group, String prefix) {
        if (group == null || group.isBlank()) return;
        RankManager.setRankPrefix(group, prefix != null ? prefix : "");
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        return "";
    }

    @Override
    public void setGroupSuffix(String world, String group, String suffix) {
        // Suffixe werden in Backbone nicht separat verwaltet
    }

    // =========================================================================
    // INFO-NODES (METADATEN-FALLBACKS)
    // =========================================================================

    @Override
    public int getPlayerInfoInteger(String world, String player, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoInteger(String world, String player, String node, int value) {
    }

    @Override
    public int getGroupInfoInteger(String world, String group, String node, int defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoInteger(String world, String group, String node, int value) {
    }

    @Override
    public double getPlayerInfoDouble(String world, String player, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoDouble(String world, String player, String node, double value) {
    }

    @Override
    public double getGroupInfoDouble(String world, String group, String node, double defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoDouble(String world, String group, String node, double value) {
    }

    @Override
    public boolean getPlayerInfoBoolean(String world, String player, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setPlayerInfoBoolean(String world, String player, String node, boolean value) {
    }

    @Override
    public boolean getGroupInfoBoolean(String world, String group, String node, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public void setGroupInfoBoolean(String world, String group, String node, boolean value) {
    }

    @Override
    public String getPlayerInfoString(String world, String player, String node, String defaultValue) {
        if ("prefix".equalsIgnoreCase(node)) {
            String pref = getPlayerPrefix(world, player);
            return !pref.isEmpty() ? pref : defaultValue;
        }
        return defaultValue;
    }

    @Override
    public void setPlayerInfoString(String world, String player, String node, String value) {
        if ("prefix".equalsIgnoreCase(node)) {
            setPlayerPrefix(world, player, value);
        }
    }

    @Override
    public String getGroupInfoString(String world, String group, String node, String defaultValue) {
        if ("prefix".equalsIgnoreCase(node)) {
            String pref = getGroupPrefix(world, group);
            return !pref.isEmpty() ? pref : defaultValue;
        }
        return defaultValue;
    }

    @Override
    public void setGroupInfoString(String world, String group, String node, String value) {
        if ("prefix".equalsIgnoreCase(node)) {
            setGroupPrefix(world, group, value);
        }
    }
}
