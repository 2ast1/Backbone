package de.basti.backbone.commands.chat;

import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.chat.SlowchatManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Befehl /slowchat [duration]
 * Permission: backbone.slowchat
 */
public class SlowchatCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.slowchat")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.slowchat"));
            return true;
        }

        if (args.length == 0) {
            if (SlowchatManager.isSlowchatEnabled()) {
                SlowchatManager.disable();
                Bukkit.broadcastMessage(Lang.getPrefix() + Lang.get("slowchat.disabled-broadcast", "sender", sender.getName()));
            } else {
                SlowchatManager.enable(0);
                Bukkit.broadcastMessage(Lang.getPrefix() + Lang.get("slowchat.enabled-broadcast-permanent", "sender", sender.getName()));
            }
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("disable") || arg.equalsIgnoreCase("aus")) {
            SlowchatManager.disable();
            Bukkit.broadcastMessage(Lang.getPrefix() + Lang.get("slowchat.disabled-broadcast", "sender", sender.getName()));
            return true;
        }

        if (DurationUtil.isPermanent(arg)) {
            SlowchatManager.enable(0);
            Bukkit.broadcastMessage(Lang.getPrefix() + Lang.get("slowchat.enabled-broadcast-permanent", "sender", sender.getName()));
            return true;
        }

        if (DurationUtil.isDurationSyntax(arg)) {
            long durationMillis = DurationUtil.parseDuration(arg);
            if (durationMillis <= 0) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("slowchat.invalid-duration"));
                return true;
            }
            SlowchatManager.enable(durationMillis);
            String formatted = DurationUtil.formatDuration(durationMillis);
            Bukkit.broadcastMessage(Lang.getPrefix() + Lang.get("slowchat.enabled-broadcast-temporary", "sender", sender.getName(), "duration", formatted));
            return true;
        }

        sender.sendMessage(Lang.getPrefix() + Lang.get("slowchat.usage", "label", label));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.slowchat")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> suggestions = List.of("off", "5m", "10m", "15m", "30m", "1h", "2h", "perm");
            List<String> matches = new ArrayList<>();
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(current)) {
                    matches.add(s);
                }
            }
            return matches;
        }

        return Collections.emptyList();
    }
}
