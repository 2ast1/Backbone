package de.basti.backbone.util.vault;

import de.basti.backbone.Backbone;
import de.basti.backbone.ranks.PermissionResolver;
import de.basti.backbone.ranks.PermissionService;
import de.basti.backbone.ranks.PlayerPermissionManager;
import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.PlayerResolver;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Vault Permission-Provider für Backbone.
 * Verbindet das interne Rang- und Berechtigungssystem ({@link RankManager}) mit der Vault-API,
 * sodass andere Plugins Ränge (Gruppen) und Berechtigungen abfragen oder zuweisen können.
 */
public class BackboneVaultPermission extends Permission {

    public BackboneVaultPermission(Backbone plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Backbone";
    }

    @Override
    public boolean isEnabled() {
        return RankManager.isRanksEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
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
    // SPIELER-BERECHTIGUNGEN
    // =========================================================================

    @Override
    public boolean playerHas(String world, OfflinePlayer player, String permission) {
        if (player == null || permission == null) return false;
        UUID uuid = resolve(player);
        if (uuid == null) return false;

        if (world == null && player.isOnline() && player instanceof Player onlinePlayer) {
            return onlinePlayer.hasPermission(permission);
        }

        Map<String, Boolean> effective = PermissionService.computeEffective(uuid, world);
        return PermissionResolver.hasPermission(effective, permission);
    }

    @Override
    public boolean playerHas(String world, String player, String permission) {
        UUID uuid = resolve(player);
        if (uuid == null || permission == null) return false;

        if (world == null) {
            Player online = Bukkit.getServer() != null ? Bukkit.getPlayer(uuid) : null;
            if (online != null && online.isOnline()) {
                return online.hasPermission(permission);
            }
        }

        Map<String, Boolean> effective = PermissionService.computeEffective(uuid, world);
        return PermissionResolver.hasPermission(effective, permission);
    }

    @Override
    public boolean playerAdd(String world, OfflinePlayer player, String permission) {
        UUID uuid = resolve(player);
        if (uuid == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return PlayerPermissionManager.addPermission(uuid, permission, worlds);
    }

    @Override
    public boolean playerAdd(String world, String player, String permission) {
        UUID uuid = resolve(player);
        if (uuid == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return PlayerPermissionManager.addPermission(uuid, permission, worlds);
    }

    @Override
    public boolean playerRemove(String world, OfflinePlayer player, String permission) {
        UUID uuid = resolve(player);
        if (uuid == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return PlayerPermissionManager.removePermission(uuid, permission, worlds);
    }

    @Override
    public boolean playerRemove(String world, String player, String permission) {
        UUID uuid = resolve(player);
        if (uuid == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return PlayerPermissionManager.removePermission(uuid, permission, worlds);
    }

    // =========================================================================
    // GRUPPEN- / RANG-BERECHTIGUNGEN
    // =========================================================================

    @Override
    public boolean groupHas(String world, String group, String permission) {
        if (group == null || permission == null) return false;
        Rank rank = RankManager.getRank(group);
        if (rank == null) return false;

        List<Collection<String>> layers = new ArrayList<>();
        layers.add(rank.getPermissions());
        if (world != null && !world.isBlank()) {
            layers.add(rank.getPermissionsForWorld(world));
        }
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, PermissionService.KNOWN_BACKBONE_PERMISSIONS);
        return PermissionResolver.hasPermission(effective, permission);
    }

    @Override
    public boolean groupAdd(String world, String group, String permission) {
        if (group == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return RankManager.addPermission(group, permission, worlds);
    }

    @Override
    public boolean groupRemove(String world, String group, String permission) {
        if (group == null || permission == null) return false;
        Collection<String> worlds = (world != null && !world.isBlank()) ? List.of(world.trim()) : null;
        return RankManager.removePermission(group, permission, worlds);
    }

    // =========================================================================
    // SPIELER-GRUPPEN (RANG-ZUORDNUNG)
    // =========================================================================

    private boolean isPlayerInRank(UUID uuid, String group) {
        if (uuid == null || group == null || group.isBlank()) return false;
        Rank rank = RankManager.getRankOfPlayer(uuid);
        if (rank == null) return false;
        return rank.getId().equalsIgnoreCase(group.trim()) || rank.getName().equalsIgnoreCase(group.trim());
    }

    @Override
    public boolean playerInGroup(String world, OfflinePlayer player, String group) {
        return isPlayerInRank(resolve(player), group);
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        return isPlayerInRank(resolve(player), group);
    }

    @Override
    public boolean playerAddGroup(String world, OfflinePlayer player, String group) {
        UUID uuid = resolve(player);
        if (uuid == null || group == null) return false;
        return RankManager.setPlayerRank(uuid, group);
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        UUID uuid = resolve(player);
        if (uuid == null || group == null) return false;
        return RankManager.setPlayerRank(uuid, group);
    }

    @Override
    public boolean playerRemoveGroup(String world, OfflinePlayer player, String group) {
        UUID uuid = resolve(player);
        if (!isPlayerInRank(uuid, group)) return false;
        RankManager.removePlayerRank(uuid);
        return true;
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        UUID uuid = resolve(player);
        if (!isPlayerInRank(uuid, group)) return false;
        RankManager.removePlayerRank(uuid);
        return true;
    }

    @Override
    public String[] getPlayerGroups(String world, OfflinePlayer player) {
        String primary = getPrimaryGroup(world, player);
        return (primary != null && !primary.isEmpty()) ? new String[]{primary} : new String[0];
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        String primary = getPrimaryGroup(world, player);
        return (primary != null && !primary.isEmpty()) ? new String[]{primary} : new String[0];
    }

    @Override
    public String getPrimaryGroup(String world, OfflinePlayer player) {
        UUID uuid = resolve(player);
        if (uuid == null) return "";
        Rank rank = RankManager.getRankOfPlayer(uuid);
        return rank != null ? rank.getId() : "";
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        UUID uuid = resolve(player);
        if (uuid == null) return "";
        Rank rank = RankManager.getRankOfPlayer(uuid);
        return rank != null ? rank.getId() : "";
    }

    @Override
    public String[] getGroups() {
        return RankManager.getAllRankIds().toArray(new String[0]);
    }
}
