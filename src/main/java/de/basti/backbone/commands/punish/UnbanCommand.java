package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.BanUtil;
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
 * Command to unban players.
 * Usage:
 * /unban <player>
 */
public class UnbanCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.unban")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.unban.usage", "label", label));
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = BanUtil.resolveBannedUuid(targetName);

        if (targetUuid == null) {
            targetUuid = de.basti.backbone.util.PlayerResolver.resolveUuid(targetName);
            if (targetUuid != null) {
                targetName = de.basti.backbone.util.PlayerResolver.getPlayerName(targetUuid);
            }
        } else {
            String resolvedName = BanUtil.getPlayerName(targetUuid);
            if (resolvedName != null && !resolvedName.isBlank()) {
                targetName = resolvedName;
            }
        }

        if (targetUuid == null || !BanUtil.isBanned(targetUuid)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.unban.not-banned"));
            return true;
        }

        BanUtil.unban(targetUuid, sender.getName());

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(prefix + " " + Lang.get("punish.unban.target", "sender", sender.getName()));
        }

        sender.sendMessage(prefix + " " + Lang.get("punish.unban.success", "player", targetName));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.unban")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> bannedNames = BanUtil.getBannedPlayerNames();
            for (String name : bannedNames) {
                if (name.toLowerCase().startsWith(current)) {
                    completions.add(name);
                }
            }
        }

        return completions;
    }
}
