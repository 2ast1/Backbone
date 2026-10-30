package de.basti.backbone.commands.gamemode;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Universal gamemode command (/gm &lt;mode&gt; [player]).
 * Permission: backbone.gamemode
 */
public class GameModeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.gamemode")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.gamemode"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.usage", "label", label));
            return true;
        }

        GameMode mode = parseGameMode(args[0]);
        if (mode == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.unknown-mode", "mode", args[0]));
            sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.valid-modes"));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("backbone.gamemode.others")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.gamemode.others"));
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[1]));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.usage-console", "label", label, "mode", args[0]));
                return true;
            }
            target = player;
        }

        applyGameMode(sender, target, mode);
        return true;
    }

    public static GameMode parseGameMode(String input) {
        if (input == null) return null;
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "0", "s", "survival", "überleben", "surv" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "kreativ", "creat" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "abenteuer", "advent" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "zuschauer", "spec" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    public static String getModeName(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> Lang.get("gamemode.mode.survival");
            case CREATIVE -> Lang.get("gamemode.mode.creative");
            case ADVENTURE -> Lang.get("gamemode.mode.adventure");
            case SPECTATOR -> Lang.get("gamemode.mode.spectator");
        };
    }

    public static String getGermanName(GameMode mode) {
        return getModeName(mode);
    }

    public static void applyGameMode(CommandSender sender, Player target, GameMode mode) {
        target.setGameMode(mode);
        String modeName = getModeName(mode);

        if (target.equals(sender)) {
            target.sendMessage(Lang.getPrefix() + Lang.get("gamemode.changed-self", "mode", modeName));
        } else {
            target.sendMessage(Lang.getPrefix() + Lang.get("gamemode.changed-target", "sender", sender.getName(), "mode", modeName));
            sender.sendMessage(Lang.getPrefix() + Lang.get("gamemode.changed-sender", "player", target.getName(), "mode", modeName));
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.gamemode")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> modes = List.of("survival", "creative", "adventure", "spectator", "0", "1", "2", "3");
            for (String m : modes) {
                if (m.startsWith(current)) {
                    completions.add(m);
                }
            }
        } else if (args.length == 2) {
            String current = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (de.basti.backbone.util.HideManager.canSee(sender, player) && player.getName().toLowerCase().startsWith(current)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
