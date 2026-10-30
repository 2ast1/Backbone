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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command to ban players (temporarily or permanently).
 * Usage:
 * /ban <player> [time] [reason]
 */
public class BanCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.ban")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.ban.usage", "label", label));
            sender.sendMessage(Lang.get("punish.ban.examples-header"));
            sender.sendMessage(Lang.get("punish.ban.example-1", "label", label));
            sender.sendMessage(Lang.get("punish.ban.example-2", "label", label));
            sender.sendMessage(Lang.get("punish.ban.example-3", "label", label));
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
            } else if (de.basti.backbone.util.PlayerResolver.isValidUsername(targetName)) {
                targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                sender.sendMessage(prefix + " " + Lang.get("common.player-not-found", "player", targetName));
                return true;
            }
        }

        if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.ban.self-error"));
            return true;
        }

        long durationMillis = 0;
        String reason;

        if (args.length >= 2) {
            boolean isPermanentKeyword = args[1].equalsIgnoreCase("permanent") || args[1].equalsIgnoreCase("perm");

            if (BanUtil.isDurationSyntax(args[1])) {
                durationMillis = BanUtil.parseDuration(args[1]);
                if (durationMillis <= 0) {
                    sender.sendMessage(prefix + " " + Lang.get("punish.ban.duration-zero"));
                    return true;
                }
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                } else {
                    reason = Lang.get("common.no-reason");
                }
            } else if (isPermanentKeyword) {
                durationMillis = 0;
                if (args.length >= 3) {
                    reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                } else {
                    reason = Lang.get("common.no-reason");
                }
            } else {
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        } else {
            reason = Lang.get("common.no-reason");
        }

        String operator = sender.getName();
        String formattedDuration;

        if (durationMillis > 0) {
            BanUtil.tempBan(targetUuid, targetName, durationMillis, reason, operator);
            formattedDuration = BanUtil.formatDuration(durationMillis);
        } else {
            BanUtil.ban(targetUuid, targetName, reason, operator);
            formattedDuration = Lang.get("common.permanent");
        }

        sender.sendMessage(prefix + " " + Lang.get("punish.ban.success", "player", targetName, "duration", formattedDuration, "reason", reason));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.ban")) {
            return Collections.emptyList();
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
            List<String> suggestions = List.of(
                    "5m", "15m", "30m", "1h", "2h", "12h", "1d", "3d", "7d", "14d", "30d", "90d", "permanent"
            );
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(current)) {
                    completions.add(s);
                }
            }
        } else if (args.length == 3) {
            String current = args[2].toLowerCase();
            List<String> reasons = Lang.getList("punish.ban.reasons");
            for (String r : reasons) {
                if (r.toLowerCase().startsWith(current)) {
                    completions.add(r);
                }
            }
        }

        return completions;
    }
}
