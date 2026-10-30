package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Command to set homes (/sethome [name]).
 * Permission: backbone.sethome
 */
public class SetHomeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.sethome")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.sethome"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        String homeName = (args.length >= 1) ? args[0].trim() : "default";

        // 1. World blacklist check
        if (HomeManager.isWorldBlacklisted(player.getWorld().getName())) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.set.world-blacklisted", "world", player.getWorld().getName()));
            return true;
        }

        // 2. Reserved subcommand name & invalid character check
        if (homeName.equalsIgnoreCase("blacklistworld")
                || homeName.equalsIgnoreCase("list")
                || homeName.equalsIgnoreCase("help")
                || homeName.contains(".")
                || homeName.contains("/")
                || homeName.contains("\\")) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.set.reserved-name", "home", homeName));
            return true;
        }

        // 3. Max homes limit check (only when creating a new home)
        int maxHomes = HomeManager.getMaxHomes();
        if (maxHomes > 0
                && !HomeManager.homeExists(player.getUniqueId(), homeName)
                && !player.isOp()
                && !player.hasPermission("backbone.home.unlimited")
                && !player.hasPermission("backbone.admin")) {
            int currentCount = HomeManager.getHomes(player.getUniqueId()).size();
            if (currentCount >= maxHomes) {
                player.sendMessage(Lang.getPrefix() + Lang.get("home.set.limit-reached", "limit", String.valueOf(maxHomes)));
                return true;
            }
        }

        HomeManager.setHome(player.getUniqueId(), homeName, player.getLocation());
        player.sendMessage(Lang.getPrefix() + Lang.get("home.set.success", "home", homeName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return List.of();
    }
}
