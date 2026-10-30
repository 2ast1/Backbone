package de.basti.backbone.commands.scoreboard;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.scoreboard.ScoreboardManager;
import de.basti.backbone.util.scoreboard.ScoreboardTemplate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for controlling and managing the scoreboard system (/scoreboard, /sb).
 * Permissions: backbone.scoreboard (self toggle), backbone.scoreboard.admin (Reload, List, Show/Hide)
 */
public class ScoreboardCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.scoreboard")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.scoreboard"));
            return true;
        }

        // 1. /scoreboard (without arguments = Toggle)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.usage-console", "label", label));
                return true;
            }
            togglePlayer(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                    return true;
                }
                togglePlayer(player);
            }
            case "on", "enable", "an" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                    return true;
                }
                ScoreboardManager.setPlayerScoreboardDisabled(player, false);
                player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.enabled"));
            }
            case "off", "disable", "aus" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                    return true;
                }
                if (!ScoreboardManager.isPlayerToggleAllowed(player)) {
                    player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.world-toggle-disabled"));
                    return true;
                }
                ScoreboardManager.setPlayerScoreboardDisabled(player, true);
                player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.disabled"));
            }
            case "reload", "rl" -> {
                if (!sender.hasPermission("backbone.scoreboard.admin")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.scoreboard.admin"));
                    return true;
                }
                ScoreboardManager.reload();
                sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.reload-success"));
            }
            case "list" -> {
                if (!sender.hasPermission("backbone.scoreboard.admin")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.scoreboard.admin"));
                    return true;
                }
                showList(sender);
            }
            case "show" -> {
                if (!sender.hasPermission("backbone.scoreboard.admin")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.scoreboard.admin"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.show.usage", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[1]));
                    return true;
                }
                ScoreboardManager.setPlayerScoreboardDisabled(target, false);
                sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.show.sender", "player", target.getName()));
                target.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.show.target"));
            }
            case "hide" -> {
                if (!sender.hasPermission("backbone.scoreboard.admin")) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.scoreboard.admin"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.hide.usage", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[1]));
                    return true;
                }
                ScoreboardManager.setPlayerScoreboardDisabled(target, true);
                sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.hide.sender", "player", target.getName()));
                target.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.hide.target"));
            }
            default -> {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.unknown-subcommand", "subcommand", args[0]));
                sender.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.usage", "label", label));
            }
        }

        return true;
    }

    private void togglePlayer(Player player) {
        boolean currentlyDisabled = ScoreboardManager.isPlayerScoreboardDisabled(player);

        if (!currentlyDisabled) {
            if (!ScoreboardManager.isPlayerToggleAllowed(player)) {
                player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.world-toggle-disabled"));
                return;
            }
            ScoreboardManager.setPlayerScoreboardDisabled(player, true);
            player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.disabled"));
        } else {
            ScoreboardManager.setPlayerScoreboardDisabled(player, false);
            player.sendMessage(Lang.getPrefix() + Lang.get("scoreboard.enabled"));
        }
    }

    private void showList(CommandSender sender) {
        sender.sendMessage(Lang.get("scoreboard.list.header"));
        for (ScoreboardTemplate tmpl : ScoreboardManager.getAllTemplates()) {
            String status = tmpl.isEnabled() ? Lang.get("common.enabled") : Lang.get("common.disabled");
            String worlds = String.join(", ", tmpl.getWorlds());
            sender.sendMessage(Lang.get("scoreboard.list.entry",
                    "id", tmpl.getId(),
                    "name", tmpl.getName(),
                    "status", status,
                    "priority", String.valueOf(tmpl.getPriority()),
                    "worlds", worlds));
        }
        sender.sendMessage(Lang.get("scoreboard.list.footer"));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.scoreboard")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> options = new ArrayList<>(List.of("on", "off", "toggle"));
            if (sender.hasPermission("backbone.scoreboard.admin")) {
                options.add("reload");
                options.add("list");
                options.add("show");
                options.add("hide");
            }
            for (String opt : options) {
                if (opt.startsWith(current)) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 2 && sender.hasPermission("backbone.scoreboard.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("show") || sub.equals("hide")) {
                String current = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (de.basti.backbone.util.HideManager.canSee(sender, p) && p.getName().toLowerCase().startsWith(current)) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}
