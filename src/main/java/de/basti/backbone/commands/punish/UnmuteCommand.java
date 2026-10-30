package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to unmute players.
 * Usage:
 * /unmute <player>
 */
public class UnmuteCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.unmute")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.unmute.usage", "label", label));
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = de.basti.backbone.util.PlayerResolver.resolveUuid(targetName);
        if (targetUuid == null && de.basti.backbone.util.PlayerResolver.isValidUsername(targetName)) {
            targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (targetUuid == null || !MuteUtil.isMuted(targetUuid)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.unmute.not-muted"));
            return true;
        }

        targetName = de.basti.backbone.util.PlayerResolver.getPlayerName(targetUuid);
        Player target = Bukkit.getPlayer(targetUuid);

        MuteUtil.unmute(targetUuid, sender.getName());

        if (target != null && target.isOnline()) {
            target.sendMessage(prefix + " " + Lang.get("punish.unmute.target", "sender", sender.getName()));
        }

        sender.sendMessage(prefix + " " + Lang.get("punish.unmute.success", "player", targetName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("backbone.unmute")) {
            return java.util.Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!de.basti.backbone.util.HideManager.canSee(sender, player)) continue;
                if (player.getName().toLowerCase().startsWith(current)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
