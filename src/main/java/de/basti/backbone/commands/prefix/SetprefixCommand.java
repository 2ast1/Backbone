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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command to set the prefix for yourself or other players.
 * Permissions:
 * - backbone.prefix        (for yourself)
 * - backbone.prefix.others (for other players)
 */
public class SetprefixCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!PrefixManager.isPrefixEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.disabled"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.usage-header"));
            sender.sendMessage(Lang.get("prefix_cmd.set.usage-self", "label", label));
            sender.sendMessage(Lang.get("prefix_cmd.set.usage-other", "label", label));
            return true;
        }

        UUID targetUuid;
        String targetName;
        String newPrefix;
        boolean isSelf;

        if (sender instanceof Player player) {
            if (args.length >= 2 && sender.hasPermission("backbone.prefix.others")) {
                UUID resolvedOther = PlayerResolver.resolveUuid(args[0]);
                if (resolvedOther != null) {
                    targetUuid = resolvedOther;
                    targetName = PlayerResolver.getPlayerName(resolvedOther);
                    newPrefix = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    isSelf = targetUuid.equals(player.getUniqueId());
                } else if (args[0].contains("&") || args[0].contains("§")) {
                    // Enthält Farbcodes -> eindeutig eigener mehrteiliger Präfix
                    targetUuid = player.getUniqueId();
                    targetName = player.getName();
                    newPrefix = String.join(" ", args);
                    isSelf = true;
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
                    return true;
                }
            } else {
                targetUuid = player.getUniqueId();
                targetName = player.getName();
                newPrefix = String.join(" ", args);
                isSelf = true;
            }
        } else {
            if (args.length < 2) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.usage-console", "label", label));
                return true;
            }
            targetUuid = PlayerResolver.resolveUuid(args[0]);
            if (targetUuid == null) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
                return true;
            }
            targetName = PlayerResolver.getPlayerName(targetUuid);
            newPrefix = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            isSelf = false;
        }

        if (isSelf) {
            if (!sender.hasPermission("backbone.prefix")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.prefix"));
                return true;
            }
        } else {
            if (!sender.hasPermission("backbone.prefix.others")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.prefix.others"));
                return true;
            }
        }

        // Prevent staff impersonation without backbone.prefix.admin
        if (!sender.hasPermission("backbone.prefix.admin")) {
            String blockedTitle = PrefixManager.getBlockedStaffTitle(newPrefix);
            if (blockedTitle != null) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.blocked-staff-title", "title", blockedTitle));
                return true;
            }
        }

        PrefixManager.setPrefix(targetUuid, newPrefix);

        String formatted = PrefixManager.formatColors(newPrefix);
        if (isSelf) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.success-self", "formatted_prefix", formatted, "raw_prefix", newPrefix));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.success-sender", "player", targetName, "formatted_prefix", formatted, "raw_prefix", newPrefix));
            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null && onlineTarget.isOnline() && !onlineTarget.equals(sender)) {
                onlineTarget.sendMessage(Lang.getPrefix() + Lang.get("prefix_cmd.set.success-target", "sender", sender.getName(), "formatted_prefix", formatted, "raw_prefix", newPrefix));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isPrefixEnabled()) return Collections.emptyList();
        boolean canOthers = sender.hasPermission("backbone.prefix.others");
        boolean canSelf = sender.hasPermission("backbone.prefix");

        if (!canOthers && !canSelf) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("backbone.prefix.admin");
        List<String> templates = new ArrayList<>(Lang.getList("prefix_cmd.suggestions.player"));
        if (isAdmin) {
            templates.addAll(Lang.getList("prefix_cmd.suggestions.admin"));
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase();

            if (canOthers) {
                for (String playerName : PlayerResolver.getAllKnownPlayerNames()) {
                    if (playerName.toLowerCase().startsWith(current)) {
                        completions.add(playerName);
                    }
                }
            }

            for (String template : templates) {
                if (template.toLowerCase().startsWith(current)) {
                    completions.add(template);
                }
            }
        } else if (args.length == 2 && canOthers) {
            String current = args[1].toLowerCase();
            for (String template : templates) {
                if (template.toLowerCase().startsWith(current)) {
                    completions.add(template);
                }
            }
        }

        return completions;
    }
}
