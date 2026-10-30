package de.basti.backbone.commands.inventory;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command to open your own Enderchest or another player's Enderchest (including offline players).
 * Permission: backbone.enderchest
 */
public class EnderchestCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.enderchest")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.enderchest"));
            return true;
        }

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (args.length == 0) {
            viewer.openInventory(viewer.getEnderChest());
            viewer.sendMessage(Lang.getPrefix() + Lang.get("enderchest.opened-self"));
            return true;
        }

        String targetInput = args[0];
        UUID targetUuid = PlayerResolver.resolveUuid(targetInput);

        if (targetUuid == null) {
            viewer.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetInput));
            return true;
        }

        String targetName = PlayerResolver.getPlayerName(targetUuid);
        Player onlineTarget = Bukkit.getPlayer(targetUuid);

        if (onlineTarget != null && onlineTarget.isOnline()) {
            viewer.openInventory(onlineTarget.getEnderChest());
            viewer.sendMessage(Lang.getPrefix() + Lang.get("enderchest.opened-other", "player", targetName, "status", Lang.get("common.online-tag")));
        } else {
            EnderchestViewSession session = new EnderchestViewSession(targetUuid, targetName);
            viewer.openInventory(session.getInventory());
            viewer.sendMessage(Lang.getPrefix() + Lang.get("enderchest.opened-other", "player", targetName, "status", Lang.get("common.offline-tag")));
            if (session.isReadOnly()) {
                viewer.sendMessage(Lang.getPrefix() + Lang.get("enderchest.readonly-hint"));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.enderchest")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (String name : PlayerResolver.getAllKnownPlayerNames()) {
                if (name.toLowerCase().startsWith(current)) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}
