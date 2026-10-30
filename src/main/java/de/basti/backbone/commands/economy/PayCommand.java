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
 * Command for transferring money to other players (/pay &lt;player&gt; &lt;amount&gt;).
 * Permission: backbone.pay
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!EconomyManager.isEconomyEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.disabled"));
            return true;
        }

        if (!sender.hasPermission("backbone.pay")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.pay"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.usage", "label", label));
            return true;
        }

        UUID targetUuid = PlayerResolver.resolveUuid(args[0]);
        if (targetUuid == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("common.player-not-found", "player", args[0]));
            return true;
        }

        if (player.getUniqueId().equals(targetUuid)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.self-error"));
            return true;
        }

        String targetName = PlayerResolver.getPlayerName(targetUuid);

        if (EconomyManager.isPayBlocked(targetUuid) && !player.hasPermission("backbone.pay.bypass") && !player.isOp()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.target-blocked", "player", targetName));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1].replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.invalid-amount"));
            return true;
        }

        if (!Double.isFinite(amount)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.invalid-amount"));
            return true;
        }

        if (amount > EconomyManager.MAX_BALANCE) {
            player.sendMessage(Lang.getPrefix() + Lang.get("money.amount-too-large"));
            return true;
        }

        amount = Math.round(amount * 100.0) / 100.0;

        if (amount < 0.01) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.min-amount", "currency", EconomyManager.getCurrencySymbol()));
            return true;
        }

        if (!EconomyManager.hasBalance(player.getUniqueId(), amount)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.insufficient-funds", "balance", EconomyManager.format(EconomyManager.getBalance(player.getUniqueId()))));
            return true;
        }

        boolean success = EconomyManager.transfer(player.getUniqueId(), targetUuid, amount);
        if (!success) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.transfer-failed"));
            return true;
        }

        String formatted = EconomyManager.format(amount);

        player.sendMessage(Lang.getPrefix() + Lang.get("pay.success-sender", "player", targetName, "amount", formatted));

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(Lang.getPrefix() + Lang.get("pay.success-target", "amount", formatted, "player", player.getName()));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.pay")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && de.basti.backbone.util.HideManager.canSee(sender, p) && p.getName().toLowerCase().startsWith(current)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            completions.add("10");
            completions.add("50");
            completions.add("100");
        }
        return completions;
    }
}
