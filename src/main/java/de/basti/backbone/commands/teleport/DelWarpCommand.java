package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.WarpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to delete warps (/delwarp &lt;name&gt;).
 * Permission: backbone.delwarp
 */
public class DelWarpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.delwarp")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.delwarp"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("warp.del.usage", "label", label));
            return true;
        }

        String warpName = args[0].trim();
        if (!WarpManager.warpExists(warpName)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("warp.not-found", "warp", warpName));
            return true;
        }

        WarpManager.deleteWarp(warpName);
        sender.sendMessage(Lang.getPrefix() + Lang.get("warp.del.success", "warp", warpName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.delwarp")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (String w : WarpManager.getWarps()) {
                if (w.toLowerCase().startsWith(current)) {
                    completions.add(w);
                }
            }
        }
        return completions;
    }
}
