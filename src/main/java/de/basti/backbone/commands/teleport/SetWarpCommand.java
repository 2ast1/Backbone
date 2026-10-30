package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.WarpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Command to create warps (/setwarp &lt;name&gt;).
 * Permission: backbone.setwarp
 */
public class SetWarpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.setwarp")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.setwarp"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.set.usage", "label", label));
            return true;
        }

        String warpName = args[0].trim();

        // 1. World blacklist check
        if (WarpManager.isWorldBlacklisted(player.getWorld().getName())) {
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.set.world-blacklisted", "world", player.getWorld().getName()));
            return true;
        }

        // 2. Reserved subcommand name & invalid character check
        if (warpName.equalsIgnoreCase("blacklistworld")
                || warpName.equalsIgnoreCase("list")
                || warpName.equalsIgnoreCase("help")
                || warpName.contains(".")
                || warpName.contains("/")
                || warpName.contains("\\")) {
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.set.reserved-name", "warp", warpName));
            return true;
        }

        WarpManager.setWarp(warpName, player.getLocation(), player.getName());
        player.sendMessage(Lang.getPrefix() + Lang.get("warp.set.success", "warp", warpName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return List.of();
    }
}
