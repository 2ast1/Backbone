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
 * Command to remove the prefix from yourself or other players.
 * Permissions:
 * - backbone.prefix        (for yourself)
 * - backbone.prefix.others (for other players)
 */
public class RemoveprefixCommand implements CommandExecutor, TabCompleter {

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

            if (!sender.hasPermission("backbone.prefix")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.prefix"));
                return true;
            }

            if (!PrefixManager.hasCustomPrefix(player)) {
                if (PrefixManager.hasPrefix(player)) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.inherited"));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.none-self"));
                }
                return true;
            }

            PrefixManager.deletePrefix(player);
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.success-self"));
            return true;
        }

        // Edit another player
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
        Player targetPlayer = Bukkit.getPlayer(targetUuid);

        if (!PrefixManager.hasCustomPrefix(targetUuid)) {
            if (PrefixManager.hasPrefix(targetUuid)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.inherited-other", "player", targetName));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.none-other", "player", targetName));
            }
            return true;
        }

        PrefixManager.deletePrefix(targetUuid);
        sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.success-sender", "player", targetName));

        if (targetPlayer != null && targetPlayer.isOnline() && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.remove.success-target", "sender", sender.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isPrefixEnabled() || !sender.hasPermission("backbone.prefix.others")) {
            return Collections.emptyList();
        }

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
