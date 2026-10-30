package de.basti.backbone.commands.prefix;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command to display your own prefix or another player's prefix.
 *
 * Usage:
 * /prefix          - Displays your own prefix
 * /prefix <player> - Displays the specified player's prefix
 */
public class PrefixCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!PrefixManager.isPrefixEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.disabled"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.usage-console", "label", label));
                return true;
            }

            if (!player.hasPermission("backbone.prefix")) {
                player.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.prefix"));
                return true;
            }

            if (PrefixManager.hasPrefix(player)) {
                String formatted = PrefixManager.getPrefix(player);
                String raw = PrefixManager.getRawPrefix(player);
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.current-self", "formatted_prefix", formatted, "code", raw));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.none-self"));
            }
            return true;
        }

        if (!sender.hasPermission("backbone.prefix.others")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.prefix.others"));
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = PlayerResolver.resolveUuid(targetName);

        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }
        targetName = PlayerResolver.getPlayerName(targetUuid);

        if (PrefixManager.hasPrefix(targetUuid)) {
            String formatted = PrefixManager.getPrefix(targetUuid);
            String raw = PrefixManager.getRawPrefix(targetUuid);
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.current-other", "player", targetName, "formatted_prefix", formatted, "code", raw));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.none-other", "player", targetName));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isPrefixEnabled() || !sender.hasPermission("backbone.prefix.others")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (String name : PlayerResolver.getAllKnownPlayerNames()) {
                if (name.toLowerCase().startsWith(current)) {
                    completions.add(name);
                }
            }
        }

        return completions;
    }
}
