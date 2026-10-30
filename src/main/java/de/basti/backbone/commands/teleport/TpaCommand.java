package de.basti.backbone.commands.teleport;

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

/**
 * Command for teleport requests (/tpa &lt;player&gt;, /tpa accept, /tpa deny).
 * Permission: backbone.tpa
 */
public class TpaCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.tpa")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.tpa"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Lang.getPrefix() + Lang.get("tpa.usage", "label", label));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("accept") || sub.equals("yes") || sub.equals("ja") || sub.equals("annehmen")) {
            TpaManager.acceptRequest(player);
            return true;
        }

        if (sub.equals("deny") || sub.equals("no") || sub.equals("nein") || sub.equals("ablehnen")) {
            TpaManager.denyRequest(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline() || !de.basti.backbone.util.HideManager.canSee(player, target)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
            return true;
        }

        TpaManager.sendRequest(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.tpa")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if ("accept".startsWith(current)) completions.add("accept");
            if ("deny".startsWith(current)) completions.add("deny");

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && de.basti.backbone.util.HideManager.canSee(sender, p) && p.getName().toLowerCase().startsWith(current)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
