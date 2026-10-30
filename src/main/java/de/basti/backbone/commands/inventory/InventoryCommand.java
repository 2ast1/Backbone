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
 * Command to view and edit player inventories.
 * Supports both online and offline players.
 * Permission: backbone.inventory
 * Advanced UI: backbone.inventory.advanced
 */
public class InventoryCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.inventory")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.inventory"));
            return true;
        }

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (args.length < 1) {
            viewer.sendMessage(Lang.getPrefix() + Lang.get("inventory.usage", "label", label));
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
        boolean isOffline = (onlineTarget == null || !onlineTarget.isOnline());
        boolean hasAdvanced = viewer.hasPermission("backbone.inventory.advanced");

        InventoryViewSession session = new InventoryViewSession(targetUuid, targetName, isOffline, hasAdvanced);
        viewer.openInventory(session.getInventory());

        String status = isOffline ? Lang.get("common.offline-tag") : Lang.get("common.online-tag");
        viewer.sendMessage(Lang.getPrefix() + Lang.get("inventory.opened", "player", targetName, "status", status));
        if (session.isReadOnly()) {
            viewer.sendMessage(Lang.getPrefix() + Lang.get("inventory.readonly-hint"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.inventory")) {
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
