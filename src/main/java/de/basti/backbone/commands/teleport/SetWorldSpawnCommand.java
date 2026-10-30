package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.SpawnManager;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to set the world spawn (/setworldspawn [true|false]).
 * Permission: backbone.setworldspawn
 */
public class SetWorldSpawnCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.setworldspawn")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.setworldspawn"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        World world = player.getWorld();
        Boolean spawnOnJoin = null;

        if (args.length >= 1) {
            String val = args[0].toLowerCase();
            if (val.equals("true") || val.equals("yes") || val.equals("ja") || val.equals("on") || val.equals("1")) {
                spawnOnJoin = true;
            } else if (val.equals("false") || val.equals("no") || val.equals("nein") || val.equals("off") || val.equals("0")) {
                spawnOnJoin = false;
            } else {
                player.sendMessage(Lang.getPrefix() + Lang.get("common.boolean-usage", "label", label));
                return true;
            }
        }

        SpawnManager.setWorldSpawn(player.getLocation(), spawnOnJoin);

        if (spawnOnJoin != null) {
            String state = spawnOnJoin ? Lang.get("common.enabled") : Lang.get("common.disabled");
            player.sendMessage(Lang.getPrefix() + Lang.get("spawn.set.success-with-toggle", "world", world.getName(), "state", state));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("spawn.set.success", "world", world.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.setworldspawn")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if ("true".startsWith(current)) completions.add("true");
            if ("false".startsWith(current)) completions.add("false");
        }
        return completions;
    }
}
