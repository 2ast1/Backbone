package de.basti.backbone.commands.message;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.message.PrivateMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command for sending private direct messages to other players or console (/msg).
 * Aliases: tell, whisper, w, m, pm, dm.
 * Permission: backbone.msg (default: op).
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.msg")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.msg"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("msg.usage"));
            return true;
        }

        String targetName = args[0].trim();
        CommandSender target = null;

        if (targetName.equalsIgnoreCase("console") || targetName.equalsIgnoreCase("server")) {
            target = Bukkit.getConsoleSender();
        } else {
            Player p = Bukkit.getPlayer(targetName);
            if (p != null && p.isOnline()) {
                if (!(sender instanceof Player senderPlayer) || HideManager.canSee(senderPlayer, p)) {
                    target = p;
                }
            }
        }

        if (target == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        String message = sb.toString();

        PrivateMessageManager.sendMessage(sender, target, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.msg")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String lower = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player senderPlayer && !HideManager.canSee(senderPlayer, p)) {
                    continue;
                }
                if (!p.equals(sender) && p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                    suggestions.add(p.getName());
                }
            }
            if ("console".startsWith(lower)) {
                suggestions.add("console");
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
