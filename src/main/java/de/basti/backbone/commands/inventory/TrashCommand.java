package de.basti.backbone.commands.inventory;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Befehl /trash
 * Öffnet ein leeres Inventar, dessen Inhalt beim Schließen dauerhaft vernichtet wird.
 * Permission: backbone.trash
 */
public class TrashCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.trash")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.trash"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        TrashHolder holder = new TrashHolder();
        String title = Lang.get("trash.title");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        player.openInventory(inv);
        player.sendMessage(Lang.getPrefix() + Lang.get("trash.opened"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
