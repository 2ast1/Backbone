package de.basti.backbone.commands.gamemode;

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
import java.util.List;
import java.util.Locale;

/**
 * Command to toggle complete invisibility (/hide).
 * Permission: backbone.hide (See hidden players: backbone.hide.see)
 */
public class HideCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission(HideManager.PERM_HIDE)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", HideManager.PERM_HIDE));
            return true;
        }

        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("hide.usage-console", "label", label));
                return true;
            }
            target = player;
        } else {
            boolean isSelfArg = (sender instanceof Player p) && p.getName().equalsIgnoreCase(args[0]);
            if (!isSelfArg && !sender.hasPermission("backbone.hide.other") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.hide.other"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
                return true;
            }
        }

        boolean nowHidden = HideManager.toggleHide(target);
        String stateMsg = nowHidden ? Lang.get("common.enabled-lower") : Lang.get("common.disabled-lower");

        if (target.equals(sender)) {
            target.sendMessage(Lang.getPrefix() + Lang.get("hide.toggled-self", "state", stateMsg));
        } else {
            target.sendMessage(Lang.getPrefix() + Lang.get("hide.toggled-target", "sender", sender.getName(), "state", stateMsg));
            sender.sendMessage(Lang.getPrefix() + Lang.get("hide.toggled-sender", "player", target.getName(), "state", stateMsg));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission(HideManager.PERM_HIDE)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (HideManager.canSee(sender, player) && player.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
