package de.basti.backbone.ranks;

import de.basti.backbone.Backbone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Bukkit PermissionAttachments für Spieler synchron auf dem Hauptthread.
 * Garantiert saubere Deregistrierung beim Verlassen des Servers (verhindert Memory Leaks).
 */
public class PermissionService {

    private final Backbone plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public static final List<String> KNOWN_BACKBONE_PERMISSIONS = List.of(
            "backbone.admin",
            "backbone.setup",
            "backbone.reload",
            "backbone.compact",
            "backbone.ram",
            "backbone.health",
            "backbone.ban",
            "backbone.unban",
            "backbone.mute",
            "backbone.unmute",
            "backbone.automod.bypass",
            "backbone.maintenance",
            "backbone.prefix",
            "backbone.prefix.others",
            "backbone.prefix.admin",
            "backbone.setprefix",
            "backbone.removeprefix",
            "backbone.namecolor",
            "backbone.namecolor.others",
            "backbone.setnamecolor",
            "backbone.removenamecolor",
            "backbone.rank",
            "backbone.rank.list",
            "backbone.rank.info",
            "backbone.rank.set",
            "backbone.rank.admin",
            "backbone.rank.buy",
            "backbone.inventory",
            "backbone.inventory.advanced",
            "backbone.enderchest",
            "backbone.fly",
            "backbone.fly.other",
            "backbone.hide",
            "backbone.hide.other",
            "backbone.hide.see",
            "backbone.gamemode",
            "backbone.gamemode.others",
            "backbone.survival",
            "backbone.creative",
            "backbone.adventure",
            "backbone.spectator",
            "backbone.time",
            "backbone.day",
            "backbone.night",
            "backbone.sunrise",
            "backbone.sunset",
            "backbone.weather",
            "backbone.sun",
            "backbone.rain",
            "backbone.storm",
            "backbone.tpa",
            "backbone.lobby",
            "backbone.setlobby",
            "backbone.warp",
            "backbone.setwarp",
            "backbone.delwarp",
            "backbone.warp.blacklist",
            "backbone.home",
            "backbone.sethome",
            "backbone.delhome",
            "backbone.home.blacklist",
            "backbone.home.unlimited",
            "backbone.spawn",
            "backbone.setworldspawn",
            "backbone.money",
            "backbone.money.others",
            "backbone.money.give",
            "backbone.money.take",
            "backbone.money.set",
            "backbone.pay",
            "backbone.scoreboard",
            "backbone.scoreboard.admin",
            "backbone.chat.color",
            "backbone.msg",
            "backbone.reply"
    );

    public PermissionService(Backbone plugin) {
        this.plugin = plugin;
    }

    /**
     * Berechnet die effektiven Berechtigungen für einen Spieler in einer bestimmten Welt.
     * Schichten-Reihenfolge (spätere überschreiben frühere):
     * 1. Rang global
     * 2. Rang weltspezifisch
     * 3. Spieler global individuell
     * 4. Spieler weltspezifisch individuell
     */
    public static Map<String, Boolean> computeEffective(UUID uuid, String world) {
        if (uuid == null) {
            return Collections.emptyMap();
        }

        Rank rank = RankManager.getRankOfPlayer(uuid);
        List<java.util.Collection<String>> layers = new ArrayList<>(4);

        // Layer 1: Rang global
        layers.add(rank != null ? rank.getPermissions() : Collections.emptyList());

        // Layer 2: Rang weltspezifisch
        layers.add((rank != null && world != null) ? rank.getPermissionsForWorld(world) : Collections.emptyList());

        // Layer 3: Spieler global individuell
        layers.add(PlayerPermissionManager.getGlobalPermissions(uuid));

        // Layer 4: Spieler weltspezifisch individuell
        layers.add(world != null ? PlayerPermissionManager.getPermissionsForWorld(uuid, world) : Collections.emptyList());

        Set<String> knownPerms = new HashSet<>(KNOWN_BACKBONE_PERMISSIONS);
        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                for (Permission p : Bukkit.getPluginManager().getPermissions()) {
                    if (p != null && p.getName() != null) {
                        knownPerms.add(p.getName());
                    }
                }
            }
        } catch (Throwable ignored) {}

        return PermissionResolver.resolve(layers, knownPerms);
    }

    /**
     * Wendet die Rang- und individuellen Berechtigungen auf den Spieler an.
     * MUSS auf dem Hauptthread ausgeführt werden (synchronisiert bei Bedarf automatisch).
     *
     * @param player Der Zielspieler
     */
    public void applyPermissions(Player player) {
        if (player == null || !player.isOnline()) return;

        // Bukkit Attachment-Änderungen MÜSSEN synchron auf dem Hauptthread stattfinden
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> applyPermissions(player));
            return;
        }

        UUID uuid = player.getUniqueId();

        // 1. Altes Attachment immer zuerst sauber abmelden (Schutz vor Duplicate Perms & Leaks)
        removeAttachment(player);

        // Falls das Rangsystem in der config.yml deaktiviert ist, keine Berechtigungen anheften
        if (!RankManager.isRanksEnabled()) {
            return;
        }

        String world = player.getWorld() != null ? player.getWorld().getName() : null;
        Map<String, Boolean> effective = computeEffective(uuid, world);
        if (effective.isEmpty()) {
            return;
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (Map.Entry<String, Boolean> entry : effective.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                attachment.setPermission(entry.getKey(), entry.getValue());
            }
        }

        attachments.put(uuid, attachment);
        de.basti.backbone.util.HideManager.updateVisibilityForViewer(player);
    }

    /**
     * Entfernt das PermissionAttachment eines Spielers sauber aus der Bukkit-Verwaltung.
     */
    public void removeAttachment(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> removeAttachment(player));
            return;
        }
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // Ignorieren, falls Bukkit das Attachment intern bereits bereinigt hat
            }
        }
    }

    /**
     * Aktualisiert die Berechtigungen für einen Spieler anhand seiner UUID.
     */
    public void updatePlayer(UUID uuid) {
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyPermissions(player);
        }
    }

    /**
     * Aktualisiert die Berechtigungen für alle aktuell online befindlichen Spieler.
     */
    public void updateAll() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::updateAll);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissions(player);
        }
    }

    /**
     * Beendet alle Attachments sauber beim Herunterfahren des Plugins (verhindert Leaks beim Reload).
     */
    public void unregisterAll() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::unregisterAll);
            return;
        }
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                try {
                    p.removeAttachment(entry.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        attachments.clear();
    }


}
