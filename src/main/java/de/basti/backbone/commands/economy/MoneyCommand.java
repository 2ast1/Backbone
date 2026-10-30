package de.basti.backbone.commands.economy;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command for viewing and managing player balances (/money).
 * Permissions: backbone.money, backbone.money.others, backbone.money.give, backbone.money.take, backbone.money.set
 */
public class MoneyCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!EconomyManager.isEconomyEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.disabled"));
            return true;
        }

        if (!sender.hasPermission("backbone.money")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money"));
            return true;
        }

        // 1. /money (own balance)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.getPrefix() + Lang.get("money.usage-console", "label", label));
                return true;
            }
            double bal = EconomyManager.getBalance(player.getUniqueId());
            player.sendMessage(Lang.getPrefix() + Lang.get("money.balance-self", "balance", EconomyManager.format(bal)));
            return true;
        }

        String firstArg = args[0].toLowerCase();

        // 2. /money give <player> <amount>
        if (firstArg.equals("give") || firstArg.equals("add")) {
            return handleGive(sender, label, args);
        }

        // 3. /money take <player> <amount>
        if (firstArg.equals("take") || firstArg.equals("remove")) {
            return handleTake(sender, label, args);
        }

        // 4. /money set <player> <amount>
        if (firstArg.equals("set")) {
            return handleSet(sender, label, args);
        }

        // 5. /money <player> (other player's balance)
        if (!sender.hasPermission("backbone.money.others")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money.others"));
            return true;
        }

        UUID targetUuid = PlayerResolver.resolveUuid(args[0]);
        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
            return true;
        }

        String targetName = PlayerResolver.getPlayerName(targetUuid);
        double bal = EconomyManager.getBalance(targetUuid);
        sender.sendMessage(Lang.getPrefix() + Lang.get("money.balance-other", "player", targetName, "balance", EconomyManager.format(bal)));
        return true;
    }

    private boolean handleGive(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("backbone.money.give")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money.give"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.usage-give", "label", label));
            return true;
        }

        UUID targetUuid = PlayerResolver.resolveUuid(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[1]));
            return true;
        }

        double amount = parseAmount(args[2]);
        if (amount == -2.0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.amount-too-large"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.invalid-positive-amount"));
            return true;
        }

        boolean success = EconomyManager.addBalance(targetUuid, amount);
        if (!success) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.amount-too-large"));
            return true;
        }
        String targetName = PlayerResolver.getPlayerName(targetUuid);
        String formatted = EconomyManager.format(amount);

        sender.sendMessage(Lang.getPrefix() + Lang.get("money.give-sender", "player", targetName, "amount", formatted));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Lang.getPrefix() + Lang.get("money.give-target", "amount", formatted));
        }
        return true;
    }

    private boolean handleTake(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("backbone.money.take")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money.take"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.usage-take", "label", label));
            return true;
        }

        UUID targetUuid = PlayerResolver.resolveUuid(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[1]));
            return true;
        }

        double amount = parseAmount(args[2]);
        if (amount == -2.0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.amount-too-large"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.invalid-positive-amount"));
            return true;
        }

        double newBal = Math.max(0.0, EconomyManager.getBalance(targetUuid) - amount);
        EconomyManager.setBalance(targetUuid, newBal);
        String targetName = PlayerResolver.getPlayerName(targetUuid);
        String formatted = EconomyManager.format(amount);

        sender.sendMessage(Lang.getPrefix() + Lang.get("money.take-sender", "player", targetName, "amount", formatted));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Lang.getPrefix() + Lang.get("money.take-target", "amount", formatted));
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("backbone.money.set")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money.set"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.usage-set", "label", label));
            return true;
        }

        UUID targetUuid = PlayerResolver.resolveUuid(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[1]));
            return true;
        }

        double amount = parseAmount(args[2]);
        if (amount == -2.0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.amount-too-large"));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.negative-amount"));
            return true;
        }

        EconomyManager.setBalance(targetUuid, amount);
        String targetName = PlayerResolver.getPlayerName(targetUuid);
        String formatted = EconomyManager.format(amount);

        sender.sendMessage(Lang.getPrefix() + Lang.get("money.set-sender", "player", targetName, "balance", formatted));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Lang.getPrefix() + Lang.get("money.set-target", "balance", formatted));
        }
        return true;
    }

    private double parseAmount(String input) {
        if (input == null || input.isBlank()) return -1.0;
        try {
            double val = Double.parseDouble(input.replace(",", "."));
            if (!Double.isFinite(val)) {
                return -1.0;
            }
            if (val > EconomyManager.MAX_BALANCE) {
                return -2.0;
            }
            return Math.round(val * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.money")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if (sender.hasPermission("backbone.money.give") && "give".startsWith(current)) completions.add("give");
            if (sender.hasPermission("backbone.money.take") && "take".startsWith(current)) completions.add("take");
            if (sender.hasPermission("backbone.money.set") && "set".startsWith(current)) completions.add("set");

            if (sender.hasPermission("backbone.money.others")) {
                for (String name : PlayerResolver.getAllKnownPlayerNames()) {
                    if (name.toLowerCase().startsWith(current)) {
                        completions.add(name);
                    }
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                String current = args[1].toLowerCase();
                for (String name : PlayerResolver.getAllKnownPlayerNames()) {
                    if (name.toLowerCase().startsWith(current)) {
                        completions.add(name);
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            }
        }

        return completions;
    }
}
