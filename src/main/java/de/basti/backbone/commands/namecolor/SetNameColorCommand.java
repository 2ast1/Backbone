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
 * Command to set the name color for yourself or other players.
 * Permissions:
 * - backbone.namecolor        (for yourself)
 * - backbone.namecolor.others (for other players)
 */
public class SetNameColorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> COLOR_SUGGESTIONS = List.of(
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9",
            "&a", "&b", "&c", "&d", "&e", "&f",
            "&a&l", "&c&l", "&b&l", "&e&l", "&6&l",
            "&#FF5555", "&#55FF55", "&#5555FF", "&#FFAA00", "&#AA00AA", "&#00AAAA"
    );

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!PrefixManager.isNameColorEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.disabled"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.usage-header"));
            sender.sendMessage(Lang.get("namecolor.set.usage-self", "label", label));
            sender.sendMessage(Lang.get("namecolor.set.usage-other", "label", label));
            sender.sendMessage(Lang.get("namecolor.set.usage-codes"));
            return true;
        }

        UUID targetUuid;
        String targetName;
        String colorInput;
        boolean isSelf;

        if (sender instanceof Player player) {
            if (args.length >= 2 && sender.hasPermission("backbone.namecolor.others")) {
                UUID resolvedOther = PlayerResolver.resolveUuid(args[0]);
                if (resolvedOther != null) {
                    targetUuid = resolvedOther;
                    targetName = PlayerResolver.getPlayerName(resolvedOther);
                    colorInput = String.join("", java.util.Arrays.copyOfRange(args, 1, args.length));
                    isSelf = targetUuid.equals(player.getUniqueId());
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
                    return true;
                }
            } else if (args.length >= 2) {
                String combined = String.join("", args);
                String testClean = PrefixManager.extractColorCodes(combined);
                if (!testClean.isEmpty()) {
                    targetUuid = player.getUniqueId();
                    targetName = player.getName();
                    colorInput = combined;
                    isSelf = true;
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
                    return true;
                }
            } else {
                targetUuid = player.getUniqueId();
                targetName = player.getName();
                colorInput = args[0];
                isSelf = true;
            }
        } else {
            if (args.length < 2) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.usage-console", "label", label));
                return true;
            }
            targetUuid = PlayerResolver.resolveUuid(args[0]);
            if (targetUuid == null) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
                return true;
            }
            targetName = PlayerResolver.getPlayerName(targetUuid);
            colorInput = String.join("", java.util.Arrays.copyOfRange(args, 1, args.length));
            isSelf = false;
        }

        if (isSelf) {
            if (!sender.hasPermission("backbone.namecolor")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.namecolor"));
                return true;
            }
        } else {
            if (!sender.hasPermission("backbone.namecolor.others")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.namecolor.others"));
                return true;
            }
        }

        String clean = PrefixManager.extractColorCodes(colorInput);
        if (clean.isEmpty()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.invalid-code"));
            return true;
        }

        PrefixManager.setNameColor(targetUuid, clean);

        String formattedName = PrefixManager.formatColors(clean) + targetName + "§r";
        if (isSelf) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.success-self", "formatted_name", formattedName, "code", clean));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.success-sender", "player", targetName, "formatted_name", formattedName, "code", clean));
            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            if (onlineTarget != null && onlineTarget.isOnline() && !onlineTarget.equals(sender)) {
                onlineTarget.sendMessage(Lang.getPrefix() + Lang.get("namecolor.set.success-target", "sender", sender.getName(), "formatted_name", formattedName, "code", clean));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isNameColorEnabled()) return Collections.emptyList();
        boolean canOthers = sender.hasPermission("backbone.namecolor.others");
        boolean canSelf = sender.hasPermission("backbone.namecolor");

        if (!canOthers && !canSelf) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();

            if (canOthers) {
                for (String playerName : PlayerResolver.getAllKnownPlayerNames()) {
                    if (playerName.toLowerCase().startsWith(current)) {
                        completions.add(playerName);
                    }
                }
            }

            for (String color : COLOR_SUGGESTIONS) {
                if (color.toLowerCase().startsWith(current)) {
                    completions.add(color);
                }
            }
        } else if (args.length == 2 && canOthers) {
            String current = args[1].toLowerCase();
            for (String color : COLOR_SUGGESTIONS) {
                if (color.toLowerCase().startsWith(current)) {
                    completions.add(color);
                }
            }
        }

        return completions;
    }
}
