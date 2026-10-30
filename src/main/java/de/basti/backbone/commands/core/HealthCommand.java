package de.basti.backbone.commands.core;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.TpsTracker;
import de.basti.backbone.util.scoreboard.ScoreboardParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Befehl /health:
 * Liefert detaillierte Informationen über die Server-TPS, Tick-Zeiten (MSPT),
 * allgemeine Server-Gesundheit, Ursachen für Performance-Einbrüche und scannt
 * alle geladenen Chunks aktiv nach potenziellen Lag-Maschinen.
 */
public class HealthCommand implements CommandExecutor, TabCompleter {

    private record ChunkHotspot(
            String worldName,
            int chunkX,
            int chunkZ,
            int centerX,
            int centerY,
            int centerZ,
            int totalEntities,
            int items,
            int mobs,
            int contraptionEntities,
            int tileEntities,
            int hoppers,
            int lagScore,
            String reasonTypeKey
    ) {}

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.health") && !sender.isOp()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.health"));
            return true;
        }

        // Subcommand: /health tp <world> <x> <y> <z>
        if (args.length == 5 && args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
                return true;
            }
            World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                player.sendMessage(Lang.getPrefix() + Lang.get("health.tp-world-not-found", "world", args[1]));
                return true;
            }
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                Location targetLoc = new Location(world, x + 0.5, y, z + 0.5);
                player.teleport(targetLoc);
                player.sendMessage(Lang.getPrefix() + Lang.get("health.tp-success",
                        "world", world.getName(),
                        "x", String.valueOf((int) x),
                        "y", String.valueOf((int) y),
                        "z", String.valueOf((int) z)));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }

        double[] tpsArr = TpsTracker.getRecentTps();
        double currentTps = tpsArr[0];
        double avgMspt = TpsTracker.getAverageMspt();
        long maxMspt = TpsTracker.getMaxMspt();

        double processCpu = ScoreboardParser.getProcessCpuLoad();
        double systemCpu = ScoreboardParser.getSystemCpuLoad();
        String uptime = ScoreboardParser.getFormattedUptime();

        Runtime rt = Runtime.getRuntime();
        long maxBytes = rt.maxMemory();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        double usedMb = usedBytes / (1024.0 * 1024.0);
        double maxMb = maxBytes / (1024.0 * 1024.0);
        int ramPercent = maxBytes > 0 ? (int) Math.round(((double) usedBytes / maxBytes) * 100.0) : 0;

        int totalChunks = 0;
        int totalEntities = 0;
        int totalItems = 0;
        int totalMobs = 0;
        int totalTiles = 0;
        int totalHoppers = 0;

        List<ChunkHotspot> hotspots = new ArrayList<>();

        if (Bukkit.getServer() != null) {
            for (World world : Bukkit.getWorlds()) {
                Chunk[] loadedChunks = world.getLoadedChunks();
                totalChunks += loadedChunks.length;

                for (Chunk chunk : loadedChunks) {
                    Entity[] entities = chunk.getEntities();
                    BlockState[] tiles = chunk.getTileEntities();

                    int cEntities = entities.length;
                    int cTiles = tiles.length;
                    int cItems = 0;
                    int cMobs = 0;
                    int cContraptions = 0;
                    int cHoppers = 0;

                    for (Entity ent : entities) {
                        if (ent instanceof Item) {
                            cItems++;
                        } else if (ent instanceof ArmorStand || ent instanceof Vehicle || ent instanceof Projectile) {
                            cContraptions++;
                        } else if (ent instanceof LivingEntity && !(ent instanceof Player)) {
                            cMobs++;
                        }
                    }

                    for (BlockState tile : tiles) {
                        if (tile instanceof Hopper) {
                            cHoppers++;
                        }
                    }

                    totalEntities += cEntities;
                    totalItems += cItems;
                    totalMobs += cMobs;
                    totalTiles += cTiles;
                    totalHoppers += cHoppers;

                    int lagScore = (cEntities * 2) + (cItems * 2) + (cContraptions * 3) + (cHoppers * 4) + cTiles;
                    if (lagScore >= 30 || cEntities >= 20 || cHoppers >= 8 || cTiles >= 25) {
                        int centerX = (chunk.getX() << 4) + 8;
                        int centerZ = (chunk.getZ() << 4) + 8;
                        int centerY = 80;
                        try {
                            centerY = Math.max(64, world.getHighestBlockYAt(centerX, centerZ) + 1);
                        } catch (Throwable ignored) {}

                        String typeKey;
                        if (cHoppers >= 10) {
                            typeKey = "health.lagmachine.type.hoppers";
                        } else if (cItems >= 20) {
                            typeKey = "health.lagmachine.type.items";
                        } else if (cContraptions >= 12) {
                            typeKey = "health.lagmachine.type.contraption";
                        } else if (cMobs >= 20) {
                            typeKey = "health.lagmachine.type.mobfarm";
                        } else if (cTiles >= 25) {
                            typeKey = "health.lagmachine.type.tiles";
                        } else {
                            typeKey = "health.lagmachine.type.general";
                        }

                        hotspots.add(new ChunkHotspot(
                                world.getName(),
                                chunk.getX(),
                                chunk.getZ(),
                                centerX,
                                centerY,
                                centerZ,
                                cEntities,
                                cItems,
                                cMobs,
                                cContraptions,
                                cTiles,
                                cHoppers,
                                lagScore,
                                typeKey
                        ));
                    }
                }
            }
        }

        hotspots.sort(Comparator.comparingInt(ChunkHotspot::lagScore).reversed());

        // Diagnose-Gründe sammeln
        List<String> diagnosisReasons = new ArrayList<>();
        if (currentTps < 18.5 || avgMspt > 52.0) {
            diagnosisReasons.add(Lang.get("health.diagnosis.low-tps",
                    "tps", formatTpsPlain(currentTps),
                    "mspt", String.format(Locale.ROOT, "%.1f", avgMspt)));
        }
        if (maxMspt >= 85 && currentTps >= 18.5) {
            diagnosisReasons.add(Lang.get("health.diagnosis.tick-spikes", "max_mspt", String.valueOf(maxMspt)));
        }
        if (totalEntities >= 600) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-entities", "entities", String.valueOf(totalEntities), "mobs", String.valueOf(totalMobs)));
        }
        if (totalItems >= 150) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-items", "items", String.valueOf(totalItems)));
        }
        if (totalHoppers >= 150 || totalTiles >= 800) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-tiles", "tiles", String.valueOf(totalTiles), "hoppers", String.valueOf(totalHoppers)));
        }
        if (totalChunks >= 600) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-chunks", "chunks", String.valueOf(totalChunks)));
        }
        if (ramPercent >= 85) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-ram", "percent", String.valueOf(ramPercent)));
        }
        if (processCpu >= 80.0 || systemCpu >= 90.0) {
            diagnosisReasons.add(Lang.get("health.diagnosis.high-cpu", "cpu", String.format(Locale.ROOT, "%.1f", processCpu)));
        }
        if (!hotspots.isEmpty() && hotspots.get(0).lagScore() >= 80) {
            diagnosisReasons.add(Lang.get("health.diagnosis.lagmachine-suspected", "count", String.valueOf(hotspots.size())));
        }

        String statusBadge;
        if (currentTps < 15.0 || avgMspt > 65.0 || ramPercent >= 95) {
            statusBadge = Lang.get("health.status.critical");
        } else if (currentTps < 19.0 || avgMspt > 48.0 || ramPercent >= 85 || !diagnosisReasons.isEmpty()) {
            statusBadge = Lang.get("health.status.warning");
        } else {
            statusBadge = Lang.get("health.status.excellent");
        }

        sender.sendMessage(Lang.get("health.header"));
        sender.sendMessage(Lang.get("health.overall-status", "status", statusBadge, "uptime", uptime));
        sender.sendMessage(Lang.get("health.tps-line",
                "tps_1m", formatTpsColored(tpsArr[0]),
                "tps_5m", formatTpsColored(tpsArr[1]),
                "tps_15m", formatTpsColored(tpsArr[2]),
                "mspt", String.format(Locale.ROOT, "%.1f", avgMspt),
                "max_mspt", String.valueOf(maxMspt)));
        sender.sendMessage(Lang.get("health.system-line",
                "cpu", String.format(Locale.ROOT, "%.1f", processCpu),
                "sys_cpu", String.format(Locale.ROOT, "%.1f", systemCpu),
                "used_mb", String.format(Locale.ROOT, "%.0f", usedMb),
                "max_mb", String.format(Locale.ROOT, "%.0f", maxMb),
                "ram_percent", String.valueOf(ramPercent)));
        sender.sendMessage(Lang.get("health.world-stats",
                "chunks", String.valueOf(totalChunks),
                "entities", String.valueOf(totalEntities),
                "items", String.valueOf(totalItems),
                "tiles", String.valueOf(totalTiles),
                "hoppers", String.valueOf(totalHoppers)));

        // Diagnose-Sektion
        sender.sendMessage(Lang.get("health.diagnosis.header"));
        if (diagnosisReasons.isEmpty()) {
            sender.sendMessage(Lang.get("health.diagnosis.healthy"));
        } else {
            for (String reason : diagnosisReasons) {
                sender.sendMessage(reason);
            }
        }

        // Lag-Maschinen-Scanner Sektion
        sender.sendMessage(Lang.get("health.lagmachine.header"));
        if (hotspots.isEmpty()) {
            sender.sendMessage(Lang.get("health.lagmachine.none"));
        } else {
            int limit = Math.min(3, hotspots.size());
            for (int i = 0; i < limit; i++) {
                ChunkHotspot h = hotspots.get(i);
                String entryText = Lang.get("health.lagmachine.entry",
                        "index", String.valueOf(i + 1),
                        "world", h.worldName(),
                        "x", String.valueOf(h.centerX()),
                        "y", String.valueOf(h.centerY()),
                        "z", String.valueOf(h.centerZ()),
                        "type", Lang.get(h.reasonTypeKey()),
                        "entities", String.valueOf(h.totalEntities()),
                        "tiles", String.valueOf(h.tileEntities()),
                        "hoppers", String.valueOf(h.hoppers()),
                        "score", String.valueOf(h.lagScore()));

                if (sender instanceof Player player) {
                    TextComponent lineComp = new TextComponent(entryText + " ");
                    TextComponent tpBtn = new TextComponent(Lang.get("health.lagmachine.tp-btn"));
                    tpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/health tp " + h.worldName() + " " + h.centerX() + " " + h.centerY() + " " + h.centerZ()));
                    tpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(Lang.get("health.lagmachine.tp-hover",
                                    "world", h.worldName(),
                                    "x", String.valueOf(h.centerX()),
                                    "y", String.valueOf(h.centerY()),
                                    "z", String.valueOf(h.centerZ())))));
                    lineComp.addExtra(tpBtn);
                    player.spigot().sendMessage(lineComp);
                } else {
                    sender.sendMessage(entryText);
                }
            }
        }

        sender.sendMessage(Lang.get("health.footer"));
        return true;
    }

    private String formatTpsPlain(double tps) {
        return String.format(Locale.ROOT, "%.1f", tps);
    }

    private String formatTpsColored(double tps) {
        String color = tps >= 19.0 ? "§a" : (tps >= 15.0 ? "§e" : "§c");
        return color + String.format(Locale.ROOT, "%.1f", tps);
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
