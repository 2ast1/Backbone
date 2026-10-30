package de.basti.backbone.commands.rank;

import de.basti.backbone.ranks.PermissionService;
import de.basti.backbone.ranks.PlayerPermissionManager;
import de.basti.backbone.ranks.PurchaseResult;
import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.ranks.RankOffer;
import de.basti.backbone.ranks.RankShop;
import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.economy.EconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Command zur Verwaltung von Rängen, Berechtigungen, temporären Rängen,
 * Rang-Verkettung (Promote/Demote) und Kaufangeboten.
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault());

    private static final List<String> COLOR_SUGGESTIONS = List.of(
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9",
            "&a", "&b", "&c", "&d", "&e", "&f"
    );

    private static final List<String> DURATION_SUGGESTIONS = List.of(
            "30m", "1h", "12h", "1d", "7d", "30d", "3mo", "1y", "perm"
    );

    private boolean hasAnyRankPermission(CommandSender sender) {
        return sender.isOp()
                || sender.hasPermission("backbone.rank")
                || sender.hasPermission("backbone.rank.admin")
                || sender.hasPermission("backbone.rank.set")
                || sender.hasPermission("backbone.rank.info")
                || sender.hasPermission("backbone.rank.list")
                || sender.hasPermission("backbone.rank.buy");
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);

        // Direkter Aufruf von /ranks
        if (lowerLabel.equals("ranks")) {
            return handleList(sender);
        }

        // Direkter Aufruf von /setrank <player> <rank> [duration]
        if (lowerLabel.equals("setrank")) {
            return handleSet(sender, args);
        }

        if (!hasAnyRankPermission(sender)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.list"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> sendHelp(sender, label);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "set" -> handleSet(sender, shiftArgs(args));
            case "setdefault", "default" -> handleSetDefault(sender, args);
            case "create", "add" -> handleCreate(sender, args);
            case "delete", "del", "remove" -> handleDelete(sender, args);
            case "perm", "permission", "permissions" -> handlePerm(sender, args);
            case "player" -> handlePlayer(sender, args);
            case "buy" -> handleBuy(sender, args);
            case "setprice" -> handleSetPrice(sender, args);
            case "shop" -> handleShop(sender, args);
            case "setname", "name" -> handleSetName(sender, args);
            case "setcolor", "color" -> handleSetColor(sender, args);
            case "setprefix", "prefix" -> handleSetPrefix(sender, args);
            case "setduration", "duration" -> handleSetDuration(sender, args);
            case "setprevious", "setprev", "previous", "prev" -> handleSetPrev(sender, args);
            case "setnext", "next" -> handleSetNext(sender, args);
            case "promote" -> handlePromote(sender, args);
            case "demote" -> handleDemote(sender, args);
            case "offer", "offers" -> handleOffer(sender, args);
            case "enable" -> handleToggle(sender, true);
            case "disable" -> handleToggle(sender, false);
            default -> {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.unknown-subcommand", "subcommand", args[0]));
                sendHelp(sender, label);
            }
        }

        return true;
    }

    private String[] shiftArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Lang.get("rank.help.header"));
        if (sender.hasPermission("backbone.rank.list")) {
            sender.sendMessage(Lang.get("rank.help.list", "label", label));
        }
        if (sender.hasPermission("backbone.rank.info")) {
            sender.sendMessage(Lang.get("rank.help.info", "label", label));
        }
        if (sender.hasPermission("backbone.rank.buy")) {
            sender.sendMessage(Lang.get("rank.help.buy", "label", label));
        }
        if (sender.hasPermission("backbone.rank.set")) {
            sender.sendMessage(Lang.get("rank.help.set"));
            sender.sendMessage(Lang.get("rank.help.promote", "label", label));
            sender.sendMessage(Lang.get("rank.help.demote", "label", label));
        }
        if (sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.get("rank.help.setdefault", "label", label));
            sender.sendMessage(Lang.get("rank.help.create", "label", label));
            sender.sendMessage(Lang.get("rank.help.delete", "label", label));
            sender.sendMessage(Lang.get("rank.help.perm-world", "label", label));
            sender.sendMessage(Lang.get("rank.help.player-addperm", "label", label));
            sender.sendMessage(Lang.get("rank.help.player-removeperm", "label", label));
            sender.sendMessage(Lang.get("rank.help.player-perms", "label", label));
            sender.sendMessage(Lang.get("rank.help.player-reset", "label", label));
            sender.sendMessage(Lang.get("rank.help.offer", "label", label));
            sender.sendMessage(Lang.get("rank.help.setprice", "label", label));
            sender.sendMessage(Lang.get("rank.help.setduration", "label", label));
            sender.sendMessage(Lang.get("rank.help.setprev", "label", label));
            sender.sendMessage(Lang.get("rank.help.setnext", "label", label));
            sender.sendMessage(Lang.get("rank.help.shop-toggle", "label", label));
            sender.sendMessage(Lang.get("rank.help.setname", "label", label));
            sender.sendMessage(Lang.get("rank.help.setcolor", "label", label));
            sender.sendMessage(Lang.get("rank.help.setprefix", "label", label));
            sender.sendMessage(Lang.get("rank.help.toggle", "label", label));
        }
        if (!RankManager.isRanksEnabled()) {
            sender.sendMessage(Lang.get("rank.help.disabled-notice"));
        }
        sender.sendMessage(Lang.get("rank.help.footer"));
    }

    // =========================================================================
    // SUBCOMMANDS
    // =========================================================================

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("backbone.rank.list")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.list"));
            return true;
        }

        Collection<Rank> ranks = RankManager.getAllRanks();
        if (ranks.isEmpty()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.list.empty"));
            if (sender.hasPermission("backbone.rank.admin")) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.list.empty-hint"));
            }
            return true;
        }

        String defaultName = RankManager.getDefaultRankName();
        sender.sendMessage(Lang.get("rank.list.header", "count", String.valueOf(ranks.size())));
        for (Rank rank : ranks) {
            boolean isDefault = rank.getName().equalsIgnoreCase(defaultName);
            String formattedPrefix = rank.getPrefix().isBlank() ? Lang.get("rank.list.no-prefix") : PrefixManager.formatColors(rank.getPrefix());
            String defaultBadge = isDefault ? Lang.get("rank.list.default-badge") : "";
            String durationBadge = rank.isDefaultPermanent() ? " §8[§e" + Lang.get("common.permanent") + "§8]" : " §8[§b" + rank.getFormattedDefaultDuration() + "§8]";
            String priceBadge = rank.isPurchasable() ? " §8[§6" + rank.getOffers().size() + " " + (rank.getOffers().size() == 1 ? "Angebot" : "Angebote") + "§8]" : "";

            sender.sendMessage("§8» " + rank.getColor() + rank.getName() + defaultBadge + durationBadge + priceBadge);

            String chainStr = "";
            if (rank.hasPreviousRank() || rank.hasNextRank()) {
                chainStr = " &8| &7Kette: &8[&7" + (rank.hasPreviousRank() ? rank.getPreviousRank() : "-") + " §8» &e" + rank.getName() + " §8» &7" + (rank.hasNextRank() ? rank.getNextRank() : "-") + "&8]";
            }

            sender.sendMessage(Lang.get("rank.list.entry-details",
                    "prefix", formattedPrefix,
                    "color", rank.getColor(),
                    "color_code", rank.getColor().replace("§", "&"),
                    "perms", String.valueOf(rank.getPermissions().size())) + chainStr);
        }
        sender.sendMessage(Lang.get("rank.help.footer"));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.info")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.info"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.info.usage"));
            return true;
        }

        String target = args[1].trim();

        Rank rank = RankManager.getRank(target);
        if (rank != null) {
            showRankInfo(sender, rank);
            return true;
        }

        UUID uuid = PlayerResolver.resolveUuid(target);
        if (uuid != null) {
            showPlayerInfo(sender, uuid, target);
            return true;
        }

        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.info.not-found", "target", target));
        return true;
    }

    private void showRankInfo(CommandSender sender, Rank rank) {
        String defaultName = RankManager.getDefaultRankName();
        boolean isDefault = rank.getName().equalsIgnoreCase(defaultName);

        sender.sendMessage(Lang.get("rank.info.rank.header", "color", rank.getColor(), "name", rank.getName()));
        sender.sendMessage(Lang.get("rank.info.rank.name", "color", rank.getColor(), "name", rank.getName()));
        sender.sendMessage(Lang.get("rank.info.rank.color", "color", rank.getColor(), "color_code", rank.getColor().replace("§", "&")));
        sender.sendMessage(Lang.get("rank.info.rank.prefix", "prefix", rank.getPrefix().isBlank() ? Lang.get("rank.info.rank.no-prefix") : PrefixManager.formatColors(rank.getPrefix())));
        sender.sendMessage(Lang.get("rank.info.rank.default", "is_default", isDefault ? Lang.get("common.yes") : Lang.get("common.no")));
        sender.sendMessage(Lang.get("rank.info.rank.default-duration", "duration", rank.getFormattedDefaultDuration()));

        if (rank.hasPreviousRank() || rank.hasNextRank()) {
            String none = Lang.get("rank.chain.none");
            sender.sendMessage(Lang.get("rank.info.rank.chain",
                    "prev", rank.hasPreviousRank() ? rank.getPreviousRank() : none,
                    "next", rank.hasNextRank() ? rank.getNextRank() : none));
        }

        if (rank.isPurchasable()) {
            sender.sendMessage(Lang.get("rank.info.rank.offers-header", "count", String.valueOf(rank.getOffers().size())));
            int idx = 1;
            for (RankOffer off : rank.getOffers()) {
                sender.sendMessage("  §8[§e" + idx++ + "§8] §a" + off.format(rank));
            }
        } else {
            sender.sendMessage(Lang.get("rank.info.rank.price-not-for-sale"));
        }

        sender.sendMessage(Lang.get("rank.info.rank.created", "date", DATE_FORMAT.format(java.time.Instant.ofEpochMilli(rank.getCreated()))));
        sender.sendMessage(Lang.get("rank.info.rank.last-change", "date", DATE_FORMAT.format(java.time.Instant.ofEpochMilli(rank.getLastChange()))));

        List<String> perms = rank.getPermissions();
        sender.sendMessage(Lang.get("rank.info.rank.permissions-header", "count", String.valueOf(perms.size())));
        if (perms.isEmpty()) {
            sender.sendMessage(Lang.get("rank.info.rank.permissions-none"));
        } else {
            for (String p : perms) {
                sender.sendMessage("  §8- §e" + p);
            }
        }

        Map<String, List<String>> worldPerms = rank.getWorldPermissions();
        if (!worldPerms.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : worldPerms.entrySet()) {
                sender.sendMessage(Lang.get("rank.info.rank.world-permissions-header", "world", entry.getKey(), "count", String.valueOf(entry.getValue().size())));
                for (String p : entry.getValue()) {
                    sender.sendMessage("  §8- §e" + p);
                }
            }
        }

        sender.sendMessage(Lang.get("rank.help.footer"));
    }

    private void showPlayerInfo(CommandSender sender, UUID uuid, String fallbackName) {
        String playerName = PlayerResolver.getPlayerName(uuid);
        if (playerName == null || playerName.isBlank()) playerName = fallbackName;

        String assignedRank = RankManager.getPlayerAssignedRankName(uuid);
        Rank effectiveRank = RankManager.getRankOfPlayer(uuid);

        sender.sendMessage(Lang.get("rank.info.player.header", "player", playerName));
        sender.sendMessage(Lang.get("rank.info.player.uuid", "uuid", uuid.toString()));

        if (effectiveRank != null) {
            sender.sendMessage(Lang.get("rank.info.player.current-rank",
                    "color", effectiveRank.getColor(),
                    "name", effectiveRank.getName()));

            String source = (assignedRank != null) ? Lang.get("rank.info.player.source-assigned") : Lang.get("rank.info.player.source-default");
            sender.sendMessage(Lang.get("rank.info.player.source", "source", source));

            if (assignedRank != null) {
                boolean perm = RankManager.isPlayerRankPermanent(uuid);
                if (perm) {
                    sender.sendMessage(Lang.get("rank.info.player.validity", "validity", Lang.get("common.permanent")));
                } else {
                    long expiresAt = RankManager.getPlayerExpiresAt(uuid);
                    sender.sendMessage(Lang.get("rank.info.player.expires",
                            "date", DATE_FORMAT.format(java.time.Instant.ofEpochMilli(expiresAt)),
                            "remaining", RankManager.getFormattedPlayerRemainingTime(uuid)));
                }
                String fallback = RankManager.getPlayerFallbackRankName(uuid);
                if (fallback != null && !fallback.isBlank()) {
                    sender.sendMessage(Lang.get("rank.info.player.fallback", "rank", fallback));
                }
            }

            sender.sendMessage(Lang.get("rank.info.player.effective-perms", "count", String.valueOf(effectiveRank.getPermissions().size())));
            for (String p : effectiveRank.getPermissions()) {
                sender.sendMessage("  §8- §e" + p);
            }
        } else {
            sender.sendMessage(Lang.get("rank.info.player.no-rank"));
        }

        List<String> globalInd = PlayerPermissionManager.getGlobalPermissions(uuid);
        Map<String, List<String>> worldInd = PlayerPermissionManager.getWorldPermissions(uuid);
        if (!globalInd.isEmpty()) {
            sender.sendMessage(Lang.get("rank.info.player.individual-perms", "count", String.valueOf(globalInd.size())));
            for (String p : globalInd) {
                sender.sendMessage("  §8- §e" + p);
            }
        }
        if (!worldInd.isEmpty()) {
            for (Map.Entry<String, List<String>> e : worldInd.entrySet()) {
                sender.sendMessage(Lang.get("rank.info.player.individual-world-perms", "world", e.getKey(), "count", String.valueOf(e.getValue().size())));
                for (String p : e.getValue()) {
                    sender.sendMessage("  §8- §e" + p);
                }
            }
        }
        if (globalInd.isEmpty() && worldInd.isEmpty()) {
            sender.sendMessage(Lang.get("rank.info.player.individual-none"));
        }

        sender.sendMessage(Lang.get("rank.help.footer"));
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.set")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.set"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.set.usage"));
            return true;
        }

        String targetName = args[0].trim();
        String rankName = args[1].trim();

        UUID uuid = PlayerResolver.resolveUuid(targetName);
        if (uuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }

        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.available-ranks", "ranks", String.join("§7, §e", RankManager.getAllRankNames())));
            return true;
        }

        Long overrideDuration = null;
        if (args.length >= 3) {
            String durInput = args[2].trim();
            if (DurationUtil.isPermanent(durInput)) {
                overrideDuration = -1L;
            } else if (DurationUtil.isDurationSyntax(durInput)) {
                long parsed = DurationUtil.parseDuration(durInput);
                overrideDuration = parsed > 0 ? parsed : null;
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.set.invalid-duration"));
                return true;
            }
        }

        String resolvedName = PlayerResolver.getPlayerName(uuid);
        RankManager.setPlayerRank(uuid, rank.getName(), overrideDuration);

        String durStr = RankManager.isPlayerRankPermanent(uuid)
                ? "§8(§a" + Lang.get("common.permanent") + "§8)"
                : Lang.get("rank.set.duration-suffix", "duration", RankManager.getFormattedPlayerRemainingTime(uuid));

        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.set.success-sender",
                "player", resolvedName,
                "color", rank.getColor(),
                "name", rank.getName(),
                "duration", durStr));

        Player targetPlayer = Bukkit.getPlayer(uuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(Lang.getPrefix() + Lang.get("rank.set.success-target",
                    "color", rank.getColor(),
                    "name", rank.getName(),
                    "duration", durStr));
        }

        return true;
    }

    private boolean handleSetDefault(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setdefault.usage"));
            return true;
        }

        String rankName = args[1].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        RankManager.setDefaultRank(rank.getName());
        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setdefault.success",
                "color", rank.getColor(),
                "name", rank.getName()));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.create.usage"));
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.create.hint-name"));
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.create.hint-color"));
            return true;
        }

        String name = args[1].trim();
        String color = args[2].trim();
        long duration = -1;
        int prefixStartIndex = 3;

        if (args.length >= 4) {
            String maybeDur = args[3].trim();
            if (DurationUtil.isPermanent(maybeDur)) {
                duration = -1;
                prefixStartIndex = 4;
            } else if (DurationUtil.isDurationSyntax(maybeDur)) {
                duration = DurationUtil.parseDuration(maybeDur);
                prefixStartIndex = 4;
            }
        }

        String prefix = "";
        if (args.length > prefixStartIndex) {
            StringBuilder sb = new StringBuilder();
            for (int i = prefixStartIndex; i < args.length; i++) {
                sb.append(args[i]).append(i + 1 < args.length ? " " : "");
            }
            prefix = sb.toString();
        }

        try {
            boolean wasEmptyBefore = RankManager.getAllRanks().isEmpty();
            Rank created = RankManager.createRank(name, color, duration, prefix);

            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.create.success",
                    "color", created.getColor(),
                    "name", created.getName(),
                    "duration", created.getFormattedDefaultDuration()));

            if (wasEmptyBefore || RankManager.getDefaultRankName().equalsIgnoreCase(created.getName())) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.create.auto-default"));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.error", "error", e.getMessage()));
        }

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.delete.usage"));
            return true;
        }

        String name = args[1].trim();
        Rank rank = RankManager.getRank(name);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", name));
            return true;
        }

        boolean success = RankManager.deleteRank(rank.getName());
        if (success) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.delete.success", "name", rank.getName()));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.delete.failed"));
        }

        return true;
    }

    private boolean handlePerm(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.usage-header"));
            sender.sendMessage(Lang.get("rank.perm.usage-add"));
            sender.sendMessage(Lang.get("rank.perm.usage-remove"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String rankName = args[2].trim();
        String permission = args[3].trim();
        if (permission.isBlank()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.usage-header"));
            return true;
        }
        List<String> worlds = args.length >= 5 ? Arrays.asList(Arrays.copyOfRange(args, 4, args.length)) : null;

        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        if (action.equals("add")) {
            boolean added = RankManager.addPermission(rank.getName(), permission, worlds);
            if (added) {
                if (worlds != null && !worlds.isEmpty()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.added-world",
                            "permission", permission,
                            "color", rank.getColor(),
                            "name", rank.getName(),
                            "world", String.join(", ", worlds)));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.added",
                            "permission", permission,
                            "color", rank.getColor(),
                            "name", rank.getName()));
                }
            } else {
                if (worlds != null && !worlds.isEmpty()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.already-exists-world",
                            "world", String.join(", ", worlds)));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.already-exists"));
                }
            }
        } else if (action.equals("remove") || action.equals("del")) {
            boolean removed = RankManager.removePermission(rank.getName(), permission, worlds);
            if (removed) {
                if (worlds != null && !worlds.isEmpty()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.removed-world",
                            "permission", permission,
                            "color", rank.getColor(),
                            "name", rank.getName(),
                            "world", String.join(", ", worlds)));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.removed",
                            "permission", permission,
                            "color", rank.getColor(),
                            "name", rank.getName()));
                }
            } else {
                if (worlds != null && !worlds.isEmpty()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.not-found-world",
                            "world", String.join(", ", worlds)));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.not-found"));
                }
            }
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.unknown-action", "action", action));
        }

        return true;
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-header"));
            sender.sendMessage(Lang.get("rank.player.usage-add"));
            sender.sendMessage(Lang.get("rank.player.usage-remove"));
            sender.sendMessage(Lang.get("rank.player.usage-perms"));
            sender.sendMessage(Lang.get("rank.player.usage-reset"));
            return true;
        }

        String targetName = args[1].trim();
        UUID uuid = PlayerResolver.resolveUuid(targetName);
        if (uuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }

        String resolvedName = PlayerResolver.getPlayerName(uuid);
        String action = args[2].toLowerCase(Locale.ROOT);

        switch (action) {
            case "addperm", "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-add"));
                    return true;
                }
                String perm = args[3].trim();
                if (perm.isBlank()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-add"));
                    return true;
                }
                List<String> worlds = args.length >= 5 ? Arrays.asList(Arrays.copyOfRange(args, 4, args.length)) : null;
                boolean added = PlayerPermissionManager.addPermission(uuid, perm, worlds);
                String scope = (worlds == null || worlds.isEmpty()) ? Lang.get("rank.player.scope-global") :
                        (worlds.size() == 1 ? Lang.get("rank.player.scope-world", "world", worlds.get(0)) :
                                Lang.get("rank.player.scope-worlds", "worlds", String.join(", ", worlds)));
                if (added) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.added",
                            "permission", perm,
                            "player", resolvedName,
                            "scope", scope));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.already-exists",
                            "scope", scope));
                }
            }
            case "removeperm", "remove", "delperm", "del" -> {
                if (args.length < 4) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-remove"));
                    return true;
                }
                String perm = args[3].trim();
                if (perm.isBlank()) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-remove"));
                    return true;
                }
                List<String> worlds = args.length >= 5 ? Arrays.asList(Arrays.copyOfRange(args, 4, args.length)) : null;
                boolean removed = PlayerPermissionManager.removePermission(uuid, perm, worlds);
                String scope = (worlds == null || worlds.isEmpty()) ? Lang.get("rank.player.scope-global") :
                        (worlds.size() == 1 ? Lang.get("rank.player.scope-world", "world", worlds.get(0)) :
                                Lang.get("rank.player.scope-worlds", "worlds", String.join(", ", worlds)));
                if (removed) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.removed",
                            "permission", perm,
                            "player", resolvedName,
                            "scope", scope));
                } else {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.not-found",
                            "scope", scope));
                }
            }
            case "perms", "permissions", "list" -> {
                sender.sendMessage(Lang.get("rank.player.perms-header", "player", resolvedName));
                List<String> global = PlayerPermissionManager.getGlobalPermissions(uuid);
                Map<String, List<String>> worlds = PlayerPermissionManager.getWorldPermissions(uuid);
                if (global.isEmpty() && worlds.isEmpty()) {
                    sender.sendMessage(Lang.get("rank.player.perms-none"));
                } else {
                    if (!global.isEmpty()) {
                        sender.sendMessage(Lang.get("rank.player.perms-global-header", "count", String.valueOf(global.size())));
                        for (String p : global) sender.sendMessage("  §8- §e" + p);
                    }
                    for (Map.Entry<String, List<String>> e : worlds.entrySet()) {
                        sender.sendMessage(Lang.get("rank.player.perms-world-header", "world", e.getKey(), "count", String.valueOf(e.getValue().size())));
                        for (String p : e.getValue()) sender.sendMessage("  §8- §e" + p);
                    }
                }
                sender.sendMessage(Lang.get("rank.help.footer"));
            }
            case "reset" -> {
                if (args.length >= 4) {
                    if (args[3].equalsIgnoreCase("all")) {
                        PlayerPermissionManager.resetAll(uuid);
                        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.reset-all-success", "player", resolvedName));
                    } else {
                        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.usage-reset"));
                    }
                } else {
                    PlayerPermissionManager.resetPermissions(uuid);
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.player.reset-perms-success", "player", resolvedName));
                }
            }
            default -> {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.perm.unknown-action", "action", action));
            }
        }

        return true;
    }

    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.buy")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.buy"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (!RankManager.isRanksEnabled()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.ranks-disabled"));
            return true;
        }

        if (!RankShop.isShopEnabled()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.disabled"));
            return true;
        }

        if (!EconomyBridge.isAvailable()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.no-economy"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        // 1. /rank buy (alle Ränge und Angebote auflisten)
        if (args.length == 1) {
            List<Rank> purchasable = RankShop.getPurchasableRanks();
            player.sendMessage(Lang.get("rank.buy.header"));
            player.sendMessage(Lang.get("rank.buy.your-balance", "balance", EconomyBridge.format(EconomyBridge.getBalance(uuid))));

            if (purchasable.isEmpty()) {
                player.sendMessage(Lang.get("rank.buy.none-available"));
            } else {
                Rank current = RankManager.getRankOfPlayer(uuid);
                for (Rank r : purchasable) {
                    player.sendMessage("§8» " + r.getColor() + r.getName() + "§7:");
                    int oIdx = 1;
                    for (RankOffer off : r.getOffers()) {
                        double p = RankShop.getPriceForPlayer(uuid, r, off);
                        String dur = off.getFormattedDuration(r);
                        player.sendMessage("   §8[§e" + oIdx + "§8] §a" + EconomyBridge.format(p) + " §8- §e" + dur
                                + " §8| §e/rank buy " + r.getName() + " " + oIdx);
                        oIdx++;
                    }
                }
            }
            player.sendMessage(Lang.get("rank.help.footer"));
            return true;
        }

        String targetRankName = args[1].trim();
        Rank targetRank = RankManager.getRank(targetRankName);
        if (targetRank == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", targetRankName));
            return true;
        }

        if (!targetRank.isPurchasable() || targetRank.getOffers().isEmpty()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.not-for-sale", "rank", targetRank.getName()));
            return true;
        }

        int offerIndex = 0; // 0-basiert
        boolean hasOfferSpecified = false;
        boolean confirm = false;

        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("confirm")) {
                confirm = true;
            } else {
                try {
                    int parsed = Integer.parseInt(args[2].trim());
                    offerIndex = parsed - 1;
                    hasOfferSpecified = true;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
            confirm = true;
        }

        // Falls es mehrere Angebote gibt und noch keines ausgewählt wurde
        if (targetRank.getOffers().size() > 1 && !hasOfferSpecified && !confirm) {
            player.sendMessage(Lang.get("rank.buy.offers-header", "name", targetRank.getName(), "color", targetRank.getColor()));
            int idx = 1;
            for (RankOffer off : targetRank.getOffers()) {
                double p = RankShop.getPriceForPlayer(uuid, targetRank, off);
                player.sendMessage(Lang.get("rank.buy.offer-entry",
                        "idx", String.valueOf(idx),
                        "price", EconomyBridge.format(p),
                        "duration", off.getFormattedDuration(targetRank),
                        "name", targetRank.getName()));
                idx++;
            }
            return true;
        }

        if (offerIndex < 0 || offerIndex >= targetRank.getOffers().size()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.invalid-offer"));
            return true;
        }

        RankOffer offer = targetRank.getOffers().get(offerIndex);

        if (!confirm) {
            PurchaseResult check = RankShop.canPurchase(uuid, targetRank.getName(), offerIndex);
            if (!check.isSuccess()) {
                double effPrice = RankShop.getPriceForPlayer(uuid, targetRank, offer);
                player.sendMessage(Lang.getPrefix() + Lang.get(check.getLangKey(),
                        "rank", targetRank.getName(),
                        "price", EconomyBridge.format(effPrice),
                        "balance", EconomyBridge.format(EconomyBridge.getBalance(uuid))));
                return true;
            }

            player.sendMessage(Lang.get("rank.buy.confirm-prompt-offer",
                    "nr", String.valueOf(offerIndex + 1),
                    "color", targetRank.getColor(),
                    "name", targetRank.getName(),
                    "price", EconomyBridge.format(check.getPricePaid()),
                    "duration", offer.getFormattedDuration(targetRank)));

            if (targetRank.getOffers().size() > 1) {
                player.sendMessage("§e/rank buy " + targetRank.getName() + " " + (offerIndex + 1) + " confirm");
            } else {
                player.sendMessage("§e/rank buy " + targetRank.getName() + " confirm");
            }
            return true;
        }

        // Confirm = true
        PurchaseResult result = RankShop.purchase(uuid, targetRank.getName(), offerIndex);
        if (result.isSuccess()) {
            if (result.isExtendedDuration()) {
                player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.extended-success",
                        "color", result.getTargetRank().getColor(),
                        "name", result.getTargetRank().getName(),
                        "duration", offer.getFormattedDuration(result.getTargetRank()),
                        "balance", EconomyBridge.format(result.getBalanceAfter())));
            } else {
                player.sendMessage(Lang.getPrefix() + Lang.get("rank.buy.success",
                        "color", result.getTargetRank().getColor(),
                        "name", result.getTargetRank().getName(),
                        "price", EconomyBridge.format(result.getPricePaid())));
            }
        } else {
            double effPrice = RankShop.getPriceForPlayer(uuid, targetRank, offer);
            player.sendMessage(Lang.getPrefix() + Lang.get(result.getLangKey(),
                    "rank", targetRank.getName(),
                    "price", EconomyBridge.format(effPrice),
                    "balance", EconomyBridge.format(EconomyBridge.getBalance(uuid))));
        }

        return true;
    }

    private boolean handleSetPrice(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.usage"));
            return true;
        }

        String rankName = args[1].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        String val = args[2].trim().toLowerCase(Locale.ROOT);
        if (val.equals("off") || val.equals("remove") || val.equals("none") || val.equals("disable")) {
            RankManager.removeRankPrice(rank.getName());
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.removed",
                    "color", rank.getColor(),
                    "name", rank.getName()));
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(val.replace(",", "."));
        } catch (NumberFormatException e) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.invalid-price"));
            return true;
        }

        if (!Double.isFinite(price) || price < 0.0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.invalid-price"));
            return true;
        }

        Long customDuration = null;
        if (args.length >= 4) {
            String durStr = args[3].trim();
            if (DurationUtil.isPermanent(durStr)) {
                customDuration = -1L;
            } else if (DurationUtil.isDurationSyntax(durStr)) {
                customDuration = DurationUtil.parseDuration(durStr);
            }
        }

        RankManager.clearOffers(rank.getName());
        RankManager.addOffer(rank.getName(), new RankOffer(price, customDuration));

        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.success",
                "color", rank.getColor(),
                "name", rank.getName(),
                "price", EconomyBridge.format(price)));
        return true;
    }

    private boolean handleShop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.shop.usage"));
            return true;
        }

        String action = args[1].trim().toLowerCase(Locale.ROOT);
        if (action.equals("enable") || action.equals("on") || action.equals("true")) {
            RankShop.setShopEnabled(true);
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.shop.enabled"));
        } else if (action.equals("disable") || action.equals("off") || action.equals("false")) {
            RankShop.setShopEnabled(false);
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.shop.disabled"));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.shop.usage"));
        }
        return true;
    }

    private boolean handleSetName(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setname.usage"));
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setname.hint"));
            return true;
        }

        String rankName = args[1].trim();
        String newName = args[2].trim();

        try {
            boolean ok = RankManager.setRankName(rankName, newName);
            if (ok) {
                Rank rank = RankManager.getRank(newName);
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setname.success", "old_name", rankName, "name", rank.getName()));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.error", "error", e.getMessage()));
        }

        return true;
    }

    private boolean handleSetColor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setcolor.usage"));
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setcolor.hint"));
            return true;
        }

        String rankName = args[1].trim();
        String color = args[2].trim();

        try {
            boolean ok = RankManager.setRankColor(rankName, color);
            if (ok) {
                Rank rank = RankManager.getRank(rankName);
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setcolor.success",
                        "name", rank.getName(),
                        "color", rank.getColor(),
                        "color_code", rank.getColor().replace("§", "&")));
            } else {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.error", "error", e.getMessage()));
        }

        return true;
    }

    private boolean handleSetPrefix(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprefix.usage"));
            return true;
        }

        String rankName = args[1].trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        String prefix = sb.toString();

        boolean ok = RankManager.setRankPrefix(rankName, prefix);
        if (ok) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprefix.success", "name", rankName, "prefix", PrefixManager.formatColors(prefix)));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
        }

        return true;
    }

    private boolean handleSetDuration(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setduration.usage"));
            return true;
        }

        String rankName = args[1].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        String durInput = args[2].trim();
        long millis = -1;
        if (!DurationUtil.isPermanent(durInput)) {
            if (!DurationUtil.isDurationSyntax(durInput)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setduration.invalid-duration"));
                return true;
            }
            millis = DurationUtil.parseDuration(durInput);
        }

        RankManager.setRankDuration(rank.getName(), millis);
        Rank updated = RankManager.getRank(rank.getName());
        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setduration.success",
                "color", updated.getColor(),
                "name", updated.getName(),
                "duration", updated.getFormattedDefaultDuration()));
        return true;
    }

    private boolean handleSetPrev(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprev.usage"));
            return true;
        }

        String rankName = args[1].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        String prevName = args[2].trim();
        boolean ok = RankManager.setRankPrevious(rank.getName(), prevName);
        if (ok) {
            Rank updated = RankManager.getRank(rank.getName());
            String prevDisplay = updated.hasPreviousRank() ? updated.getPreviousRank() : Lang.get("rank.chain.none");
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprev.success",
                    "name", updated.getName(),
                    "prev", prevDisplay));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", prevName));
        }
        return true;
    }

    private boolean handleSetNext(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setnext.usage"));
            return true;
        }

        String rankName = args[1].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        String nextName = args[2].trim();
        boolean ok = RankManager.setRankNext(rank.getName(), nextName);
        if (ok) {
            Rank updated = RankManager.getRank(rank.getName());
            String nextDisplay = updated.hasNextRank() ? updated.getNextRank() : Lang.get("rank.chain.none");
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setnext.success",
                    "name", updated.getName(),
                    "next", nextDisplay));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", nextName));
        }
        return true;
    }

    private boolean handlePromote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.set") && !sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.set"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.promote.usage"));
            return true;
        }

        String targetName = args[1].trim();
        UUID uuid = PlayerResolver.resolveUuid(targetName);
        if (uuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }

        Rank cur = RankManager.getRankOfPlayer(uuid);
        if (cur == null || !cur.hasNextRank()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.promote.no-next", "name", cur != null ? cur.getName() : ""));
            return true;
        }

        Long overrideDur = null;
        if (args.length >= 3) {
            String durInput = args[2].trim();
            if (DurationUtil.isPermanent(durInput)) {
                overrideDur = -1L;
            } else if (DurationUtil.isDurationSyntax(durInput)) {
                overrideDur = DurationUtil.parseDuration(durInput);
            }
        }

        boolean ok = RankManager.promotePlayer(uuid, overrideDur);
        if (ok) {
            Rank newRank = RankManager.getRankOfPlayer(uuid);
            String resolved = PlayerResolver.getPlayerName(uuid);
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.promote.success",
                    "player", resolved,
                    "color", newRank.getColor(),
                    "name", newRank.getName()));
        }
        return true;
    }

    private boolean handleDemote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.set") && !sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.set"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.demote.usage"));
            return true;
        }

        String targetName = args[1].trim();
        UUID uuid = PlayerResolver.resolveUuid(targetName);
        if (uuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", targetName));
            return true;
        }

        Rank cur = RankManager.getRankOfPlayer(uuid);
        if (cur == null || !cur.hasPreviousRank()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.demote.no-prev", "name", cur != null ? cur.getName() : ""));
            return true;
        }

        Long overrideDur = null;
        if (args.length >= 3) {
            String durInput = args[2].trim();
            if (DurationUtil.isPermanent(durInput)) {
                overrideDur = -1L;
            } else if (DurationUtil.isDurationSyntax(durInput)) {
                overrideDur = DurationUtil.parseDuration(durInput);
            }
        }

        boolean ok = RankManager.demotePlayer(uuid, overrideDur);
        if (ok) {
            Rank newRank = RankManager.getRankOfPlayer(uuid);
            String resolved = PlayerResolver.getPlayerName(uuid);
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.demote.success",
                    "player", resolved,
                    "color", newRank.getColor(),
                    "name", newRank.getName()));
        }
        return true;
    }

    private boolean handleOffer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.usage"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String rankName = args[2].trim();
        Rank rank = RankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.not-exists", "rank", rankName));
            return true;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.usage-add"));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[3].replace(",", "."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.setprice.invalid-price"));
                    return true;
                }
                Long dur = null;
                if (args.length >= 5) {
                    String dStr = args[4].trim();
                    if (DurationUtil.isPermanent(dStr)) {
                        dur = -1L;
                    } else if (DurationUtil.isDurationSyntax(dStr)) {
                        dur = DurationUtil.parseDuration(dStr);
                    }
                }
                RankOffer offer = new RankOffer(price, dur);
                RankManager.addOffer(rank.getName(), offer);
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.added",
                        "name", rank.getName(),
                        "offer", offer.format(rank)));
            }
            case "remove", "del" -> {
                if (args.length < 4) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.usage-remove"));
                    return true;
                }
                try {
                    int nr = Integer.parseInt(args[3].trim());
                    boolean ok = RankManager.removeOffer(rank.getName(), nr - 1);
                    if (ok) {
                        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.removed", "name", rank.getName(), "nr", String.valueOf(nr)));
                    } else {
                        sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.invalid-number"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.invalid-price"));
                }
            }
            case "list" -> {
                sender.sendMessage(Lang.get("rank.offer.list-header", "name", rank.getName()));
                if (rank.getOffers().isEmpty()) {
                    sender.sendMessage(Lang.get("rank.offer.list-empty"));
                } else {
                    int i = 1;
                    for (RankOffer off : rank.getOffers()) {
                        sender.sendMessage("  §8[§e" + i++ + "§8] §a" + off.format(rank));
                    }
                }
                sender.sendMessage(Lang.get("rank.help.footer"));
            }
            case "clear" -> {
                RankManager.clearOffers(rank.getName());
                sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.cleared", "name", rank.getName()));
            }
            default -> sender.sendMessage(Lang.getPrefix() + Lang.get("rank.offer.usage"));
        }

        return true;
    }

    private boolean handleToggle(CommandSender sender, boolean enable) {
        if (!sender.hasPermission("backbone.rank.admin")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.rank.admin"));
            return true;
        }

        RankManager.setRanksEnabled(enable);
        if (enable) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.toggle.enabled"));
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("rank.toggle.disabled"));
        }

        return true;
    }

    // =========================================================================
    // TAB COMPLETION
    // =========================================================================

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String lowerLabel = label.toLowerCase(Locale.ROOT);

        if (lowerLabel.equals("ranks")) {
            return Collections.emptyList();
        }

        if (lowerLabel.equals("setrank")) {
            if (!sender.hasPermission("backbone.rank.set")) return Collections.emptyList();
            if (args.length == 1) {
                return filterPrefix(PlayerResolver.getAllKnownPlayerNames(), args[0]);
            }
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(DURATION_SUGGESTIONS, args[2]);
            }
            return Collections.emptyList();
        }

        if (!hasAnyRankPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("backbone.rank.list")) subs.add("list");
            if (sender.hasPermission("backbone.rank.info")) subs.add("info");
            if (sender.hasPermission("backbone.rank.buy")) subs.add("buy");
            if (sender.hasPermission("backbone.rank.set")) {
                subs.addAll(List.of("set", "promote", "demote"));
            }
            if (sender.hasPermission("backbone.rank.admin")) {
                subs.addAll(List.of("setdefault", "create", "delete", "perm", "player", "offer", "setprice", "shop", "setname", "setcolor", "setprefix", "setduration", "setprev", "setnext", "enable", "disable"));
            }
            subs.add("help");
            return filterPrefix(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "del", "remove" -> sub = "delete";
            case "color" -> sub = "setcolor";
            case "name" -> sub = "setname";
            case "prefix" -> sub = "setprefix";
            case "duration" -> sub = "setduration";
            case "previous", "setprevious" -> sub = "setprev";
            case "next" -> sub = "setnext";
            case "offers" -> sub = "offer";
            case "permission", "permissions" -> sub = "perm";
            case "default" -> sub = "setdefault";
            case "add" -> sub = "create";
        }

        if (sub.equals("info") && args.length == 2) {
            List<String> targets = new ArrayList<>(RankManager.getAllRankNames());
            targets.addAll(PlayerResolver.getAllKnownPlayerNames());
            return filterPrefix(targets, args[1]);
        }

        if (sub.equals("buy")) {
            if (!sender.hasPermission("backbone.rank.buy")) return Collections.emptyList();
            if (args.length == 2) {
                List<String> purchasable = new ArrayList<>();
                for (Rank r : RankShop.getPurchasableRanks()) {
                    purchasable.add(r.getName());
                }
                return filterPrefix(purchasable, args[1]);
            }
            if (args.length == 3) {
                Rank r = RankManager.getRank(args[1]);
                List<String> choices = new ArrayList<>();
                if (r != null && r.getOffers().size() > 1) {
                    for (int i = 1; i <= r.getOffers().size(); i++) {
                        choices.add(String.valueOf(i));
                    }
                }
                choices.add("confirm");
                return filterPrefix(choices, args[2]);
            }
            if (args.length == 4) {
                return filterPrefix(List.of("confirm"), args[3]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("offer") && sender.hasPermission("backbone.rank.admin")) {
            if (args.length == 2) {
                return filterPrefix(List.of("add", "remove", "list", "clear"), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(RankManager.getAllRankNames(), args[2]);
            }
            if (args[1].equalsIgnoreCase("add") && args.length == 4) {
                return filterPrefix(List.of("100", "500", "1000", "2000"), args[3]);
            }
            if (args[1].equalsIgnoreCase("add") && args.length == 5) {
                return filterPrefix(DURATION_SUGGESTIONS, args[4]);
            }
            if ((args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("del")) && args.length == 4) {
                Rank r = RankManager.getRank(args[2]);
                if (r != null) {
                    List<String> nrs = new ArrayList<>();
                    for (int i = 1; i <= r.getOffers().size(); i++) nrs.add(String.valueOf(i));
                    return filterPrefix(nrs, args[3]);
                }
            }
            return Collections.emptyList();
        }

        if (sub.equals("setprice") && sender.hasPermission("backbone.rank.admin")) {
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(List.of("100", "500", "1000", "off"), args[2]);
            }
            if (args.length == 4) {
                return filterPrefix(DURATION_SUGGESTIONS, args[3]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("setduration") && sender.hasPermission("backbone.rank.admin")) {
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(DURATION_SUGGESTIONS, args[2]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("setprev") && sender.hasPermission("backbone.rank.admin")) {
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                List<String> pool = new ArrayList<>(RankManager.getAllRankNames());
                pool.add("none");
                return filterPrefix(pool, args[2]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("setnext") && sender.hasPermission("backbone.rank.admin")) {
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                List<String> pool = new ArrayList<>(RankManager.getAllRankNames());
                pool.add("none");
                return filterPrefix(pool, args[2]);
            }
            return Collections.emptyList();
        }

        if ((sub.equals("promote") || sub.equals("demote")) && (sender.hasPermission("backbone.rank.set") || sender.hasPermission("backbone.rank.admin"))) {
            if (args.length == 2) {
                return filterPrefix(PlayerResolver.getAllKnownPlayerNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(DURATION_SUGGESTIONS, args[2]);
            }
            return Collections.emptyList();
        }

        if (sub.equals("set")) {
            if (args.length == 2) {
                return filterPrefix(PlayerResolver.getAllKnownPlayerNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(RankManager.getAllRankNames(), args[2]);
            }
            if (args.length == 4) {
                return filterPrefix(DURATION_SUGGESTIONS, args[3]);
            }
        }

        if (sub.equals("setdefault") && args.length == 2) {
            return filterPrefix(RankManager.getAllRankNames(), args[1]);
        }

        if (sub.equals("delete") && args.length == 2) {
            return filterPrefix(RankManager.getAllRankNames(), args[1]);
        }

        if (sub.equals("setname") && args.length == 2) {
            return filterPrefix(RankManager.getAllRankNames(), args[1]);
        }

        if (sub.equals("setcolor")) {
            if (args.length == 2) {
                return filterPrefix(RankManager.getAllRankNames(), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(COLOR_SUGGESTIONS, args[2]);
            }
        }

        if (sub.equals("setprefix") && args.length == 2) {
            return filterPrefix(RankManager.getAllRankNames(), args[1]);
        }

        if (sub.equals("create")) {
            if (args.length == 3) {
                return filterPrefix(COLOR_SUGGESTIONS, args[2]);
            }
            if (args.length == 4) {
                return filterPrefix(DURATION_SUGGESTIONS, args[3]);
            }
        }

        if (sub.equals("perm")) {
            if (args.length == 2) {
                return filterPrefix(List.of("add", "remove"), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(RankManager.getAllRankNames(), args[2]);
            }
            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("del")) {
                    Rank rank = RankManager.getRank(args[2]);
                    if (rank != null) {
                        Set<String> allPerms = new LinkedHashSet<>(rank.getPermissions());
                        for (List<String> list : rank.getWorldPermissions().values()) {
                            allPerms.addAll(list);
                        }
                        return filterPrefix(allPerms, args[3]);
                    }
                } else if (args[1].equalsIgnoreCase("add")) {
                    return filterPrefix(PermissionService.KNOWN_BACKBONE_PERMISSIONS, args[3]);
                }
            }
            if (args.length >= 5) {
                return filterPrefix(getWorldNames(), args[args.length - 1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> getWorldNames() {
        try {
            if (Bukkit.getServer() != null) {
                List<String> names = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) {
                    names.add(w.getName());
                }
                return names;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    private List<String> filterPrefix(Collection<String> pool, String prefix) {
        if (pool == null) return Collections.emptyList();
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String item : pool) {
            if (item.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(item);
            }
        }
        return matches;
    }
}
