package de.basti.backbone.commands.core;

import de.basti.backbone.commands.teleport.TpaManager;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.RamManager;
import de.basti.backbone.util.scoreboard.ScoreboardManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Befehl /ram:
 * Zeigt eine detaillierte Aufschlüsselung des Arbeitsspeicherverbrauchs nach JVM-Speicherpools,
 * Welten/Chunks, Plugins/Threads sowie internen Backbone-Subsystemen.
 */
public class RamCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.ram") && !sender.isOp()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.ram"));
            return true;
        }

        Runtime rt = Runtime.getRuntime();
        long maxBytes = rt.maxMemory();
        long allocatedBytes = rt.totalMemory();
        long freeBytes = rt.freeMemory();
        long usedBytes = allocatedBytes - freeBytes;

        double maxMb = maxBytes / (1024.0 * 1024.0);
        double allocatedMb = allocatedBytes / (1024.0 * 1024.0);
        double usedMb = usedBytes / (1024.0 * 1024.0);
        double freeMb = (maxBytes - usedBytes) / (1024.0 * 1024.0);
        int percent = maxBytes > 0 ? (int) Math.round(((double) usedBytes / maxBytes) * 100.0) : 0;

        long nonHeapBytes = 0L;
        try {
            nonHeapBytes = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
        } catch (Throwable ignored) {}
        double nonHeapMb = nonHeapBytes / (1024.0 * 1024.0);

        long pid = -1;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Throwable ignored) {}

        int totalChunks = 0;
        int totalEntities = 0;
        int totalTiles = 0;
        try {
            if (Bukkit.getServer() != null) {
                for (World w : Bukkit.getWorlds()) {
                    Chunk[] loaded = w.getLoadedChunks();
                    totalChunks += loaded.length;
                    totalEntities += w.getEntities().size();
                    for (Chunk c : loaded) {
                        totalTiles += c.getTileEntities().length;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Schätzung des Welten-/Chunk-Speicheranteils im Heap
        double estimatedWorldMb = Math.min(usedMb * 0.75, (totalChunks * 0.085) + (totalEntities * 0.004) + (totalTiles * 0.003));

        // Schätzung des Backbone-In-Memory-Caches
        int cachedFiles = DataManager.getCachedFileCount();
        int cachedKeys = DataManager.getCachedKeyCount();
        int rankCount = RankManager.getAllRanks().size();
        int sbTemplates = ScoreboardManager.getAllTemplates().size();
        int onlinePlayers = (Bukkit.getServer() != null) ? Bukkit.getOnlinePlayers().size() : 0;
        int tpaCount = TpaManager.getPendingRequestCount();
        double backboneEstMb = Math.max(0.2, (cachedKeys * 0.0015) + (cachedFiles * 0.02) + (onlinePlayers * 0.03) + (rankCount * 0.005));

        int threadCount = 0;
        try {
            threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        } catch (Throwable ignored) {}

        int pendingTasks = 0;
        int pluginCount = 0;
        try {
            if (Bukkit.getServer() != null) {
                pendingTasks = Bukkit.getScheduler().getPendingTasks().size();
                pluginCount = Bukkit.getPluginManager().getPlugins().length;
            }
        } catch (Throwable ignored) {}

        String modeKey = switch (RamManager.getMode()) {
            case FAST -> "ram.mode.fast";
            case BALANCED -> "ram.mode.balanced";
            case LIGHT -> "ram.mode.light";
        };

        sender.sendMessage(Lang.get("ram.header"));
        sender.sendMessage(Lang.get("ram.overview",
                "used", String.format(Locale.ROOT, "%.1f", usedMb),
                "allocated", String.format(Locale.ROOT, "%.1f", allocatedMb),
                "max", String.format(Locale.ROOT, "%.1f", maxMb),
                "free", String.format(Locale.ROOT, "%.1f", freeMb),
                "percent", String.valueOf(percent),
                "bar", buildProgressBar(percent)));
        sender.sendMessage(Lang.get("ram.mode-line", "mode", Lang.get(modeKey)));

        sender.sendMessage(Lang.get("ram.processes-header"));
        sender.sendMessage(Lang.get("ram.process.server-jvm",
                "pid", pid > 0 ? String.valueOf(pid) : "N/A",
                "heap", String.format(Locale.ROOT, "%.1f", usedMb),
                "nonheap", String.format(Locale.ROOT, "%.1f", nonHeapMb),
                "threads", String.valueOf(threadCount)));
        sender.sendMessage(Lang.get("ram.process.worlds",
                "mb", String.format(Locale.ROOT, "%.1f", estimatedWorldMb),
                "chunks", String.valueOf(totalChunks),
                "entities", String.valueOf(totalEntities),
                "tiles", String.valueOf(totalTiles)));
        sender.sendMessage(Lang.get("ram.process.backbone",
                "mb", String.format(Locale.ROOT, "%.2f", backboneEstMb),
                "files", String.valueOf(cachedFiles),
                "keys", String.valueOf(cachedKeys),
                "ranks", String.valueOf(rankCount),
                "sb", String.valueOf(sbTemplates),
                "tpa", String.valueOf(tpaCount)));
        sender.sendMessage(Lang.get("ram.process.plugins",
                "plugins", String.valueOf(pluginCount),
                "tasks", String.valueOf(pendingTasks)));

        // JVM Memory Pools (Eden, Old Gen, Survivor, Metaspace etc.)
        try {
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            if (!pools.isEmpty()) {
                sender.sendMessage(Lang.get("ram.pools-header"));
                for (MemoryPoolMXBean pool : pools) {
                    MemoryUsage usage = pool.getUsage();
                    if (usage != null && usage.getUsed() > 0) {
                        double poolUsedMb = usage.getUsed() / (1024.0 * 1024.0);
                        if (poolUsedMb >= 0.5) {
                            sender.sendMessage(Lang.get("ram.pool-entry",
                                    "name", pool.getName(),
                                    "used", String.format(Locale.ROOT, "%.1f", poolUsedMb)));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (sender instanceof Player player) {
            TextComponent btn = new TextComponent(Lang.get("ram.compact-btn"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/compact"));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("ram.compact-hover"))));
            player.spigot().sendMessage(btn);
        }

        sender.sendMessage(Lang.get("ram.footer"));
        return true;
    }

    private String buildProgressBar(int percent) {
        int totalBars = 10;
        int filled = Math.max(0, Math.min(totalBars, (int) Math.round(percent / 10.0)));
        String color = percent >= 85 ? "§c" : (percent >= 65 ? "§e" : "§a");
        return "§8[" + color + "■".repeat(filled) + "§7" + "□".repeat(totalBars - filled) + "§8]";
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
