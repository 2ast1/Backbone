package de.basti.backbone.commands.player;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Befehl /feed [player]
 * Permissions: backbone.feed (für sich selbst) und backbone.feed.others (für andere)
 */
public class FeedCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        Player target;

        if (args.length == 0) {
            if (!sender.hasPermission("backbone.feed")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.feed"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                return true;
            }
            target = player;
        } else {
            boolean isSelf = (sender instanceof Player p) && p.getName().equalsIgnoreCase(args[0]);
            if (isSelf) {
                if (!sender.hasPermission("backbone.feed")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.feed"));
                    return true;
                }
                target = (Player) sender;
            } else {
                if (!sender.hasPermission("backbone.feed.others") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.feed.others"));
                    return true;
                }
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    target = Bukkit.getPlayer(args[0]);
                }
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
                    return true;
                }
            }
        }

        target.setFoodLevel(20);
        target.setSaturation(20.0f);

        if (target.equals(sender)) {
            target.sendMessage(Lang.getPrefix() + Lang.get("feed.success-self"));
        } else {
            target.sendMessage(Lang.getPrefix() + Lang.get("feed.received", "sender", sender.getName()));
            sender.sendMessage(Lang.getPrefix() + Lang.get("feed.success-other", "player", target.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.feed") && !sender.hasPermission("backbone.feed.others")) {
            return Collections.emptyList();
        }

        if (args.length == 1 && (sender.hasPermission("backbone.feed.others") || sender.isOp())) {
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
