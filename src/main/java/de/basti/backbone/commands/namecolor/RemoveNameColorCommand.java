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
 * Command to remove the name color from yourself or other players.
 * Permissions:
 * - backbone.namecolor        (for yourself)
 * - backbone.namecolor.others (for other players)
 */
public class RemoveNameColorCommand implements CommandExecutor, TabCompleter {

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

            if (!sender.hasPermission("backbone.namecolor")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.namecolor"));
                return true;
            }

            if (!PrefixManager.hasNameColor(player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.remove.none-self"));
                return true;
            }

            PrefixManager.removeNameColor(player);
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.remove.success-self"));
            return true;
        }

        // Edit another player
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
        Player targetPlayer = Bukkit.getPlayer(targetUuid);

        if (!PrefixManager.hasNameColor(targetUuid)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.remove.none-other", "player", targetName));
            return true;
        }

        PrefixManager.removeNameColor(targetUuid);
        sender.sendMessage(Lang.getPrefix() + Lang.get("namecolor.remove.success-sender", "player", targetName));

        if (targetPlayer != null && targetPlayer.isOnline() && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(Lang.getPrefix() + Lang.get("namecolor.remove.success-target", "sender", sender.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!PrefixManager.isNameColorEnabled() || !sender.hasPermission("backbone.namecolor.others")) {
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
