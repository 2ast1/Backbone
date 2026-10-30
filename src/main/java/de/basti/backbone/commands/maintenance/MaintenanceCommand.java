package de.basti.backbone.commands.maintenance;

import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Command for managing maintenance mode (/maintenance /wartung).
 * Permission: backbone.maintenance (or backbone.maintance)
 */
public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    // Temporary maintenance message for the current session
    private static volatile String sessionMessage = null;

    public static String getSessionMessage() {
        return sessionMessage;
    }

    public static void setSessionMessage(String message) {
        sessionMessage = message;
    }

    public static boolean hasMaintenancePermission(UUID uuid) {
        if (uuid == null) return false;
        try {
            if (Bukkit.getServer() != null) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    if (online.isOp()) return true;
                    if (online.hasPermission("backbone.maintenance") || online.hasPermission("backbone.maintance") || online.hasPermission("*") || online.hasPermission("backbone.*")) {
                        return true;
                    }
                }
                org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                if (offline.isOp()) return true;

                // Vault-Berechtigungen für OfflinePlayer prüfen (z.B. bei LuckPerms)
                try {
                    var rsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
                    if (rsp != null && rsp.getProvider() != null) {
                        var vaultPerm = rsp.getProvider();
                        if (vaultPerm.playerHas(null, offline, "backbone.maintenance")
                                || vaultPerm.playerHas(null, offline, "backbone.maintance")
                                || vaultPerm.playerHas(null, offline, "backbone.admin")
                                || vaultPerm.playerHas(null, offline, "*")
                                || vaultPerm.playerHas(null, offline, "backbone.*")) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // Individuelle Spielerberechtigungen aus playerdata.yml prüfen
        try {
            List<String> individualPerms = de.basti.backbone.ranks.PlayerPermissionManager.getGlobalPermissions(uuid);
            if (!individualPerms.isEmpty()) {
                if (individualPerms.contains("-backbone.maintenance") || individualPerms.contains("-backbone.maintance")) {
                    return false;
                }
                if (individualPerms.contains("backbone.maintenance")
                        || individualPerms.contains("backbone.maintance")
                        || individualPerms.contains("backbone.admin")
                        || individualPerms.contains("*")
                        || individualPerms.contains("backbone.*")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // If the rank system is active, also check the player's rank
        if (RankManager.isRanksEnabled()) {
            Rank rank = RankManager.getRankOfPlayer(uuid);
            if (rank != null && rank.getPermissions() != null) {
                List<String> perms = rank.getPermissions();
                if (perms.contains("-backbone.maintenance") || perms.contains("-backbone.maintance")) {
                    return false;
                }
                return perms.contains("backbone.maintenance")
                        || perms.contains("backbone.maintance")
                        || perms.contains("backbone.admin")
                        || perms.contains("*")
                        || perms.contains("backbone.*");
            }
        }
        return false;
    }

    public static boolean hasMaintenancePermission(Player player) {
        if (player == null) return false;
        if (player.isOp()) return true;
        if (player.hasPermission("backbone.maintenance")
                || player.hasPermission("backbone.maintance")
                || player.hasPermission("backbone.admin")
                || player.hasPermission("*")
                || player.hasPermission("backbone.*")) {
            return true;
        }
        return hasMaintenancePermission(player.getUniqueId());
    }

    public static boolean hasMaintenancePermission(CommandSender sender) {
        if (sender instanceof Player player) {
            return hasMaintenancePermission(player);
        }
        return true; // Console always allowed
    }

    public static String getMaintenanceKickMessage() {
        if (sessionMessage != null && !sessionMessage.isBlank()) {
            return de.basti.backbone.util.PrefixManager.formatColors(sessionMessage).replace("\\n", "\n");
        }
        String msg = DataManager.getConfigString("server.maintenance-message");
        if (msg == null || msg.isBlank()) {
            msg = Lang.getRaw("maintenance.default-kick-message");
        }
        return de.basti.backbone.util.PrefixManager.formatColors(msg).replace("\\n", "\n");
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!hasMaintenancePermission(sender)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.maintenance"));
            return true;
        }

        boolean current = DataManager.getConfigBoolean("server.maintenance");

        if (args.length == 0) {
            setMaintenance(sender, !current);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on", "enable", "an", "ein" -> {
                if (current) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.already-enabled"));
                } else {
                    setMaintenance(sender, true);
                }
            }
            case "off", "disable", "aus" -> {
                if (!current) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.already-disabled"));
                } else {
                    setMaintenance(sender, false);
                }
            }
            case "toggle" -> setMaintenance(sender, !current);
            case "status", "info" -> {
                String status = current ? Lang.get("common.enabled") : Lang.get("common.disabled");
                sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.status", "status", status));
                if (sessionMessage != null) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.session-message-info", "message", sessionMessage));
                }
            }
            case "message", "msg", "nachricht" -> handleMessage(sender, args);
            default -> sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.usage", "label", label));
        }

        return true;
    }

    private void handleMessage(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sessionMessage != null) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.current-session-message", "message", sessionMessage));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.current-default-message", "message", getMaintenanceKickMessage()));
            }
            sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.message-tip"));
            return;
        }

        if (args.length == 2 && (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("clear"))) {
            setSessionMessage(null);
            sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.message-reset"));
            return;
        }

        String rawMsg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String formatted = de.basti.backbone.util.PrefixManager.formatColors(rawMsg).replace("\\n", "\n");
        setSessionMessage(formatted);

        sender.sendMessage(Lang.getPrefix() + Lang.get("maintenance.message-set"));
        sender.sendMessage("§8» §r" + formatted);
    }

    private void setMaintenance(CommandSender sender, boolean enable) {
        DataManager.setConfig("server.maintenance", enable);

        if (enable) {
            String kickReason = getMaintenanceKickMessage();
            int kickedCount = 0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!hasMaintenancePermission(player)) {
                    player.kickPlayer(kickReason);
                    kickedCount++;
                }
            }

            String kickedInfo = kickedCount > 0 ? Lang.get("maintenance.broadcast-kicked-info", "count", String.valueOf(kickedCount)) : "";
            String broadcastMsg = Lang.getPrefix() + Lang.get("maintenance.broadcast-enabled", "sender", sender.getName(), "kicked_info", kickedInfo);
            notifyAdmins(broadcastMsg);
        } else {
            String broadcastMsg = Lang.getPrefix() + Lang.get("maintenance.broadcast-disabled", "sender", sender.getName());
            notifyAdmins(broadcastMsg);
        }
    }

    private void notifyAdmins(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hasMaintenancePermission(p)) {
                p.sendMessage(message);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!hasMaintenancePermission(sender)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            List<String> subs = List.of("on", "off", "toggle", "status", "message");
            for (String s : subs) {
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("message") || args[0].equalsIgnoreCase("msg"))) {
            String current = args[1].toLowerCase();
            if ("reset".startsWith(current)) {
                completions.add("reset");
            }
        }
        return completions;
    }
}
