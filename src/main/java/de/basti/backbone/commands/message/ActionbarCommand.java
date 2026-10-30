package de.basti.backbone.commands.message;

import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.message.ActionbarManager;
import org.bukkit.Bukkit;
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

/**
 * Befehl /actionbar <broadcast|player> <text> <duration oder perm>
 * Alternativ: /actionbar <broadcast|player> <clear|stop>
 * Permission: backbone.actionbar
 */
public class ActionbarCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.actionbar")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.actionbar"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.usage", "label", label));
            return true;
        }

        boolean isBroadcast = isBroadcastTarget(args[0]);
        Player target = null;
        if (!isBroadcast) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
                return true;
            }
        }

        // Prüfung auf Clear / Stop
        String secondArg = args[1].toLowerCase();
        if (secondArg.equals("clear") || secondArg.equals("stop") || secondArg.equals("off") || secondArg.equals("aus")) {
            if (isBroadcast) {
                ActionbarManager.stopBroadcast();
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.cleared-broadcast"));
            } else {
                ActionbarManager.stopPlayer(target.getUniqueId());
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.cleared-player", "player", target.getName()));
            }
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.usage", "label", label));
            return true;
        }

        String lastArg = args[args.length - 1];
        boolean isPermanent = DurationUtil.isPermanent(lastArg);
        long durationMillis = 0;

        if (!isPermanent) {
            if (DurationUtil.isDurationSyntax(lastArg)) {
                durationMillis = DurationUtil.parseDuration(lastArg);
                if (durationMillis <= 0) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.invalid-duration"));
                    return true;
                }
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.invalid-duration"));
                return true;
            }
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1)).trim();
        if (text.isEmpty()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.usage", "label", label));
            return true;
        }

        if (isBroadcast) {
            ActionbarManager.sendBroadcast(text, durationMillis);
            if (isPermanent) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.success-broadcast-permanent"));
            } else {
                String durFormatted = DurationUtil.formatDuration(durationMillis);
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.success-broadcast-temporary", "duration", durFormatted));
            }
        } else {
            ActionbarManager.sendPlayer(target, text, durationMillis);
            if (isPermanent) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.success-player-permanent", "player", target.getName()));
            } else {
                String durFormatted = DurationUtil.formatDuration(durationMillis);
                sender.sendMessage(Lang.getPrefix() + Lang.get("actionbar.success-player-temporary", "player", target.getName(), "duration", durFormatted));
            }
        }

        return true;
    }

    private boolean isBroadcastTarget(String arg) {
        if (arg == null) return false;
        String lower = arg.toLowerCase();
        return lower.equals("broadcast") || lower.equals("brodcast") || lower.equals("all") || lower.equals("*") || lower.equals("alle");
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.actionbar")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            if ("broadcast".startsWith(current)) list.add("broadcast");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!HideManager.canSee(sender, player)) continue;
                if (player.getName().toLowerCase().startsWith(current)) {
                    list.add(player.getName());
                }
            }
            return list;
        }

        if (args.length == 2) {
            String current = args[1].toLowerCase();
            List<String> list = new ArrayList<>();
            if ("clear".startsWith(current)) list.add("clear");
            if ("stop".startsWith(current)) list.add("stop");
            return list;
        }

        String last = args[args.length - 1].toLowerCase();
        List<String> suggestions = List.of("10s", "30s", "1m", "5m", "10m", "1h", "1d", "30d", "perm", "-1");
        List<String> list = new ArrayList<>();
        for (String s : suggestions) {
            if (s.toLowerCase().startsWith(last)) {
                list.add(s);
            }
        }
        return list;
    }
}
