package de.basti.backbone.commands.player;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Befehl /skull [player]
 * Permissions: backbone.skull (für eigenen Kopf) und backbone.skull.others (für Köpfe anderer)
 */
public class SkullCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        String targetOwner;

        if (args.length == 0) {
            if (!player.hasPermission("backbone.skull")) {
                player.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.skull"));
                return true;
            }
            targetOwner = player.getName();
        } else {
            boolean isSelf = args[0].equalsIgnoreCase(player.getName());
            if (isSelf) {
                if (!player.hasPermission("backbone.skull")) {
                    player.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.skull"));
                    return true;
                }
                targetOwner = player.getName();
            } else {
                if (!player.hasPermission("backbone.skull.others") && !player.hasPermission("backbone.admin") && !player.isOp()) {
                    player.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.skull.others"));
                    return true;
                }
                targetOwner = args[0];
            }
        }

        Player onlineOwner = Bukkit.getPlayerExact(targetOwner);
        if (onlineOwner != null) {
            giveSkull(player, onlineOwner, targetOwner);
            return true;
        }

        // Asynchron auflösen, um den Server-Hauptthread nicht mit Mojang-HTTP-Requests zu blockieren
        Bukkit.getScheduler().runTaskAsynchronously(de.basti.backbone.Backbone.getInstance(), () -> {
            OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(targetOwner);
            Bukkit.getScheduler().runTask(de.basti.backbone.Backbone.getInstance(), () -> {
                if (player.isOnline()) {
                    giveSkull(player, offlineOwner, targetOwner);
                }
            });
        });
        return true;
    }

    private void giveSkull(Player player, OfflinePlayer owner, String targetOwner) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(owner);
            meta.setDisplayName(Lang.get("skull.item-name", "player", targetOwner));
            skull.setItemMeta(meta);
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(skull);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        player.sendMessage(Lang.getPrefix() + Lang.get("skull.success", "player", targetOwner));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.skull") && !sender.hasPermission("backbone.skull.others")) {
            return Collections.emptyList();
        }

        if (args.length == 1 && (sender.hasPermission("backbone.skull.others") || sender.isOp())) {
            String current = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!HideManager.canSee(sender, player)) continue;
                if (player.getName().toLowerCase().startsWith(current)) {
                    list.add(player.getName());
                }
            }
            return list;
        }

        return Collections.emptyList();
    }
}
