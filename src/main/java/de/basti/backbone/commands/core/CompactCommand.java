package de.basti.backbone.commands.core;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.RamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Befehl /compact:
 * Wirft alle aktuell nicht benötigten Plugin-Daten aus dem Arbeitsspeicher (lagert sie auf die Festplatte aus),
 * führt eine Speicherbereinigung durch und zeigt dem Ausführer an, wie viel RAM eingespart wurde.
 */
public class CompactCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("backbone.compact") && !sender.isOp()) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.compact"));
            return true;
        }

        RamManager.CompactionResult result = RamManager.executeCompact(true);

        String beforeStr = String.format(Locale.ROOT, "%.1f", result.beforeMb());
        String afterStr = String.format(Locale.ROOT, "%.1f", result.afterMb());
        String savedStr = String.format(Locale.ROOT, "%.2f", result.savedMb());

        sender.sendMessage(Lang.get("compact.header"));
        sender.sendMessage(Lang.get("compact.offloaded", "files", String.valueOf(result.filesOffloaded())));
        sender.sendMessage(Lang.get("compact.memory", "before", beforeStr, "after", afterStr));
        sender.sendMessage(Lang.get("compact.saved", "saved", savedStr, "ms", String.valueOf(result.durationMs())));
        sender.sendMessage(Lang.get("compact.footer"));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return Collections.emptyList();
    }
}
