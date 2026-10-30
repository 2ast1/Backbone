package de.basti.backbone.util.message;

import de.basti.backbone.Backbone;
import de.basti.backbone.util.scoreboard.ScoreboardParser;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet zeitgesteuerte und permanente Actionbar-Nachrichten (Broadcast & Einzelspieler).
 * Sendet Actionbar-Nachrichten in regelmäßigen Intervallen (alle 20 Ticks / 1 Sekunde),
 * damit sie nicht nach 2 Sekunden durch die Minecraft-Client-Engine ausgeblendet werden.
 */
public final class ActionbarManager {

    private static final Map<UUID, ActionbarSession> playerSessions = new ConcurrentHashMap<>();
    private static ActionbarSession broadcastSession = null;

    private static class ActionbarSession {
        final String rawText;
        final long expiryTimestamp; // 0 = permanent
        BukkitTask task;

        ActionbarSession(String rawText, long expiryTimestamp) {
            this.rawText = rawText;
            this.expiryTimestamp = expiryTimestamp;
        }

        boolean isExpired() {
            return expiryTimestamp > 0 && System.currentTimeMillis() >= expiryTimestamp;
        }

        void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private ActionbarManager() {}

    /**
     * Startet eine Actionbar-Nachricht an alle Online-Spieler.
     *
     * @param rawText        Nachricht mit Farbcodes und Platzhaltern
     * @param durationMillis Dauer in Millisekunden (0 für permanent)
     */
    public static synchronized void sendBroadcast(String rawText, long durationMillis) {
        stopBroadcast();

        Backbone plugin = Backbone.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            sendBroadcastPacket(rawText);
            return;
        }

        long expiry = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0;
        ActionbarSession session = new ActionbarSession(rawText, expiry);

        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.isExpired()) {
                stopBroadcast();
                return;
            }
            sendBroadcastPacket(rawText);
        }, 0L, 20L);

        broadcastSession = session;
    }

    private static void sendBroadcastPacket(String rawText) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendPacket(p, rawText);
        }
    }

    /**
     * Startet eine Actionbar-Nachricht an einen einzelnen Spieler.
     *
     * @param target         Ziel-Spieler
     * @param rawText        Nachricht mit Farbcodes und Platzhaltern
     * @param durationMillis Dauer in Millisekunden (0 für permanent)
     */
    public static synchronized void sendPlayer(Player target, String rawText, long durationMillis) {
        if (target == null) return;
        UUID uuid = target.getUniqueId();
        stopPlayer(uuid);

        Backbone plugin = Backbone.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            sendPacket(target, rawText);
            return;
        }

        long expiry = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0;
        ActionbarSession session = new ActionbarSession(rawText, expiry);

        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || session.isExpired()) {
                stopPlayer(uuid);
                return;
            }
            sendPacket(p, rawText);
        }, 0L, 20L);

        playerSessions.put(uuid, session);
    }

    private static void sendPacket(Player player, String rawText) {
        if (player == null || !player.isOnline()) return;
        try {
            String parsed = ScoreboardParser.parse(rawText, player);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(parsed));
        } catch (Throwable ignored) {}
    }

    public static synchronized void stopBroadcast() {
        if (broadcastSession != null) {
            broadcastSession.cancel();
            broadcastSession = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                } catch (Throwable ignored) {}
            }
        }
    }

    public static synchronized void stopPlayer(UUID uuid) {
        if (uuid == null) return;
        ActionbarSession session = playerSessions.remove(uuid);
        if (session != null) {
            session.cancel();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                try {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                } catch (Throwable ignored) {}
            }
        }
    }

    public static synchronized void shutdown() {
        stopBroadcast();
        for (UUID uuid : playerSessions.keySet()) {
            ActionbarSession s = playerSessions.remove(uuid);
            if (s != null) {
                s.cancel();
            }
        }
        playerSessions.clear();
    }
}
