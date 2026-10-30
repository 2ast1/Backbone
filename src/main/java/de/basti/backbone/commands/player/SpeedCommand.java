package de.basti.backbone.commands.player;

import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
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

/**
 * Befehl /speed [player] <walk|fly> <1-100>
 * Permissions: backbone.speed (für sich selbst) und backbone.speed.others (für andere)
 */
public class SpeedCommand implements CommandExecutor, TabCompleter {

    private enum SpeedType {
        WALK, FLY
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("speed.usage", "label", label));
            return true;
        }

        Player target = null;
        SpeedType type = null;
        Integer speedValue = null;

        if (args.length == 1) {
            // /speed <1-100>
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                return true;
            }
            target = p;
            speedValue = parseSpeed(args[0]);
            type = target.isFlying() ? SpeedType.FLY : SpeedType.WALK;
        } else if (args.length == 2) {
            // Entweder: /speed <walk|fly> <1-100>
            // Oder: /speed <player> <1-100>
            SpeedType parsedType = parseType(args[0]);
            if (parsedType != null) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                    return true;
                }
                target = p;
                type = parsedType;
                speedValue = parseSpeed(args[1]);
            } else {
                if (sender instanceof Player p && !args[0].equalsIgnoreCase(p.getName())) {
                    if (!sender.hasPermission("backbone.speed.others") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                        sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.speed.others"));
                        return true;
                    }
                }
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) target = Bukkit.getPlayer(args[0]);
                speedValue = parseSpeed(args[1]);
                if (target != null) {
                    type = target.isFlying() ? SpeedType.FLY : SpeedType.WALK;
                }
            }
        } else {
            // /speed <player> <walk|fly> <1-100>
            if (sender instanceof Player p && !args[0].equalsIgnoreCase(p.getName())) {
                if (!sender.hasPermission("backbone.speed.others") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.speed.others"));
                    return true;
                }
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getPlayer(args[0]);
            type = parseType(args[1]);
            speedValue = parseSpeed(args[2]);
        }

        if (target == null || !target.isOnline()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-online", "player", args[0]));
            return true;
        }

        if (type == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("speed.invalid-type"));
            return true;
        }

        if (speedValue == null || speedValue < 1 || speedValue > 100) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("speed.invalid-range"));
            return true;
        }

        boolean isSelf = (sender instanceof Player p) && p.equals(target);
        if (isSelf) {
            if (!sender.hasPermission("backbone.speed")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.speed"));
                return true;
            }
        } else {
            if (!sender.hasPermission("backbone.speed.others") && !sender.hasPermission("backbone.admin") && !sender.isOp()) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.speed.others"));
                return true;
            }
        }

        float bukkitSpeed;
        if (type == SpeedType.WALK) {
            if (speedValue == 1) {
                bukkitSpeed = 0.2f;
            } else {
                bukkitSpeed = Math.min(1.0f, 0.2f + ((float) (speedValue - 1) / 99.0f) * 0.8f);
            }
            target.setWalkSpeed(bukkitSpeed);
        } else {
            if (speedValue == 1) {
                bukkitSpeed = 0.1f;
            } else {
                bukkitSpeed = Math.min(1.0f, 0.1f + ((float) (speedValue - 1) / 99.0f) * 0.9f);
            }
            target.setFlySpeed(bukkitSpeed);
        }

        String typeName = type == SpeedType.WALK ? Lang.get("speed.type-walk") : Lang.get("speed.type-fly");

        if (isSelf) {
            target.sendMessage(Lang.getPrefix() + Lang.get("speed.success-self", "type", typeName, "speed", String.valueOf(speedValue)));
        } else {
            target.sendMessage(Lang.getPrefix() + Lang.get("speed.received", "sender", sender.getName(), "type", typeName, "speed", String.valueOf(speedValue)));
            sender.sendMessage(Lang.getPrefix() + Lang.get("speed.success-other", "player", target.getName(), "type", typeName, "speed", String.valueOf(speedValue)));
        }

        return true;
    }

    private SpeedType parseType(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase();
        if (lower.equals("walk") || lower.equals("laufen") || lower.equals("gehen")) {
            return SpeedType.WALK;
        }
        if (lower.equals("fly") || lower.equals("fliegen") || lower.equals("flug")) {
            return SpeedType.FLY;
        }
        return null;
    }

    private Integer parseSpeed(String input) {
        if (input == null) return null;
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.speed") && !sender.hasPermission("backbone.speed.others")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if ("walk".startsWith(current)) completions.add("walk");
            if ("fly".startsWith(current)) completions.add("fly");
            if (sender.hasPermission("backbone.speed.others") || sender.isOp()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!HideManager.canSee(sender, player)) continue;
                    if (player.getName().toLowerCase().startsWith(current)) {
                        completions.add(player.getName());
                    }
                }
            }
            for (String num : List.of("1", "5", "10", "25", "50", "100")) {
                if (num.startsWith(current)) completions.add(num);
            }
        } else if (args.length == 2) {
            String current = args[1].toLowerCase();
            if ("walk".startsWith(current)) completions.add("walk");
            if ("fly".startsWith(current)) completions.add("fly");
            for (String num : List.of("1", "5", "10", "25", "50", "100")) {
                if (num.startsWith(current)) completions.add(num);
            }
        } else if (args.length == 3) {
            String current = args[2].toLowerCase();
            for (String num : List.of("1", "5", "10", "25", "50", "100")) {
                if (num.startsWith(current)) completions.add(num);
            }
        }

        return completions;
    }
}
