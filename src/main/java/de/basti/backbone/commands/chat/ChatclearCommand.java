package de.basti.backbone.commands.chat;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Befehl /chatclear
 * Permission: backbone.chatclear
 */
public class ChatclearCommand implements CommandExecutor, TabCompleter {

    private static final int CLEAR_LINES = 100;

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.chatclear")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.chatclear"));
            return true;
        }

        String clearLine = " ";
        String announcement = Lang.getPrefix() + Lang.get("chatclear.broadcast", "sender", sender.getName());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("backbone.chatclear.bypass")) {
                for (int i = 0; i < CLEAR_LINES; i++) {
                    online.sendMessage(clearLine);
                }
            }
            online.sendMessage(announcement);
        }

        Bukkit.getConsoleSender().sendMessage(announcement);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
