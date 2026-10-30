package de.basti.backbone.commands.gamemode;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to switch directly to Spectator mode (/spectator).
 * Permission: backbone.gamemode or backbone.spectator
 */
public class SpectatorCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.gamemode") && !sender.hasPermission("backbone.spectator")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.gamemode"));
            return true;
        }

        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("backbone.gamemode.others")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.gamemode.others"));
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.usage-direct-console", "label", label));
                return true;
            }
            target = player;
        }

        GameModeCommand.applyGameMode(sender, target, GameMode.SPECTATOR);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.gamemode") && !sender.hasPermission("backbone.spectator")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (de.basti.backbone.util.HideManager.canSee(sender, player) && player.getName().toLowerCase().startsWith(current)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
