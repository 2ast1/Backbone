package de.basti.backbone.commands.namecolor;

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
 * Command to display your own name color or another player's name color.
 *
 * Usage:
 * /namecolor          - Displays your own name color
 * /namecolor <player> - Displays the specified player's name color
 */
public class NameColorCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!PrefixManager.isNameColorEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.disabled"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.usage-console", "label", label));
                return true;
            }

            if (!player.hasPermission("backbone.namecolor")) {
                player.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.namecolor"));
                return true;
            }

            if (PrefixManager.hasNameColor(player)) {
                String formatted = PrefixManager.getFormattedNameColor(player);
                String raw = PrefixManager.getNameColor(player);
                sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.current-self", "formatted_name", formatted + player.getName(), "code", raw));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.none-self"));
            }
            return true;
        }

        if (!sender.hasPermission("backbone.namecolor.others")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.namecolor.others"));
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = PlayerResolver.resolveUuid(targetName);

        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }
        targetName = PlayerResolver.getPlayerName(targetUuid);

        if (PrefixManager.hasNameColor(targetUuid)) {
            String formatted = PrefixManager.getFormattedNameColor(targetUuid);
            String raw = PrefixManager.getNameColor(targetUuid);
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.current-other", "player", targetName, "formatted_name", formatted + targetName, "code", raw));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.none-other", "player", targetName));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isNameColorEnabled() || !sender.hasPermission("backbone.namecolor.others")) return Collections.emptyList();
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
