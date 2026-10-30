package de.basti.backbone.util.message;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.automod.chat.ChatMod;
import de.basti.backbone.util.automod.message.AutoModMessages;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages direct private messages (/msg, /reply) between players and console.
 * Thread-safe with mute, automod, vanish, and setup checks.
 */
public final class PrivateMessageManager {

    public static final UUID CONSOLE_UUID = new UUID(0, 0);
    private static final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();

    private PrivateMessageManager() {}

    public static boolean isMessagingEnabled() {
        return DataManager.getConfig().getBoolean("chat.private-messages", true);
    }

    public static void setMessagingEnabled(boolean enabled) {
        DataManager.setConfig("chat.private-messages", enabled);
    }

    public static void setReplyTarget(UUID sender, UUID target) {
        if (sender == null || target == null) return;
        replyTargets.put(sender, target);
    }

    public static UUID getReplyTarget(UUID sender) {
        if (sender == null) return null;
        return replyTargets.get(sender);
    }

    public static void removeReplyTarget(UUID player) {
        if (player == null) return;
        replyTargets.remove(player);
        replyTargets.values().removeIf(target -> target.equals(player));
    }

    public static void clearAll() {
        replyTargets.clear();
    }

    public static boolean sendMessage(CommandSender sender, CommandSender target, String message) {
        if (sender == null || target == null || message == null || message.isBlank()) {
            return false;
        }

        if (!isMessagingEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("msg.disabled"));
            return false;
        }

        if (sender.equals(target)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("msg.self"));
            return false;
        }

        // Mute check
        if (sender instanceof Player player) {
            if (MuteUtil.isMuted(player.getUniqueId())) {
                long until = MuteUtil.getUntil(player.getUniqueId());
                String reason = MuteUtil.getReason(player.getUniqueId());
                String by = MuteUtil.getMutedBy(player.getUniqueId());
                player.sendMessage(AutoModMessages.getChatMutedMessage(player.getName(), reason, by, until));
                return false;
            }

            // AutoMod check
            boolean blocked = ChatMod.executeChatMod(message, player);
            if (blocked) {
                return false;
            }
        }

        // Setup wizard & mute checks
        if (target instanceof Player targetPlayer) {
            if (SetupWizardManager.isInSetup(targetPlayer)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("msg.target-in-setup"));
                return false;
            }

            if (MuteUtil.isMuted(targetPlayer.getUniqueId())) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("msg.target-muted"));
                return false;
            }

            if (sender instanceof Player senderPlayer && !HideManager.canSee(senderPlayer, targetPlayer)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetPlayer.getName()));
                return false;
            }
        }

        String senderDisplayName = getDisplayName(sender);
        String targetDisplayName = getDisplayName(target);

        // Format and send
        String sendMsg = Lang.get("msg.format-sender", "receiver", targetDisplayName, "message", message);
        String receiveMsg = Lang.get("msg.format-receiver", "sender", senderDisplayName, "message", message);

        sender.sendMessage(sendMsg);
        target.sendMessage(receiveMsg);

        // Update reply targets
        UUID senderUuid = getSenderUuid(sender);
        UUID targetUuid = getSenderUuid(target);
        if (senderUuid != null && targetUuid != null) {
            setReplyTarget(senderUuid, targetUuid);
            setReplyTarget(targetUuid, senderUuid);
        }

        // Console spy if neither is console
        if (!(sender instanceof ConsoleCommandSender) && !(target instanceof ConsoleCommandSender)) {
            try {
                if (Bukkit.getServer() != null && Bukkit.getConsoleSender() != null) {
                    Bukkit.getConsoleSender().sendMessage(Lang.get("msg.console-spy",
                            "sender", sender.getName(),
                            "receiver", target.getName(),
                            "message", message));
                }
            } catch (Throwable ignored) {}
        }

        return true;
    }

    public static UUID getSenderUuid(CommandSender sender) {
        if (sender instanceof Player p) {
            return p.getUniqueId();
        }
        if (sender instanceof ConsoleCommandSender) {
            return CONSOLE_UUID;
        }
        return null;
    }

    public static String getDisplayName(CommandSender sender) {
        if (sender instanceof Player p) {
            return PrefixManager.formatPlayerName(p);
        }
        return Lang.get("msg.console-name");
    }
}
