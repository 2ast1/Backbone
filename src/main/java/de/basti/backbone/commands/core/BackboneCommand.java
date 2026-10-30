package de.basti.backbone.commands.core;

import de.basti.backbone.Backbone;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.CommandRegistryManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.RamManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.automod.core.AutoMod;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main command for the Backbone plugin.
 * Commands:
 * - /backbone setup   (Interactive Setup Wizard)
 * - /backbone reload  (Synchronizes configuration files from disk)
 * - /backbone github  (Interactive link to the GitHub repository)
 * - /backbone version (Shows the installed version)
 */
public class BackboneCommand implements CommandExecutor, TabCompleter {

    private static final String GITHUB_URL = "https://github.com/2ast1/Backbone";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasSetupPermission(sender)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.admin"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "setup" -> handleSetup(sender, args);
            case "reload" -> handleReload(sender);
            case "github" -> handleGithub(sender);
            case "version", "ver" -> handleVersion(sender);
            default -> {
                sender.sendMessage(Lang.getPrefix() + Lang.get("common.unknown-subcommand", "subcommand", args[0]));
                sendHelp(sender, label);
            }
        }

        return true;
    }

    private boolean hasSetupPermission(CommandSender sender) {
        return sender.isOp()
                || sender.hasPermission("backbone.setup")
                || sender.hasPermission("backbone.admin")
                || sender.hasPermission("backbone.reload");
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (!hasSetupPermission(sender)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.setup"));
            return;
        }

        // /backbone setup ignore (funktioniert auch von der Konsole)
        if (args.length >= 2 && args[1].equalsIgnoreCase("ignore")) {
            DataManager.setConfig("server.backbone.setup-completed", true);
            if (sender instanceof Player player && SetupWizardManager.isInSetup(player)) {
                SetupWizardManager.removeSession(player);
            }
            sender.sendMessage(Lang.getPrefix() + Lang.get("setup.ignored"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.player-only"));
            return;
        }

        // Interner Klick-Callback der Buttons im Setup-Wizard: /backbone setup _input <wert>
        if (args.length >= 3 && args[1].equalsIgnoreCase("_input")) {
            if (!SetupWizardManager.isInSetup(player)) {
                player.sendMessage(Lang.getPrefix() + Lang.get("setup.not-active"));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                sb.append(args[i]).append(i + 1 < args.length ? " " : "");
            }
            SetupWizardManager.handleInput(player, sb.toString());
            return;
        }

        // /backbone setup stop
        if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
            if (SetupWizardManager.isInSetup(player)) {
                SetupWizardManager.stopSetup(player);
            } else {
                player.sendMessage(Lang.getPrefix() + Lang.get("setup.not-active"));
            }
            return;
        }

        // /backbone setup <Kapitel>
        if (args.length >= 2) {
            SetupWizardManager.Step targetStep = SetupWizardManager.resolveChapterStep(args[1]);
            if (targetStep == null) {
                player.sendMessage(Lang.getPrefix() + Lang.get("setup.unknown-chapter", "chapter", args[1]));
                return;
            }
            SetupWizardManager.startSetup(player, targetStep);
            return;
        }

        // /backbone setup (Start ab Kapitel 1.1)
        SetupWizardManager.startSetup(player, SetupWizardManager.Step.CH1_1_LANGUAGE);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("backbone.reload") && !sender.hasPermission("backbone.relaod")) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("common.no-permission", "permission", "backbone.reload"));
            return;
        }

        long start = System.currentTimeMillis();

        // 1. RAM-Cache leeren und config.yml frisch von Festplatte lesen
        DataManager.reloadAll();
        // 2. Fehlende Standardwerte nachziehen (ohne bestehende zu überschreiben)
        DataManager.createDefaultConfig();
        // 3. Sprachdateien neu laden
        Lang.reload();
        // 4. AutoMod-Dateien und Einstellungen frisch einlesen
        AutoMod.initialize();
        // 5. Ränge und Berechtigungen neu laden
        RankManager.reload();
        // 6. Tablisten-Präfixe für alle Online-Spieler aktualisieren
        PrefixManager.updateAllTabLists();
        // 7. Scoreboard-Dateien neu laden und Boards aktualisieren
        de.basti.backbone.util.scoreboard.ScoreboardManager.reload();
        // 8. Befehlsregistrierungen, Vault-Services und RAM-Compacting-Modus synchronisieren
        CommandRegistryManager.syncCommands();
        de.basti.backbone.util.vault.VaultManager.reload();
        RamManager.applyMode();

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(Lang.getPrefix() + Lang.get("backbone.reload-success", "ms", String.valueOf(elapsed)));
    }

    private void handleGithub(CommandSender sender) {
        if (sender instanceof Player player) {
            sender.sendMessage(Lang.getPrefix() + Lang.get("backbone.github.header"));

            TextComponent linkComponent = new TextComponent("§8» §b§n" + GITHUB_URL);
            linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, GITHUB_URL));
            linkComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("backbone.github.hover-open"))));

            TextComponent hint = new TextComponent(Lang.get("backbone.github.clickable-hint"));
            hint.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Lang.get("backbone.github.hover-url", "url", GITHUB_URL))));

            TextComponent fullMessage = new TextComponent("");
            fullMessage.addExtra(linkComponent);
            fullMessage.addExtra(hint);

            player.spigot().sendMessage(fullMessage);
        } else {
            sender.sendMessage(Lang.getPrefix() + Lang.get("backbone.github.console", "url", GITHUB_URL));
        }
    }

    private void handleVersion(CommandSender sender) {
        Backbone plugin = Backbone.getInstance();
        String version = plugin != null ? plugin.getDescription().getVersion() : Lang.get("common.unknown");
        List<String> authors = plugin != null ? plugin.getDescription().getAuthors() : List.of("2ast1");

        sender.sendMessage(Lang.getPrefix() + Lang.get("backbone.version.version", "version", version));
        sender.sendMessage(Lang.getPrefix() + Lang.get("backbone.version.authors", "authors", String.join(", ", authors)));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Lang.get("backbone.help.header"));
        sender.sendMessage(Lang.get("backbone.help.version", "label", label));
        sender.sendMessage(Lang.get("backbone.help.github", "label", label));
        if (hasSetupPermission(sender)) {
            sender.sendMessage(Lang.get("backbone.help.setup", "label", label));
        }
        if (sender.hasPermission("backbone.reload") || sender.hasPermission("backbone.relaod")) {
            sender.sendMessage(Lang.get("backbone.help.reload", "label", label));
        }
        sender.sendMessage(Lang.get("backbone.help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasSetupPermission(sender)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> subCommands = new ArrayList<>();

            subCommands.add("version");
            subCommands.add("github");
            if (hasSetupPermission(sender)) {
                subCommands.add("setup");
            }
            if (sender.hasPermission("backbone.reload") || sender.hasPermission("backbone.relaod")) {
                subCommands.add("reload");
            }

            for (String sub : subCommands) {
                if (sub.startsWith(current)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup") && hasSetupPermission(sender)) {
            String current = args[1].toLowerCase(Locale.ROOT);
            List<String> chapters = Lang.getList("setup.tab-chapters");
            if (chapters.isEmpty()) {
                chapters = List.of("Sprache", "Scoreboard", "Ränge", "Automod", "Befehle", "Economy", "Teleportation", "MotD", "Nachrichten", "Compating", "ignore");
            }
            for (String chapter : chapters) {
                if (chapter.toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(chapter);
                }
            }
        }

        return completions;
    }
}
