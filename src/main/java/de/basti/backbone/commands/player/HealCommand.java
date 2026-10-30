package de.basti.backbone.commands.player;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
 * Befehl /heal [player]
 * Permissions: backbone.heal (für sich selbst) und backbone.heal.others (für andere)
 */
public class HealCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        Player target;

        if (args.length == 0) {
            if (!sender.hasPermission("backbone.heal")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.heal"));
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
                if (!sender.hasPermission("backbone.heal")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.heal"));
                    return true;
                }
                target = (Player) sender;
            } else {
                if (!sender.hasPermission("backbone.heal.others") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.heal.others"));
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

        double maxHealth = 20.0;
        try {
            AttributeInstance attr = target.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                maxHealth = attr.getValue();
            }
        } catch (Throwable ignored) {}

        target.setHealth(maxHealth);
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setFireTicks(0);

        if (target.equals(sender)) {
            target.sendMessage(Lang.getPrefix() + Lang.get("heal.success-self"));
        } else {
            target.sendMessage(Lang.getPrefix() + Lang.get("heal.received", "sender", sender.getName()));
            sender.sendMessage(Lang.getPrefix() + Lang.get("heal.success-other", "player", target.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.heal") && !sender.hasPermission("backbone.heal.others")) {
            return Collections.emptyList();
        }

        if (args.length == 1 && (sender.hasPermission("backbone.heal.others") || sender.isOp())) {
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
