package de.basti.backbone.commands.message;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.message.PrivateMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Command for replying to the last received private message (/reply).
 * Alias: r.
 * Permission: backbone.reply (default: op).
 */
public class ReplyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.reply")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.reply"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("reply.usage"));
            return true;
        }

        UUID senderUuid = PrivateMessageManager.getSenderUuid(sender);
        UUID targetUuid = PrivateMessageManager.getReplyTarget(senderUuid);

        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("reply.no-target"));
            return true;
        }

        CommandSender target = null;
        if (targetUuid.equals(PrivateMessageManager.CONSOLE_UUID)) {
            target = Bukkit.getConsoleSender();
        } else {
            Player p = Bukkit.getPlayer(targetUuid);
            if (p != null && p.isOnline()) {
                if (!(sender instanceof Player senderPlayer) || HideManager.canSee(senderPlayer, p)) {
                    target = p;
                }
            }
        }

        if (target == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("reply.no-target"));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        String message = sb.toString();

        PrivateMessageManager.sendMessage(sender, target, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
