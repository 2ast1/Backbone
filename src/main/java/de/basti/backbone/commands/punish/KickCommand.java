package de.basti.backbone.commands.punish;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.automod.punish.HistoryUtil;
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

/**
 * Befehl /kick <player> [grund]
 * Permission: backbone.kick
 */
public class KickCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String prefix = AutoMod.getPrefix();

        if (!sender.hasPermission("backbone.kick")) {
            sender.sendMessage(prefix + " " + Lang.get("common.no-permission-plain"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(prefix + " " + Lang.get("punish.kick.usage", "label", label));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null || !target.isOnline()) {
            sender.sendMessage(prefix + " " + Lang.get("common.player-not-online", "player", args[0]));
            return true;
        }

        if (sender instanceof Player p && p.equals(target)) {
            sender.sendMessage(prefix + " " + Lang.get("punish.kick.self-error"));
            return true;
        }

        String reason = (args.length >= 2)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : Lang.get("common.no-reason");
        String operator = sender.getName();

        HistoryUtil.addEntry(target.getUniqueId(), "KICK", reason, operator, "-");

        String kickScreen = Lang.get("punish.kick.screen", "operator", operator, "reason", reason);
        target.kickPlayer(kickScreen);

        sender.sendMessage(prefix + " " + Lang.get("punish.kick.success", "player", target.getName(), "reason", reason));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.kick")) {
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
