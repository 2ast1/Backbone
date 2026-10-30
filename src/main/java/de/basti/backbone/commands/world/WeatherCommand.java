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
 * Commands for weather control (/sun, /rain, /storm).
 * Permission: backbone.weather
 */
public class WeatherCommand implements CommandExecutor, TabCompleter {

    public enum WeatherType {
        SUN("weather.type.sun", false, false),
        RAIN("weather.type.rain", true, false),
        STORM("weather.type.storm", true, true);

        private final String langKey;
        private final boolean storm;
        private final boolean thunder;

        WeatherType(String langKey, boolean storm, boolean thunder) {
            this.langKey = langKey;
            this.storm = storm;
            this.thunder = thunder;
        }

        public String getDisplayName() {
            return Lang.get(langKey);
        }

        public String getGermanName() {
            return getDisplayName();
        }

        public boolean isStorm() {
            return storm;
        }

        public boolean isThunder() {
            return thunder;
        }
    }

    private final WeatherType defaultType;

    public WeatherCommand(WeatherType defaultType) {
        this.defaultType = defaultType;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.weather") && !sender.hasPermission("backbone." + command.getName().toLowerCase())) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.weather"));
            return true;
        }

        WeatherType type = defaultType;
        if (type == null) {
            String name = command.getName().toLowerCase();
            type = switch (name) {
                case "rain" -> WeatherType.RAIN;
                case "storm", "thunder" -> WeatherType.STORM;
                default -> WeatherType.SUN;
            };
        }

        if (sender instanceof Player player) {
            applyWeather(player.getWorld(), type);
            player.sendMessage(Lang.getPrefix() + Lang.get("weather.set-world", "weather", type.getDisplayName()));
        } else {
            for (World world : Bukkit.getWorlds()) {
                applyWeather(world, type);
            }
            sender.sendMessage(Lang.getPrefix() + Lang.get("weather.set-all", "weather", type.getDisplayName()));
        }

        return true;
    }

    private void applyWeather(World world, WeatherType type) {
        world.setStorm(type.isStorm());
        world.setThundering(type.isThunder());
        if (!type.isStorm()) {
            world.setWeatherDuration(12000);
            world.setThunderDuration(12000);
        } else {
            world.setWeatherDuration(6000);
            if (type.isThunder()) {
                world.setThunderDuration(6000);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return List.of();
    }
}
