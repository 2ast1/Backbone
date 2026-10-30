package de.basti.backbone.commands.chat;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PrefixManager;
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
 * Befehl /staffchat <nachricht> oder /sc <nachricht>
 * Permission: backbone.staffchat (zum Senden und Empfangen)
 */
public class StaffchatCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.staffchat")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.staffchat"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("staffchat.usage", "label", label));
            return true;
        }

        String message = String.join(" ", args);
        if (sender.hasPermission("backbone.chat.color") || sender.isOp()) {
            message = PrefixManager.formatColors(message);
        }

        String formatted = Lang.get("staffchat.format", "sender", sender.getName(), "message", message);

        // An alle berechtigten Online-Spieler senden
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("backbone.staffchat") || online.isOp()) {
                online.sendMessage(formatted);
            }
        }

        // Auch in der Konsole anzeigen
        Bukkit.getConsoleSender().sendMessage(formatted);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
