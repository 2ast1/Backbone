package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet das dynamische Registrieren und Deaktivieren (Unregistrieren) von Plugin-Befehlen.
 * Deaktivierte Befehle werden aus der Bukkit CommandMap sowie aus dem Brigadier-Baum
 * (PlayerCommandSendEvent) entfernt, sodass sie im Client rot markiert werden.
 */
public class CommandRegistryManager implements Listener {

    private static Backbone plugin;
    private static final Map<String, PluginCommand> cachedCommands = new ConcurrentHashMap<>();
    private static final Set<String> disabledLabels = ConcurrentHashMap.newKeySet();
    private static BukkitTask pendingSyncTask = null;

    private static final Map<String, List<String>> MODULE_COMMANDS = new LinkedHashMap<>();

    static {
        MODULE_COMMANDS.put("scoreboard.enabled", List.of("scoreboard"));
        MODULE_COMMANDS.put("ranks.enabled", List.of("rank", "setrank"));
        MODULE_COMMANDS.put("prefix.enabled", List.of("prefix", "setprefix", "removeprefix"));
        MODULE_COMMANDS.put("namecolor.enabled", List.of("namecolor", "setnamecolor", "removenamecolor"));
        MODULE_COMMANDS.put("chat.private-messages", List.of("msg", "reply"));
        MODULE_COMMANDS.put("essentials.enabled", List.of(
                "inventory", "enderchest", "craft", "trash",
                "heal", "feed", "speed", "skull", "back",
                "fly", "hide", "gamemode", "creative", "survival", "adventure", "spectator",
                "day", "night", "sunrise", "sunset",
                "sun", "rain", "storm",
                "tpa", "tpaccept", "tpdeny"
        ));
        MODULE_COMMANDS.put("economy.enabled", List.of("money", "pay", "baltop", "paytoggle"));
        MODULE_COMMANDS.put("warps.enabled", List.of("warp", "warps", "setwarp", "delwarp"));
        MODULE_COMMANDS.put("homes.enabled", List.of("home", "homes", "sethome", "delhome"));
        MODULE_COMMANDS.put("lobby.enabled", List.of("lobby", "setlobby"));
        MODULE_COMMANDS.put("spawn.enabled", List.of("spawn", "setworldspawn"));
    }

    public static void init(Backbone backboneInstance) {
        plugin = backboneInstance;
        cacheAllCommands();
        syncCommands();
    }

    private static void cacheAllCommands() {
        if (plugin == null) return;
        for (List<String> cmdNames : MODULE_COMMANDS.values()) {
            for (String cmdName : cmdNames) {
                PluginCommand pc = plugin.getCommand(cmdName);
                if (pc != null) {
                    cachedCommands.put(cmdName.toLowerCase(Locale.ROOT), pc);
                }
            }
        }
    }

    /**
     * Synchronisiert alle registrierten bzw. deaktivierten Befehle anhand der aktuellen config.yml
     * und aktualisiert den Befehlsbaum für alle Online-Spieler.
     * <p>
     * Wird automatisch debounced (1 Server-Tick Verzögerung), um Race Conditions und
     * {@link java.util.ConcurrentModificationException} im asynchronen Command-Builder
     * moderner Paper-Server ("Paper Async Command Builder Thread Pool") sicher zu verhindern.
     */
    public static synchronized void syncCommands() {
        if (plugin == null) return;

        // Falls Plugin noch nicht vollständig aktiviert ist oder in Unit-Tests (kein Scheduler):
        if (!plugin.isEnabled()) {
            executeSync();
            return;
        }

        // Falls bereits ein Sync für den nächsten Tick geplant ist, diesen abwarten
        if (pendingSyncTask != null) {
            return;
        }

        try {
            pendingSyncTask = Bukkit.getScheduler().runTask(plugin, () -> {
                pendingSyncTask = null;
                executeSync();
            });
        } catch (Throwable t) {
            // Fallback (z.B. MockBukkit / Unit-Tests / Disable-Phase):
            executeSync();
        }
    }

    /**
     * Beendet eventuell geplante asynchrone Sync-Tasks beim Plugin-Shutdown.
     */
    public static synchronized void shutdown() {
        if (pendingSyncTask != null) {
            try {
                pendingSyncTask.cancel();
            } catch (Throwable ignored) {}
            pendingSyncTask = null;
        }
    }

    /**
     * Führt die eigentliche Registrierung / Deregistrierung der Befehle synchron auf dem Hauptthread durch.
     */
    public static synchronized void executeSync() {
        if (plugin == null) return;
        if (cachedCommands.isEmpty()) {
            cacheAllCommands();
        }

        disabledLabels.clear();
        CommandMap commandMap = getCommandMap();
        Map<String, Command> knownCommands = getKnownCommands(commandMap);
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, List<String>> entry : MODULE_COMMANDS.entrySet()) {
            String configKey = entry.getKey();
            boolean defaultVal = !configKey.equals("ranks.enabled");
            boolean enabled = DataManager.getConfig().contains(configKey)
                    ? DataManager.getConfigBoolean(configKey)
                    : defaultVal;

            for (String cmdName : entry.getValue()) {
                PluginCommand pc = cachedCommands.get(cmdName.toLowerCase(Locale.ROOT));
                if (pc == null) {
                    pc = plugin.getCommand(cmdName);
                    if (pc != null) {
                        cachedCommands.put(cmdName.toLowerCase(Locale.ROOT), pc);
                    }
                }
                if (pc == null) continue;

                List<String> allLabels = new ArrayList<>();
                allLabels.add(pc.getName().toLowerCase(Locale.ROOT));
                for (String alias : pc.getAliases()) {
                    allLabels.add(alias.toLowerCase(Locale.ROOT));
                }

                if (enabled) {
                    if (knownCommands != null && commandMap != null) {
                        try {
                            pc.register(commandMap);
                        } catch (Throwable ignored) {}
                        for (String label : allLabels) {
                            knownCommands.put(label, pc);
                            knownCommands.put(fallbackPrefix + ":" + label, pc);
                        }
                    }
                } else {
                    for (String label : allLabels) {
                        disabledLabels.add(label);
                        disabledLabels.add(fallbackPrefix + ":" + label);
                        if (knownCommands != null) {
                            knownCommands.remove(label, pc);
                            knownCommands.remove(fallbackPrefix + ":" + label);
                        }
                    }
                }
            }
        }

        // CraftServer.syncCommands() aufrufen, falls vorhanden (aktualisiert Brigadier-Dispatcher).
        // WICHTIG: In Paper sendet CraftServer.syncCommands() das aktualisierte Paket
        // BEREITS asynchron an alle Online-Spieler. Daher darf online.updateCommands()
        // NICHT zusätzlich aufgerufen werden, da dies parallele Worker-Threads ("Paper Async
        // Command Builder Thread Pool") auf unfertigen Brigadier-Nodes erzeugt.
        boolean serverSynced = false;
        try {
            if (Bukkit.getServer() != null) {
                Method syncMethod = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
                syncMethod.setAccessible(true);
                syncMethod.invoke(Bukkit.getServer());
                serverSynced = true;
            }
        } catch (Throwable ignored) {}

        // Fallback: Nur wenn CraftServer.syncCommands() auf dieser Plattform nicht existiert
        if (!serverSynced) {
            try {
                if (Bukkit.getServer() != null) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.updateCommands();
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isCommandDisabled(String label) {
        if (label == null || label.isBlank()) return false;
        String clean = label.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return disabledLabels.contains(clean);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (disabledLabels.isEmpty()) return;
        CommandMap map = getCommandMap();
        Map<String, Command> known = getKnownCommands(map);
        event.getCommands().removeIf(cmdName -> {
            String lower = cmdName.toLowerCase(Locale.ROOT);
            if (!disabledLabels.contains(lower)) return false;
            if (known != null) {
                Command c = known.get(lower);
                if (c != null && (!(c instanceof PluginCommand pc) || pc.getPlugin() != plugin)) {
                    return false; // Ein anderes Plugin oder Vanilla stellt diesen Befehl bereit!
                }
            }
            return true;
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (disabledLabels.isEmpty()) return;
        String msg = event.getMessage();
        if (msg == null || !msg.startsWith("/")) return;

        String[] parts = msg.substring(1).trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) return;

        String cmdLabel = parts[0].toLowerCase(Locale.ROOT);
        if (disabledLabels.contains(cmdLabel)) {
            CommandMap map = getCommandMap();
            Map<String, Command> known = getKnownCommands(map);
            if (known != null) {
                Command cmd = known.get(cmdLabel);
                // Nur blockieren, wenn der Befehl tatsächlich von Backbone registriert wurde!
                if (cmd instanceof PluginCommand pc && pc.getPlugin() == plugin) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("Unknown command. Type \"/help\" for help.");
                }
            }
        }
    }

    private static CommandMap getCommandMap() {
        try {
            if (Bukkit.getServer() == null) return null;
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(CommandMap commandMap) {
        if (commandMap == null) return null;
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (Throwable ignored) {
            try {
                Method method = commandMap.getClass().getDeclaredMethod("getKnownCommands");
                method.setAccessible(true);
                return (Map<String, Command>) method.invoke(commandMap);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }
}
