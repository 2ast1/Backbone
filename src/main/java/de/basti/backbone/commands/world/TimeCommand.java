package de.basti.backbone.commands.world;

import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Commands for time control (/day, /night, /sunrise, /sunset).
 * Permission: backbone.time
 */
public class TimeCommand implements CommandExecutor, TabCompleter {

    public enum TimeType {
        DAY(1000, "time.type.day"),
        NIGHT(13000, "time.type.night"),
        SUNRISE(23000, "time.type.sunrise"),
        SUNSET(12000, "time.type.sunset");

        private final long timeTicks;
        private final String langKey;

        TimeType(long timeTicks, String langKey) {
            this.timeTicks = timeTicks;
            this.langKey = langKey;
        }

        public long getTimeTicks() {
            return timeTicks;
        }

        public String getDisplayName() {
            return Lang.get(langKey);
        }

        public String getGermanName() {
            return getDisplayName();
        }
    }

    private final TimeType defaultType;

    public TimeCommand(TimeType defaultType) {
        this.defaultType = defaultType;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.time") && !sender.hasPermission("backbone." + command.getName().toLowerCase())) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.time"));
            return true;
        }

        TimeType type = defaultType;
        if (type == null) {
            String name = command.getName().toLowerCase();
            type = switch (name) {
                case "night" -> TimeType.NIGHT;
                case "sunrise" -> TimeType.SUNRISE;
                case "sunset" -> TimeType.SUNSET;
                default -> TimeType.DAY;
            };
        }

        if (sender instanceof Player player) {
            World world = player.getWorld();
            try {
                world.setTime(type.getTimeTicks());
                player.sendMessage(Lang.getPrefix() + Lang.get("time.set-world", "time", type.getDisplayName()));
            } catch (Throwable t) {
                player.sendMessage(Lang.getPrefix() + Lang.get("time.invalid-world"));
            }
        } else {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    try {
                        world.setTime(type.getTimeTicks());
                    } catch (Throwable ignored) {}
                }
            }
            sender.sendMessage(Lang.getPrefix() + Lang.get("time.set-all", "time", type.getDisplayName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return List.of();
    }
}
