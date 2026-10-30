package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.LobbyManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Command to teleport to the lobby (/lobby, /hub).
 * Permission: backbone.lobby
 */
public class LobbyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.lobby")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.lobby"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        Location lobby = LobbyManager.getLobbyLocation();
        if (lobby == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("lobby.not-set"));
            return true;
        }

        player.teleport(lobby);
        player.sendMessage(Lang.getPrefix() + Lang.get("lobby.teleported"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return List.of();
    }
}
