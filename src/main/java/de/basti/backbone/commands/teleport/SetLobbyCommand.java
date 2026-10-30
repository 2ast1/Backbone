package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.LobbyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to set the lobby spawn point (/setlobby [true|false]).
 * Permission: backbone.setlobby
 */
public class SetLobbyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.setlobby")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.setlobby"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        Boolean teleportOnJoin = null;
        if (args.length >= 1) {
            String val = args[0].toLowerCase();
            if (val.equals("true") || val.equals("yes") || val.equals("ja") || val.equals("on") || val.equals("1")) {
                teleportOnJoin = true;
            } else if (val.equals("false") || val.equals("no") || val.equals("nein") || val.equals("off") || val.equals("0")) {
                teleportOnJoin = false;
            } else {
                player.sendMessage(Lang.getPrefix() + Lang.get("common.boolean-usage", "label", label));
                return true;
            }
        }

        LobbyManager.setLobby(player.getLocation(), teleportOnJoin);

        if (teleportOnJoin != null) {
            String state = teleportOnJoin ? Lang.get("common.enabled") : Lang.get("common.disabled");
            player.sendMessage(Lang.getPrefix() + Lang.get("lobby.set.success-with-toggle", "state", state));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("lobby.set.success"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.setlobby")) {
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
