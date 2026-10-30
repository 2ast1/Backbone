package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.WarnManager;
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
import java.util.UUID;

/**
 * Befehl /warn <player> <grund>
 * Permission: backbone.warn
 */
public class WarnCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.warn")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(prefix + " " + Lang.get("punish.warn.usage", "label", label));
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

        if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.warn.self-error"));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String operator = sender.getName();

        WarnManager.warnPlayer(targetUuid, targetName, reason, operator);
        sender.sendMessage(prefix + " " + Lang.get("punish.warn.success", "player", targetName, "reason", reason));
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
        } else if (args.length == 2) {
            String current = args[1].toLowerCase();
            List<String> reasons = Lang.getList("punish.ban.reasons");
            for (String r : reasons) {
                if (r.toLowerCase().startsWith(current)) {
                    completions.add(r);
                }
            }
        }

        return completions;
    }
}
