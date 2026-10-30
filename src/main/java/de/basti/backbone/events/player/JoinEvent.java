package de.basti.backbone.events.player;

import de.basti.backbone.Backbone;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.scoreboard.ScoreboardParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Event-Klasse für das Beitreten von Spielern (PlayerJoinEvent).
 * Verwaltet Namens-Caching, Offline-Inventar-Synchronisation, Rang-Berechtigungen,
 * Tabliste, benutzerdefinierte Join-Nachrichten und den Erststart-Setup-Hinweis für OPs.
 */
public class JoinEvent implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Namen in playerdata.yml sichern/aktualisieren
        PlayerResolver.recordPlayer(player);

        // 2. Falls ein Admin gerade das Offline-Inventar/Enderchest dieses Spielers offen hat,
        // zuerst schließen (damit eventuelle Offline-Änderungen vor dem Laden gespeichert werden)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || online.getOpenInventory() == null) continue;
            org.bukkit.inventory.Inventory top = online.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof de.basti.backbone.commands.inventory.InventoryViewSession session
                    && player.getUniqueId().equals(session.getTargetUuid())) {
                online.closeInventory();
            } else if (top != null && top.getHolder() instanceof de.basti.backbone.commands.inventory.EnderchestViewSession ecSession
                    && player.getUniqueId().equals(ecSession.getTargetUuid())) {
                online.closeInventory();
            }
        }

        // Offline-Bearbeitungen am Inventar / Enderchest sofort anwenden und aktuelles Inventar sichern
        OfflineInventoryManager.applyPendingChanges(player);
        OfflineInventoryManager.savePlayer(player);

        // 3. Rang-Berechtigungen synchron anwenden (falls Rangsystem aktiv)
        RankManager.checkAndHandleExpiration(player.getUniqueId(), false);
        if (RankManager.getPermissionService() != null) {
            RankManager.getPermissionService().applyPermissions(player);
        }

        // 4. Unsichtbarkeits-Status (/hide) und Tabliste sofort aktualisieren
        de.basti.backbone.util.HideManager.updateAllVisibilities();
        PrefixManager.updateTabList(player);

        // 5. Benutzerdefinierte Join-Nachricht (server.join in config.yml)
        boolean hiddenPlayer = de.basti.backbone.util.HideManager.isHidden(player);
        String joinTemplate = DataManager.getConfigString("server.join");
        if (hiddenPlayer) {
            event.setJoinMessage(null);
            if (joinTemplate != null && !joinTemplate.isBlank()) {
                String formattedJoin = ScoreboardParser.formatJoinLeaveMessage(joinTemplate, player);
                Bukkit.getConsoleSender().sendMessage(formattedJoin);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!SetupWizardManager.isInSetup(online) && de.basti.backbone.util.HideManager.canSee(online, player)) {
                        online.sendMessage(formattedJoin);
                    }
                }
            }
        } else if (joinTemplate != null && !joinTemplate.isBlank()) {
            String formattedJoin = ScoreboardParser.formatJoinLeaveMessage(joinTemplate, player);
            if (SetupWizardManager.hasActiveSessions()) {
                event.setJoinMessage(null);
                Bukkit.getConsoleSender().sendMessage(formattedJoin);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!SetupWizardManager.isInSetup(online)) {
                        online.sendMessage(formattedJoin);
                    }
                }
            } else {
                event.setJoinMessage(formattedJoin);
            }
        }

        // 6. Verzögerte Aktualisierung nach 1 und 5 Ticks + OP Setup-Reminder nach 20 Ticks
        Backbone plugin = Backbone.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    PrefixManager.updateTabList(player);
                }
            }, 1L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    PrefixManager.updateTabList(player);
                }
            }, 5L);

            if ((player.isOp() || player.hasPermission("backbone.setup"))
                    && !DataManager.getConfigBoolean("server.backbone.setup-completed")) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !DataManager.getConfigBoolean("server.backbone.setup-completed")) {
                        sendOpSetupReminder(player);
                    }
                }, 20L);
            }
        }
    }

    private void sendOpSetupReminder(Player player) {
        player.sendMessage(Lang.get("setup.op-reminder.header"));
        player.sendMessage(Lang.get("setup.op-reminder.line1"));
        player.sendMessage(Lang.get("setup.op-reminder.line2"));

        TextComponent startBtn = new TextComponent(Lang.get("setup.op-reminder.btn-start"));
        startBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/backbone setup"));
        startBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("setup.op-reminder.hover-start"))));

        TextComponent space = new TextComponent("   ");

        TextComponent ignoreBtn = new TextComponent(Lang.get("setup.op-reminder.btn-ignore"));
        ignoreBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/backbone setup ignore"));
        ignoreBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("setup.op-reminder.hover-ignore"))));

        TextComponent row = new TextComponent("");
        row.addExtra(startBtn);
        row.addExtra(space);
        row.addExtra(ignoreBtn);
        player.spigot().sendMessage(row);

        player.sendMessage(Lang.get("setup.op-reminder.footer"));
    }
}
