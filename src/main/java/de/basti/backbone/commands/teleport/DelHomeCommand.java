package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Command to delete homes (/delhome [name]).
 * Permission: backbone.delhome
 */
public class DelHomeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.delhome")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.delhome"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        Set<String> homes = HomeManager.getHomes(player.getUniqueId());
        String targetHome;

        if (args.length >= 1) {
            targetHome = args[0].trim();
        } else {
            if (homes.contains("default")) {
                targetHome = "default";
            } else if (homes.size() == 1) {
                targetHome = homes.iterator().next();
            } else {
                player.sendMessage(Lang.getPrefix() + Lang.get("home.del.usage", "label", label));
                return true;
            }
        }

        if (!HomeManager.homeExists(player.getUniqueId(), targetHome)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.not-found", "home", targetHome));
            return true;
        }

        HomeManager.deleteHome(player.getUniqueId(), targetHome);
        player.sendMessage(Lang.getPrefix() + Lang.get("home.del.success", "home", targetHome));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.delhome")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender instanceof Player player) {
            String current = args[0].toLowerCase();
            for (String h : HomeManager.getHomes(player.getUniqueId())) {
                if (h.toLowerCase().startsWith(current)) {
                    completions.add(h);
                }
            }
        }
        return completions;
    }
}
