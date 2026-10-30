package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.GenericGameEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet den kompletten Unsichtbarkeits-Modus (/hide).
 * Spieler im Hide-Modus werden auf Paket-Ebene (Entity + Tablist + Kollision + Mobs + Sculk-Sensoren)
 * für alle Spieler ohne die Berechtigung 'backbone.hide.see' vollständig ausgeblendet.
 */
public class HideManager implements Listener {

    public static final String PERM_HIDE = "backbone.hide";
    public static final String PERM_SEE = "backbone.hide.see";

    private static final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private static Backbone plugin;

    public static void init(Backbone backboneInstance) {
        plugin = backboneInstance;
        hiddenPlayers.clear();

        if (Bukkit.getServer() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (DataManager.getBoolean("", "playerdata.yml", online.getUniqueId() + ".hidden")) {
                    hiddenPlayers.add(online.getUniqueId());
                }
            }
            updateAllVisibilities();
        }
    }

    public static void shutdown() {
        if (plugin != null && Bukkit.getServer() != null) {
            for (Player hidden : Bukkit.getOnlinePlayers()) {
                if (hiddenPlayers.contains(hidden.getUniqueId())) {
                    hidden.setSleepingIgnored(false);
                    hidden.setCollidable(true);
                    hidden.setSilent(false);
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (!viewer.equals(hidden)) {
                            viewer.showPlayer(plugin, hidden);
                        }
                    }
                }
            }
        }
        hiddenPlayers.clear();
    }

    public static boolean isHidden(UUID uuid) {
        if (uuid == null) return false;
        if (hiddenPlayers.contains(uuid)) return true;
        return DataManager.getBoolean("", "playerdata.yml", uuid + ".hidden");
    }

    public static boolean isHidden(Player player) {
        return player != null && isHidden(player.getUniqueId());
    }

    public static boolean canSee(CommandSender viewer, Player target) {
        if (target == null) return false;
        if (!isHidden(target)) return true;
        if (viewer == null) return false;
        if (viewer.equals(target)) return true;
        return viewer.hasPermission(PERM_SEE);
    }

    public static boolean toggleHide(Player player) {
        boolean newState = !isHidden(player);
        setHidden(player, newState);
        return newState;
    }

    public static void setHidden(Player player, boolean hidden) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (hidden) {
            hiddenPlayers.add(uuid);
        } else {
            hiddenPlayers.remove(uuid);
        }
        DataManager.set("", "playerdata.yml", uuid + ".hidden", hidden);
        applyPlayerState(player, hidden);
        updateVisibilityForTarget(player);
    }

    private static void applyPlayerState(Player player, boolean hidden) {
        if (player == null || !player.isOnline()) return;
        try {
            player.setSleepingIgnored(hidden);
            player.setCollidable(!hidden);
            player.setSilent(hidden);
            if (hidden) {
                // Falls Mobs den Spieler bereits im Visier hatten, Ziel sofort löschen
                for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(32, 32, 32)) {
                    if (nearby instanceof org.bukkit.entity.Mob mob && player.equals(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Aktualisiert die Sichtbarkeit eines bestimmten Spielers für alle aktuell online befindlichen Spieler.
     */
    public static void updateVisibilityForTarget(Player target) {
        if (target == null || !target.isOnline()) return;
        Backbone inst = plugin != null ? plugin : Backbone.getInstance();
        if (inst == null || Bukkit.getServer() == null) return;

        boolean hidden = isHidden(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (hidden && !viewer.hasPermission(PERM_SEE)) {
                viewer.hidePlayer(inst, target);
            } else {
                viewer.showPlayer(inst, target);
            }
        }
    }

    /**
     * Aktualisiert alle Sichtbarkeiten für einen bestimmten Betrachter (z.B. beim Joinen oder Rangwechsel).
     */
    public static void updateVisibilityForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        Backbone inst = plugin != null ? plugin : Backbone.getInstance();
        if (inst == null || Bukkit.getServer() == null) return;

        boolean viewerCanSeeHidden = viewer.hasPermission(PERM_SEE);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (isHidden(target) && !viewerCanSeeHidden) {
                viewer.hidePlayer(inst, target);
            } else {
                viewer.showPlayer(inst, target);
            }
        }
    }

    /**
     * Synchronisiert die Sichtbarkeit aller Online-Spieler untereinander.
     */
    public static void updateAllVisibilities() {
        if (Bukkit.getServer() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isHidden(p.getUniqueId())) {
                hiddenPlayers.add(p.getUniqueId());
                applyPlayerState(p, true);
            } else {
                hiddenPlayers.remove(p.getUniqueId());
                applyPlayerState(p, false);
            }
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateVisibilityForViewer(viewer);
        }
    }

    /**
     * Gibt die Anzahl der für den angegebenen Spieler sichtbaren Online-Spieler zurück.
     */
    public static int getVisibleOnlineCount(Player viewer) {
        if (Bukkit.getServer() == null) return 0;
        if (viewer != null && viewer.hasPermission(PERM_SEE)) {
            return Bukkit.getOnlinePlayers().size();
        }
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isHidden(p) || (viewer != null && viewer.equals(p))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gibt die Anzahl der öffentlich sichtbaren Online-Spieler (ohne versteckte Spieler) zurück.
     */
    public static int getPublicOnlineCount() {
        if (Bukkit.getServer() == null) return 0;
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isHidden(p)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gibt die Anzahl der für den Betrachter sichtbaren Spieler in einer Welt zurück.
     */
    public static int getVisibleWorldPlayerCount(World world, Player viewer) {
        if (world == null) return 0;
        if (viewer != null && viewer.hasPermission(PERM_SEE)) {
            return world.getPlayers().size();
        }
        int count = 0;
        for (Player p : world.getPlayers()) {
            if (!isHidden(p) || (viewer != null && viewer.equals(p))) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // EVENT LISTENERS (Verhindert Verraten durch Mobs oder Sculk-Sensoren)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && isHidden(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericGameEvent(GenericGameEvent event) {
        if (event.getEntity() instanceof Player player && isHidden(player)) {
            event.setCancelled(true);
        }
    }
}
