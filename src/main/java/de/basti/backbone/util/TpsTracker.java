package de.basti.backbone.util;

import de.basti.backbone.Backbone;
import org.bukkit.Bukkit;

/**
 * Überwacht und berechnet die Ticks per Second (TPS) des Servers.
 * Nutzt primär Server-interne Methoden (Paper/Spigot recentTps) und
 * verfügt über einen vollautomatischen internen Fallback-Tracker.
 */
public class TpsTracker implements Runnable {

    private static final int TICK_HISTORY = 100;
    private static final long[] TICKS = new long[TICK_HISTORY];
    private static final long[] TICK_DURATIONS = new long[TICK_HISTORY];
    private static int tickIndex = 0;
    private static long lastTickTimestamp = 0;
    private static boolean running = false;

    public static void start(Backbone plugin) {
        if (running) return;
        running = true;
        long now = System.currentTimeMillis();
        lastTickTimestamp = now;
        for (int i = 0; i < TICK_HISTORY; i++) {
            TICKS[i] = now;
            TICK_DURATIONS[i] = 50L;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, new TpsTracker(), 1L, 1L);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        int idx = tickIndex % TICK_HISTORY;
        TICKS[idx] = now;
        if (lastTickTimestamp > 0) {
            TICK_DURATIONS[idx] = Math.max(0L, now - lastTickTimestamp);
        }
        lastTickTimestamp = now;
        tickIndex++;
    }

    /**
     * Gibt den Durchschnitt der Tick-Dauer (MSPT) der letzten 100 Ticks in Millisekunden zurück.
     */
    public static double getAverageMspt() {
        int count = Math.min(tickIndex, TICK_HISTORY);
        if (count <= 0) return 50.0;
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += TICK_DURATIONS[i];
        }
        return (double) sum / count;
    }

    /**
     * Gibt die maximale Tick-Dauer (Lag-Spike) der letzten 100 Ticks in Millisekunden zurück.
     */
    public static long getMaxMspt() {
        int count = Math.min(tickIndex, TICK_HISTORY);
        if (count <= 0) return 50L;
        long max = 0L;
        for (int i = 0; i < count; i++) {
            if (TICK_DURATIONS[i] > max) {
                max = TICK_DURATIONS[i];
            }
        }
        return max;
    }

    /**
     * Gibt die konfigurierte Ziel-Tickrate des Servers zurück (Standard: 20.0, bei /tick rate anpassbar).
     */
    public static double getTargetTickRate() {
        try {
            if (Bukkit.getServer() != null) {
                org.bukkit.ServerTickManager stm = Bukkit.getServer().getServerTickManager();
                if (stm != null) {
                    return Math.max(1.0, (double) stm.getTickRate());
                }
            }
        } catch (Throwable ignored) {}
        return 20.0;
    }

    /**
     * Ermittelt die TPS-Werte für 1m, 5m und 15m (oder fällt auf den aktuellen TPS-Wert zurück).
     */
    public static double[] getRecentTps() {
        double maxTps = getTargetTickRate();
        try {
            java.lang.reflect.Method m = Bukkit.class.getMethod("getTPS");
            double[] tps = (double[]) m.invoke(null);
            if (tps != null && tps.length >= 3) {
                double scale = (maxTps > 20.0 && tps[0] <= 20.0) ? (maxTps / 20.0) : 1.0;
                return new double[]{
                        Math.min(maxTps, Math.max(0.0, tps[0] * scale)),
                        Math.min(maxTps, Math.max(0.0, tps[1] * scale)),
                        Math.min(maxTps, Math.max(0.0, tps[2] * scale))
                };
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> msClass = Class.forName("net.minecraft.server.MinecraftServer");
            java.lang.reflect.Method getServer = msClass.getMethod("getServer");
            Object server = getServer.invoke(null);
            if (server != null) {
                java.lang.reflect.Field f = server.getClass().getField("recentTps");
                double[] tps = (double[]) f.get(server);
                if (tps != null && tps.length >= 3) {
                    double scale = (maxTps > 20.0 && tps[0] <= 20.0) ? (maxTps / 20.0) : 1.0;
                    return new double[]{
                            Math.min(maxTps, Math.max(0.0, tps[0] * scale)),
                            Math.min(maxTps, Math.max(0.0, tps[1] * scale)),
                            Math.min(maxTps, Math.max(0.0, tps[2] * scale))
                    };
                }
            }
        } catch (Throwable ignored) {}

        double current = getTps();
        return new double[]{current, current, current};
    }

    /**
     * Ermittelt die aktuellen TPS des Servers (dynamisch bis zur eingestellten Server-Tickrate).
     */
    public static double getTps() {
        double maxTps = getTargetTickRate();

        // 1. Paper / Bukkit getTPS() via Reflection
        try {
            java.lang.reflect.Method m = Bukkit.class.getMethod("getTPS");
            double[] tps = (double[]) m.invoke(null);
            if (tps != null && tps.length > 0) {
                if (maxTps <= 20.0) {
                    return Math.min(20.0, Math.max(0.0, tps[0]));
                } else if (tps[0] > 20.0) {
                    return Math.min(maxTps, Math.max(0.0, tps[0]));
                }
            }
        } catch (Throwable ignored) {}

        // 2. MinecraftServer.recentTps via Reflection
        try {
            Class<?> msClass = Class.forName("net.minecraft.server.MinecraftServer");
            java.lang.reflect.Method getServer = msClass.getMethod("getServer");
            Object server = getServer.invoke(null);
            if (server != null) {
                java.lang.reflect.Field f = server.getClass().getField("recentTps");
                double[] tps = (double[]) f.get(server);
                if (tps != null && tps.length > 0) {
                    if (maxTps <= 20.0) {
                        return Math.min(20.0, Math.max(0.0, tps[0]));
                    } else if (tps[0] > 20.0) {
                        return Math.min(maxTps, Math.max(0.0, tps[0]));
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Interner Fallback-Tracker (berechnet tatsächliche Ticks pro Zeit)
        if (tickIndex < 20) {
            return maxTps;
        }
        int count = Math.min(tickIndex, TICK_HISTORY);
        int target = (tickIndex - 1) % TICK_HISTORY;
        int oldest = (tickIndex - count) % TICK_HISTORY;
        long elapsed = TICKS[target] - TICKS[oldest];
        if (elapsed <= 0) return maxTps;
        double tps = ((double) count / elapsed) * 1000.0;
        return Math.min(maxTps, Math.max(0.0, tps));
    }
}
