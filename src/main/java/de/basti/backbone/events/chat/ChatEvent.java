package de.basti.backbone.events.chat;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.automod.chat.ChatMod;
import de.basti.backbone.util.automod.message.AutoModMessages;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Event-Klasse für den Server-Chat (AsyncPlayerChatEvent).
 * Verwaltet Setup-Wizard-Eingaben, Mutes, AutoMod-Filter und Chat-Formatierung.
 */
public class ChatEvent implements Listener {

    private static final java.util.Set<String> MUTED_BLOCKED_COMMANDS = java.util.Set.of(
            "msg", "tell", "w", "whisper", "m", "pm", "dm", "reply", "r", "me", "say", "tm", "teammsg"
    );

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!MuteUtil.isMuted(p)) {
            return;
        }

        String message = e.getMessage().trim();
        if (!message.startsWith("/")) return;

        String rawCmd = message.substring(1).split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        if (rawCmd.contains(":")) {
            rawCmd = rawCmd.substring(rawCmd.indexOf(':') + 1);
        }

        if (MUTED_BLOCKED_COMMANDS.contains(rawCmd)) {
            e.setCancelled(true);
            long until = MuteUtil.getUntil(p.getUniqueId());
            String reason = MuteUtil.getReason(p.getUniqueId());
            String by = MuteUtil.getMutedBy(p.getUniqueId());
            p.sendMessage(AutoModMessages.getChatMutedMessage(p.getName(), reason, by, until));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String rawMessage = e.getMessage();

        // 1. Falls der Spieler sich im Setup-Wizard befindet: Chat stoppen und Eingabe abfangen
        if (SetupWizardManager.isInSetup(p)) {
            e.setCancelled(true);
            Backbone plugin = Backbone.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> SetupWizardManager.handleInput(p, rawMessage));
            } else {
                SetupWizardManager.handleInput(p, rawMessage);
            }
            return;
        }

        // 2. Spieler im Setup-Wizard sollen keine normalen Chat-Nachrichten anderer Spieler empfangen
        if (SetupWizardManager.hasActiveSessions()) {
            e.getRecipients().removeIf(SetupWizardManager::isInSetup);
        }

        // 3. Slowchat-Prüfung
        if (de.basti.backbone.util.chat.SlowchatManager.isSlowchatEnabled()) {
            if (!p.hasPermission("backbone.slowchat.bypass") && !p.hasPermission("backbone.admin") && !p.isOp()) {
                long remainingMillis = de.basti.backbone.util.chat.SlowchatManager.getPlayerRemainingCooldownMillis(p.getUniqueId());
                if (remainingMillis > 0) {
                    e.setCancelled(true);
                    int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
                    p.sendMessage(de.basti.backbone.util.Lang.getPrefix() + de.basti.backbone.util.Lang.get("slowchat.cooldown-message", "seconds", String.valueOf(remainingSeconds)));
                    return;
                }
            }
        }

        if (moderation(rawMessage, p)) {
            e.setCancelled(true);
            return;
        }

        de.basti.backbone.util.chat.SlowchatManager.recordChat(p.getUniqueId());

        renderMessage(e);
    }

    private boolean moderation(String rawMessage, Player p) {
        if (MuteUtil.isMuted(p)) {
            long until = MuteUtil.getUntil(p.getUniqueId());
            String reason = MuteUtil.getReason(p.getUniqueId());
            String by = MuteUtil.getMutedBy(p.getUniqueId());
            p.sendMessage(AutoModMessages.getChatMutedMessage(p.getName(), reason, by, until));
            return true;
        }

        return ChatMod.executeChatMod(rawMessage, p);
    }

    private void renderMessage(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        LocalTime now = LocalTime.now();
        String chatFormat = DataManager.getConfigString("chat.format");

        if (chatFormat == null) {
            chatFormat = "{playername} | {msg}";
        }

        boolean chatColorful = DataManager.getConfigBoolean("chat.color");
        if (chatColorful && p.hasPermission("backbone.chat.color")) {
            e.setMessage(de.basti.backbone.util.PrefixManager.formatColors(e.getMessage()));
        }

        String playerWorldName = p.getWorld().getName();
        String playerWorldType = p.getWorld().getEnvironment().name();
        String playerUUID = p.getUniqueId().toString();

        String hours = now.format(DateTimeFormatter.ofPattern("HH"));
        String minutes = now.format(DateTimeFormatter.ofPattern("mm"));
        String seconds = now.format(DateTimeFormatter.ofPattern("ss"));

        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        String version = Bukkit.getServer().getVersion();

        String rawPluginPrefix = de.basti.backbone.util.Lang.getPrefix();
        String pluginPrefixFull = (rawPluginPrefix != null && !rawPluginPrefix.isEmpty()) ? rawPluginPrefix : "§9[Backbone] ";
        if (!pluginPrefixFull.endsWith(" ")) {
            pluginPrefixFull = pluginPrefixFull + " ";
        }
        String pluginPrefixTrimmed = pluginPrefixFull.trim();
        String playerPrefix = de.basti.backbone.util.PrefixManager.getPrefix(p);
        if (playerPrefix == null || playerPrefix.isEmpty()) {
            playerPrefix = de.basti.backbone.util.vault.VaultManager.getExternalPlayerPrefix(p);
        }
        String nameColor = de.basti.backbone.util.PrefixManager.getFormattedNameColor(p);
        boolean ranksEnabled = de.basti.backbone.ranks.RankManager.isRanksEnabled();
        de.basti.backbone.ranks.Rank rank = ranksEnabled ? de.basti.backbone.ranks.RankManager.getRankOfPlayer(p) : null;
        boolean useBackboneRank = rank != null && ranksEnabled;
        String rankName = (useBackboneRank && rank.getName() != null) ? rank.getName() : "";
        if (rankName.isEmpty()) {
            rankName = de.basti.backbone.util.vault.VaultManager.getExternalRankName(p);
        }
        String rankPrefix = (useBackboneRank && rank.getPrefix() != null) ? de.basti.backbone.util.PrefixManager.formatColors(rank.getPrefix()) : "";
        if (rankPrefix.isEmpty()) {
            rankPrefix = de.basti.backbone.util.vault.VaultManager.getExternalRankPrefix(p);
        }

        chatFormat = de.basti.backbone.util.PrefixManager.formatColors(chatFormat)
                .replace("%", "%%")
                .replace("{pluginPrefix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{pluginPrefix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{plugin_prefix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{plugin_prefix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{plugin-prefix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{plugin-prefix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{Plugin-Prefix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{Plugin-Prefix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{Plugin-Präfix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{Plugin-Präfix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{plugin-präfix} ", pluginPrefixFull.replace("%", "%%"))
                .replace("{plugin-präfix}", pluginPrefixTrimmed.replace("%", "%%"))
                .replace("{playerPrefix}", (playerPrefix != null ? playerPrefix : "").replace("%", "%%"))
                .replace("{prefix}", (playerPrefix != null ? playerPrefix : "").replace("%", "%%"))
                .replace("{rank}", rankName.replace("%", "%%"))
                .replace("{rankPrefix}", rankPrefix.replace("%", "%%"))
                .replace("{nameColor}", (nameColor != null ? nameColor : "").replace("%", "%%"))
                .replace("{status} ", "")
                .replace("{status}", "")
                .replace("{world}", playerWorldName.replace("%", "%%"))
                .replace("{worldtype}", playerWorldType.replace("%", "%%"))
                .replace("{playeruuid}", playerUUID)
                .replace("{hh}", hours)
                .replace("{mm}", minutes)
                .replace("{ss}", seconds)
                .replace("{bukkitversion}", bukkitVersion.replace("%", "%%"))
                .replace("{version}", version.replace("%", "%%"))
                .replace("{playername}", "%1$s§r")
                .replace("{msg}", "%2$s")
                .replace("{message}", "%2$s");

        e.setFormat(chatFormat);
    }
}
