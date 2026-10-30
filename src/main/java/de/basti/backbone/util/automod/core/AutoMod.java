package de.basti.backbone.util.automod.core;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.DataManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.List;

public class AutoMod {

    public static void initialize() {
        setupFiles();
        Backbone.infoMsg("AutoMod is " + (isActive() ? "enabled and armed" : "not enabled and is inactive"));
    }

    private static void setupFiles() {
        File configFile = DataManager.getFile("automod", "config.yml");
        boolean isNewConfig = !configFile.exists();
        FileConfiguration automodConfig = DataManager.getConfiguration("automod","config.yml");

        automodConfig.addDefault("settings.enabled", true);
        automodConfig.addDefault("settings.prefix", "&9[AutoMod]");
        automodConfig.addDefault("settings.chat.scan", true);

        automodConfig.addDefault("settings.chat.block-domains", true);
        automodConfig.addDefault("settings.chat.block-ips", true);
        automodConfig.addDefault("settings.chat.block-discord-invites", true);

        automodConfig.addDefault("settings.chat.strikes.1.action", "WARN");
        automodConfig.addDefault("settings.chat.strikes.3.action", "MUTE");
        automodConfig.addDefault("settings.chat.strikes.5.action", "KICK");
        automodConfig.addDefault("settings.chat.strikes.8.action", "BAN");

        // Autonomous AutoMod messages (in English by default, with {automod_prefix})
        automodConfig.addDefault("messages.automod.warn", "{automod_prefix} &rWarning: This message was flagged by AutoMod. &7Reason: &e{reason}");
        automodConfig.addDefault("messages.automod.mute", "{automod_prefix} &cYou were muted for {until} due to repeated violations! &7Reason: &e{reason}");
        automodConfig.addDefault("messages.automod.kick", "{automod_prefix}\n&cYou were kicked by AutoMod!\n&7Reason: &e{reason}");
        automodConfig.addDefault("messages.automod.ban", "{automod_prefix}\n\n&c&lYou have been banned from the server!\n\n&7Reason: &e{reason}\n&7Remaining time: &c{until}");

        // Manual sanctions by staff / players
        automodConfig.addDefault("messages.manual.warn", "&cYou were warned by &e{operator}&c! &7Reason: &e{reason}");
        automodConfig.addDefault("messages.manual.mute", "&cYou were muted by &e{operator} &cfor {until}! &7Reason: &e{reason}");
        automodConfig.addDefault("messages.manual.kick", "&cYou were kicked by &e{operator}&c!\n&7Reason: &e{reason}");
        automodConfig.addDefault("messages.manual.ban", "&c&lYou have been banned from the server!\n\n&7Reason: &e{reason}\n&7Banned by: &e{operator}\n&7Remaining time: &c{until}");

        // Message shown when a muted player tries to chat
        automodConfig.addDefault("messages.chat-muted", "{automod_prefix} &cYou are currently muted. &7({until})");

        automodConfig.options().copyDefaults(true);
        if (isNewConfig || configFile.length() == 0) {
            DataManager.saveConfiguration("automod","config.yml",automodConfig);
        }

        //Böse Wörter Liste
        File wordsFile = DataManager.getFile("automod", "badwords.yml");
        boolean isNewWords = !wordsFile.exists();
        FileConfiguration wordsConfig = DataManager.getConfiguration("automod", "badwords.yml");
        wordsConfig.addDefault("blocked-words", List.of(
                // Deutsch
                "hurensohn",
                "hs",
                "bastard",
                "arschloch",
                "wichser",
                "fotze",
                "schlampe",
                "hure",
                "nutte",
                "missgeburt",
                "spast",
                "spasti",
                "behindert",
                "schwuchtel",
                "kanake",
                // Englisch / Gaming
                "fuck",
                "bitch",
                "asshole",
                "motherfucker",
                "retard",
                "cunt",
                "whore",
                "slut",
                "kys"
        ));
        wordsConfig.options().copyDefaults(true);
        if (isNewWords || wordsFile.length() == 0) {
            DataManager.saveConfiguration("automod", "badwords.yml", wordsConfig);
        }
    }

    public static boolean isActive() {
        return DataManager.getBoolean("automod","config.yml","settings.enabled");
    }
    public static boolean isChatActive() {
        return DataManager.getBoolean("automod","config.yml","settings.chat.scan");
    }
    public static boolean areDomainsAllowed() {
        return !DataManager.getBoolean("automod", "config.yml", "settings.chat.block-domains");
    }
    public static boolean areIpsAllowed() {
        return !DataManager.getBoolean("automod", "config.yml", "settings.chat.block-ips");
    }
    public static boolean areDiscordInvitesAllowed() {
        return !DataManager.getBoolean("automod", "config.yml", "settings.chat.block-discord-invites");
    }
    public static String getPrefix() {
        String raw = DataManager.getString("automod", "config.yml", "settings.prefix");
        return de.basti.backbone.util.PrefixManager.formatColors(raw != null ? raw : "&9[AutoMod]");
    }

    public static List<String> getBlockedWords() {
        return DataManager.getStringList("automod", "badwords.yml", "blocked-words");
    }

}
