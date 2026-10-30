package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.WarpManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Command to teleport to warps (/warp [name]), list all warps,
 * and manage blacklisted worlds (/warp blacklistworld [world]).
 * Permission: backbone.warp / backbone.warp.blacklist
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.warp")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.warp"));
            return true;
        }

        // 1. Subcommand: /warp blacklistworld [world]
        if (args.length >= 1 && args[0].equalsIgnoreCase("blacklistworld")) {
            return handleBlacklistWorld(sender, args);
        }

        // 2. Without arguments (or /warps): Show list of all warps
        if (args.length == 0 || label.equalsIgnoreCase("warps")) {
            showWarpList(sender);
            return true;
        }

        // 3. Teleport to warp
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        String warpName = args[0];
        Location loc = WarpManager.getWarp(warpName);
        if (loc == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.not-found", "warp", warpName));
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.not-found-tip"));
            return true;
        }

        if (loc.getWorld() != null && WarpManager.isWorldBlacklisted(loc.getWorld().getName()) && !player.hasPermission("backbone.warp.blacklist")) {
            player.sendMessage(Lang.getPrefix() + Lang.get("warp.set.world-blacklisted", "world", loc.getWorld().getName()));
            return true;
        }

        player.teleport(loc);
        player.sendMessage(Lang.getPrefix() + Lang.get("warp.teleported", "warp", warpName));
        return true;
    }

    private boolean handleBlacklistWorld(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.warp.blacklist")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.warp.blacklist"));
            return true;
        }

        String targetWorld;
        if (args.length >= 2) {
            targetWorld = args[1].trim();
        } else {
            if (sender instanceof Player p) {
                targetWorld = p.getWorld().getName();
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("warp.blacklist.usage"));
                return true;
            }
        }

        boolean added = WarpManager.toggleWorldBlacklist(targetWorld);
        if (added) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("warp.blacklist.added", "world", targetWorld));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("warp.blacklist.removed", "world", targetWorld));
        }
        return true;
    }

    private void showWarpList(CommandSender sender) {
        Set<String> warps = WarpManager.getWarps();
        if (warps.isEmpty()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("warp.list.empty"));
            return;
        }

        sender.sendMessage(Lang.get("warp.list.header"));
        if (sender instanceof Player player) {
            TextComponent line = new TextComponent(Lang.get("warp.list.prefix"));
            int index = 0;
            for (String w : warps) {
                TextComponent warpBtn = new TextComponent("§e" + w);
                warpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp " + w));
                warpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("warp.list.hover", "warp", w))));
                line.addExtra(warpBtn);

                index++;
                if (index < warps.size()) {
                    line.addExtra(new TextComponent("§7, "));
                }
            }
            player.spigot().sendMessage(line);
        } else {
            sender.sendMessage("§8» §e" + String.join("§7, §e", warps));
        }
        sender.sendMessage(Lang.get("warp.list.footer"));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.warp")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();

            if (sender.hasPermission("backbone.warp.blacklist") && "blacklistworld".startsWith(current)) {
                completions.add("blacklistworld");
            }

            for (String w : WarpManager.getWarps()) {
                if (w.toLowerCase().startsWith(current)) {
                    completions.add(w);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("blacklistworld")) {
            if (sender.hasPermission("backbone.warp.blacklist")) {
                String current = args[1].toLowerCase();
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(current)) {
                        completions.add(world.getName());
                    }
                }
            }
        }

        return completions;
    }
}
