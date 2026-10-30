package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.message.AutoModMessages;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command to mute players (temporarily or permanently).
 * Usage:
 * /mute <player> [time] [reason]
 */
public class MuteCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.mute")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.mute.usage", "label", label));
            sender.sendMessage(Lang.get("punish.mute.examples-header"));
            sender.sendMessage(Lang.get("punish.mute.example-1", "label", label));
            sender.sendMessage(Lang.get("punish.mute.example-2", "label", label));
            sender.sendMessage(Lang.get("punish.mute.example-3", "label", label));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUuid;

        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUuid = de.basti.backbone.util.PlayerResolver.resolveUuid(targetName);
            if (targetUuid != null) {
                targetName = de.basti.backbone.util.PlayerResolver.getPlayerName(targetUuid);
                target = Bukkit.getPlayer(targetUuid);
            } else if (de.basti.backbone.util.PlayerResolver.isValidUsername(targetName)) {
                targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                sender.sendMessage(prefix + " " + Lang.get("common.player-not-found", "player", targetName));
                return true;
            }
        }

        if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.mute.self-error"));
            return true;
        }

        long durationMillis = 0;
        String reason;
        long until;

        if (args.length >= 2) {
            boolean isPermanentKeyword = args[1].equalsIgnoreCase("permanent") || args[1].equalsIgnoreCase("perm");

            if (MuteUtil.isDurationSyntax(args[1])) {
                durationMillis = MuteUtil.parseDuration(args[1]);
                if (durationMillis <= 0) {
                    sender.sendMessage(prefix + " " + Lang.get("punish.mute.duration-zero"));
                    return true;
                }
                until = System.currentTimeMillis() + durationMillis;
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                } else {
                    reason = Lang.get("common.no-reason");
                }
            } else if (isPermanentKeyword) {
                until = -1;
                durationMillis = 0;
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                } else {
                    reason = Lang.get("common.no-reason");
                }
            } else {
                until = -1;
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        } else {
            until = -1;
            reason = Lang.get("common.no-reason");
        }

        String operator = sender.getName();
        String formattedDuration;

        if (durationMillis > 0) {
            MuteUtil.tempMute(targetUuid, durationMillis, reason, operator);
            formattedDuration = MuteUtil.formatDuration(durationMillis);
        } else {
            MuteUtil.mute(targetUuid, reason, operator);
            formattedDuration = Lang.get("common.permanent");
        }

        if (target != null && target.isOnline()) {
            String muteMsg = AutoModMessages.getMuteMessage(targetName, reason, operator, false, until);
            target.sendMessage(muteMsg);
        }

        sender.sendMessage(prefix + " " + Lang.get("punish.mute.success", "player", targetName, "duration", formattedDuration, "reason", reason));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("backbone.mute")) {
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
        } else if (args.length == 2) {
            String current = args[1].toLowerCase();
            List<String> suggestions = List.of("5m", "15m", "30m", "1h", "2h", "12h", "1d", "7d", "30d", "permanent");
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(current)) {
                    completions.add(s);
                }
            }
        } else if (args.length == 3) {
            String current = args[2].toLowerCase();
            List<String> reasons = Lang.getList("punish.mute.reasons");
            for (String r : reasons) {
                if (r.toLowerCase().startsWith(current)) {
                    completions.add(r);
                }
            }
        }

        return completions;
    }
}
