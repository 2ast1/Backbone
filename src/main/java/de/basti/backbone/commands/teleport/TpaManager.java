package de.basti.backbone.commands.teleport;

import de.basti.backbone.util.Lang;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all pending TPA teleport requests (/tpa, /tpaccept, /tpdeny).
 */
public class TpaManager {

    private static final Map<UUID, TpaRequest> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Sends a teleport request from one player to another.
     */
    public static void sendRequest(Player requester, Player target) {
        if (requester.equals(target)) {
            requester.sendMessage(Lang.getPrefix() + Lang.get("tpa.self-error"));
            return;
        }

        UUID targetUuid = target.getUniqueId();
        TpaRequest request = new TpaRequest(requester.getUniqueId(), requester.getName(), targetUuid, target.getName());
        pendingRequests.put(targetUuid, request);

        // Confirmation to requester
        requester.sendMessage(Lang.getPrefix() + Lang.get("tpa.sent", "player", target.getName()));

        // Interactive message to target
        target.sendMessage(Lang.get("tpa.request.header"));
        target.sendMessage(Lang.get("tpa.request.line1", "player", requester.getName()));
        target.sendMessage(Lang.get("tpa.request.line2"));

        TextComponent acceptBtn = new TextComponent(Lang.get("tpa.request.accept-btn"));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa accept"));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("tpa.request.accept-hover"))));

        TextComponent space = new TextComponent("     ");

        TextComponent denyBtn = new TextComponent(Lang.get("tpa.request.deny-btn"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa deny"));
        denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("tpa.request.deny-hover"))));

        TextComponent buttonRow = new TextComponent("");
        buttonRow.addExtra(acceptBtn);
        buttonRow.addExtra(space);
        buttonRow.addExtra(denyBtn);

        target.spigot().sendMessage(buttonRow);
        target.sendMessage(Lang.get("tpa.request.footer"));
    }

    /**
     * Accepts a pending teleport request.
     */
    public static void acceptRequest(Player target) {
        UUID targetUuid = target.getUniqueId();
        TpaRequest request = pendingRequests.get(targetUuid);

        if (request == null || request.isExpired()) {
            pendingRequests.remove(targetUuid);
            target.sendMessage(Lang.getPrefix() + Lang.get("tpa.no-pending"));
            return;
        }

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester == null || !requester.isOnline()) {
            pendingRequests.remove(targetUuid);
            target.sendMessage(Lang.getPrefix() + Lang.get("tpa.requester-offline"));
            return;
        }

        pendingRequests.remove(targetUuid);

        requester.teleport(target.getLocation());

        target.sendMessage(Lang.getPrefix() + Lang.get("tpa.accepted-target", "player", requester.getName()));
        requester.sendMessage(Lang.getPrefix() + Lang.get("tpa.accepted-requester", "player", target.getName()));
    }

    /**
     * Denies a pending teleport request.
     */
    public static void denyRequest(Player target) {
        UUID targetUuid = target.getUniqueId();
        TpaRequest request = pendingRequests.remove(targetUuid);

        if (request == null || request.isExpired()) {
            target.sendMessage(Lang.getPrefix() + Lang.get("tpa.no-pending"));
            return;
        }

        target.sendMessage(Lang.getPrefix() + Lang.get("tpa.denied-target", "player", request.getRequesterName()));

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Lang.getPrefix() + Lang.get("tpa.denied-requester", "player", target.getName()));
        }
    }

    /**
     * Removes all expired teleport requests from RAM and returns the number of removed requests.
     */
    public static int cleanupExpired() {
        int removed = 0;
        var iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() == null || entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public static int getPendingRequestCount() {
        cleanupExpired();
        return pendingRequests.size();
    }
}
