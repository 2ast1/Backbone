package de.basti.backbone.commands.economy;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.economy.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Befehl /baltop [page]
 * Zeigt die reichsten Spieler auf dem Server an.
 * Permission: backbone.money.others
 */
public class BaltopCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!EconomyManager.isEconomyEnabled()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.disabled"));
            return true;
        }

        if (!sender.hasPermission("backbone.money.others")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.money.others"));
            return true;
        }

        List<EconomyManager.BalanceEntry> balances = EconomyManager.getTopBalances();
        if (balances.isEmpty()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("money.baltop.empty"));
            return true;
        }

        int maxPages = Math.max(1, (int) Math.ceil((double) balances.size() / PAGE_SIZE));
        int page = 1;

        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        sender.sendMessage(Lang.getPrefix() + Lang.get("money.baltop.header", "page", String.valueOf(page), "max_pages", String.valueOf(maxPages)));

        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, balances.size());

        for (int i = startIndex; i < endIndex; i++) {
            EconomyManager.BalanceEntry entry = balances.get(i);
            sender.sendMessage(Lang.get("money.baltop.entry",
                    "rank", String.valueOf(i + 1),
                    "player", entry.name(),
                    "balance", EconomyManager.format(entry.balance())));
        }

        sender.sendMessage(Lang.get("money.baltop.footer", "total", EconomyManager.format(EconomyManager.getTotalCirculation())));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.money.others")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<EconomyManager.BalanceEntry> balances = EconomyManager.getTopBalances();
            int maxPages = Math.max(1, (int) Math.ceil((double) balances.size() / PAGE_SIZE));
            List<String> completions = new ArrayList<>();
            String current = args[0].toLowerCase();
            for (int p = 1; p <= maxPages; p++) {
                String s = String.valueOf(p);
                if (s.startsWith(current)) {
                    completions.add(s);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
