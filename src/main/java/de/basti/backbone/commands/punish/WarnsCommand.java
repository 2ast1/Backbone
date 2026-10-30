package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.HistoryUtil;
import de.basti.backbone.util.automod.punish.WarnManager;
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
import java.util.UUID;

/**
 * Befehl /warns <player>
 * Zeigt alle Verwarnungen eines Spielers an.
 * Permission: backbone.warn
 */
public class WarnsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.warn")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.warns.usage", "label", label));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUuid;

        if (target != null) {
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            targetUuid = PlayerResolver.resolveUuid(targetName);
            if (targetUuid != null) {
                targetName = PlayerResolver.getPlayerName(targetUuid);
            } else if (PlayerResolver.isValidUsername(targetName)) {
                targetUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                sender.sendMessage(prefix + " " + Lang.get("common.player-not-found", "player", targetName));
                return true;
            }
        }

        List<HistoryUtil.HistoryEntry> warns = WarnManager.getWarnings(targetUuid);
        if (warns.isEmpty()) {
            sender.sendMessage(prefix + " " + Lang.get("punish.warns.empty", "player", targetName));
            return true;
        }

        sender.sendMessage(prefix + " " + Lang.get("punish.warns.header", "player", targetName, "count", String.valueOf(warns.size())));
        for (int i = 0; i < warns.size(); i++) {
            HistoryUtil.HistoryEntry entry = warns.get(i);
            sender.sendMessage(Lang.get("punish.warns.entry", "index", String.valueOf(i + 1), "date", entry.date(), "by", entry.by(), "reason", entry.reason()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.warn")) {
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
        }

        return completions;
    }
}
