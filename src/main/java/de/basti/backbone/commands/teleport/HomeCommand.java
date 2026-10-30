package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.locations.HomeManager;
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
 * Command to teleport to homes (/home [name]), list all homes,
 * and manage blacklisted worlds (/home blacklistworld [world]).
 * Permission: backbone.home / backbone.home.blacklist
 */
public class HomeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.home")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.home"));
            return true;
        }

        // 1. Subcommand: /home blacklistworld [world]
        if (args.length >= 1 && args[0].equalsIgnoreCase("blacklistworld")) {
            return handleBlacklistWorld(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        Set<String> homes = HomeManager.getHomes(player.getUniqueId());

        // 2. /homes or /home without arguments
        if (args.length == 0 || label.equalsIgnoreCase("homes")) {
            if (homes.isEmpty()) {
                player.sendMessage(Lang.getPrefix() + Lang.get("home.empty"));
                return true;
            }

            if (homes.size() == 1 && !label.equalsIgnoreCase("homes")) {
                String singleHome = homes.iterator().next();
                teleportToHome(player, singleHome);
                return true;
            }

            showHomeList(player, homes);
            return true;
        }

        // 3. Teleport to specified home
        String targetHome = args[0];
        if (!HomeManager.homeExists(player.getUniqueId(), targetHome)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.not-found", "home", targetHome));
            if (!homes.isEmpty()) {
                showHomeList(player, homes);
            }
            return true;
        }

        teleportToHome(player, targetHome);
        return true;
    }

    private void teleportToHome(Player player, String homeName) {
        Location loc = HomeManager.getHome(player.getUniqueId(), homeName);
        if (loc == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.world-unavailable"));
            return;
        }

        if (loc.getWorld() != null && HomeManager.isWorldBlacklisted(loc.getWorld().getName()) && !player.hasPermission("backbone.home.blacklist")) {
            player.sendMessage(Lang.getPrefix() + Lang.get("home.set.world-blacklisted", "world", loc.getWorld().getName()));
            return;
        }

        player.teleport(loc);
        player.sendMessage(Lang.getPrefix() + Lang.get("home.teleported", "home", homeName));
    }

    private boolean handleBlacklistWorld(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.home.blacklist")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.home.blacklist"));
            return true;
        }

        String targetWorld;
        if (args.length >= 2) {
            targetWorld = args[1].trim();
        } else {
            if (sender instanceof Player p) {
                targetWorld = p.getWorld().getName();
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("home.blacklist.usage"));
                return true;
            }
        }

        boolean added = HomeManager.toggleWorldBlacklist(targetWorld);
        if (added) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("home.blacklist.added", "world", targetWorld));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("home.blacklist.removed", "world", targetWorld));
        }
        return true;
    }

    private void showHomeList(Player player, Set<String> homes) {
        player.sendMessage(Lang.get("home.list.header"));
        TextComponent line = new TextComponent(Lang.get("home.list.prefix"));
        int index = 0;
        for (String h : homes) {
            TextComponent homeBtn = new TextComponent("§e" + h);
            homeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + h));
            homeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("home.list.hover", "home", h))));
            line.addExtra(homeBtn);

            index++;
            if (index < homes.size()) {
                line.addExtra(new TextComponent("§7, "));
            }
        }
        player.spigot().sendMessage(line);
        player.sendMessage(Lang.get("home.list.footer"));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.home")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();

            if (sender.hasPermission("backbone.home.blacklist") && "blacklistworld".startsWith(current)) {
                completions.add("blacklistworld");
            }

            if (sender instanceof Player player) {
                for (String h : HomeManager.getHomes(player.getUniqueId())) {
                    if (h.toLowerCase().startsWith(current)) {
                        completions.add(h);
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("blacklistworld")) {
            if (sender.hasPermission("backbone.home.blacklist")) {
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
