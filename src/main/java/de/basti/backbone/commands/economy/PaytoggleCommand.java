package de.basti.backbone.commands.economy;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.economy.EconomyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Befehl /paytoggle
 * Blockiert oder erlaubt Überweisungen (/pay) an die eigene Person.
 * Permission: backbone.pay
 */
public class PaytoggleCommand implements CommandExecutor, TabCompleter {

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

        boolean currentBlocked = EconomyManager.isPayBlocked(player.getUniqueId());
        boolean newBlocked = !currentBlocked;
        EconomyManager.setPayBlocked(player.getUniqueId(), newBlocked);

        if (newBlocked) {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.toggle-disabled"));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("pay.toggle-enabled"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
