package de.basti.backbone.util;

import de.basti.backbone.ranks.Rank;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.locations.LobbyManager;
import de.basti.backbone.util.scoreboard.ScoreboardManager;
import de.basti.backbone.util.scoreboard.ScoreboardParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Steuert den interaktiven Setup-Wizard (/backbone setup).
 * Pausiert den öffentlichen Chat für den ausführenden Spieler, fängt Chateingaben ab
 * und stellt klickbare Chat-Komponenten für alle 10 Kapitel bereit.
 */
public class SetupWizardManager {

    public enum Step {
        CH1_1_LANGUAGE,
        CH1_2_TRANSLATE_DEFAULTS,
        CH2_1_SCOREBOARD,
        CH2_2_SCOREBOARD_HINT,
        CH3_1_RANKS,
        CH3_2_1_RANK_NAME,
        CH3_2_2_RANK_PREFIX,
        CH3_2_3_RANK_COLOR,
        CH3_2_4_RANK_HINT,
        CH3_3_RANK_BUY,
        CH3_4_RANK_HINT,
        CH4_1_AUTOMOD,
        CH4_2_AUTOMOD_HINT,
        CH5_1_ESSENTIALS,
        CH5_2_MESSAGING,
        CH6_1_ECONOMY,
        CH6_2_START_BALANCE,
        CH6_3_CURRENCY_SYMBOL,
        CH7_1_WARP,
        CH7_2_HOME,
        CH7_3_LOBBY,
        CH7_4_LOBBY_JOIN,
        CH7_5_LOBBY_LOCATION,
        CH7_6_WORLDSPAWN,
        CH8_MOTD,
        CH9_1_JOIN_MSG,
        CH9_2_LEAVE_MSG,
        CH10_RAM_COMPACTING
    }

    public static class SetupSession {
        private final UUID playerUuid;
        private Step currentStep;
        private String pendingRankId = "";
        private String pendingRankName = "";
        private String pendingRankPrefix = "";

        public SetupSession(UUID playerUuid, Step startStep) {
            this.playerUuid = playerUuid;
            this.currentStep = startStep;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public Step getCurrentStep() {
            return currentStep;
        }

        public void setCurrentStep(Step currentStep) {
            this.currentStep = currentStep;
        }
    }

    private static final Map<UUID, SetupSession> activeSessions = new ConcurrentHashMap<>();

    public static boolean isInSetup(Player player) {
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    public static boolean isInSetup(UUID uuid) {
        return uuid != null && activeSessions.containsKey(uuid);
    }

    public static boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    public static SetupSession getSession(Player player) {
        return player != null ? activeSessions.get(player.getUniqueId()) : null;
    }

    public static void removeSession(Player player) {
        if (player != null) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    public static void shutdown() {
        activeSessions.clear();
    }

    /**
     * Löst einen Kapitel-Namen (Deutsch oder Englisch) zum jeweiligen Start-Step auf.
     */
    public static Step resolveChapterStep(String arg) {
        if (arg == null || arg.isBlank()) return Step.CH1_1_LANGUAGE;
        String clean = arg.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "sprache", "language", "lang", "1" -> Step.CH1_1_LANGUAGE;
            case "scoreboard", "sb", "2" -> Step.CH2_1_SCOREBOARD;
            case "ränge", "raenge", "ranks", "rank", "3" -> Step.CH3_1_RANKS;
            case "rankbuy", "buy", "shop" -> Step.CH3_3_RANK_BUY;
            case "automod", "4" -> Step.CH4_1_AUTOMOD;
            case "befehle", "commands", "essentials", "5" -> Step.CH5_1_ESSENTIALS;
            case "msg", "reply", "tell", "whisper", "messaging", "chat" -> Step.CH5_2_MESSAGING;
            case "economy", "eco", "geld", "money", "6" -> Step.CH6_1_ECONOMY;
            case "teleportation", "teleport", "tp", "7", "warp", "warps" -> Step.CH7_1_WARP;
            case "home", "homes" -> Step.CH7_2_HOME;
            case "lobby" -> Step.CH7_3_LOBBY;
            case "spawn", "worldspawn" -> Step.CH7_6_WORLDSPAWN;
            case "motd", "8" -> Step.CH8_MOTD;
            case "nachrichten", "messages", "join", "leave", "joinleave", "9" -> Step.CH9_1_JOIN_MSG;
            case "compating", "compacting", "compact", "ram", "10" -> Step.CH10_RAM_COMPACTING;
            default -> null;
        };
    }

    /**
     * Startet den Setup-Wizard für einen Spieler ab dem angegebenen Kapitel.
     */
    public static void startSetup(Player player, Step startStep) {
        if (player == null) return;
        if (hasActiveSessions()) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.already-running"));
            return;
        }

        Step initial = (startStep != null) ? startStep : Step.CH1_1_LANGUAGE;

        SetupSession session = new SetupSession(player.getUniqueId(), initial);
        activeSessions.put(player.getUniqueId(), session);

        sendClearPadding(player);
        player.sendMessage(Lang.get("setup.started-banner"));
        renderCurrentStep(player, session);
    }

    /**
     * Beendet den Setup-Wizard vorzeitig (z.B. durch Eingabe von "Stop").
     */
    public static void stopSetup(Player player) {
        if (player == null) return;
        activeSessions.remove(player.getUniqueId());
        player.sendMessage(Lang.get("setup.stopped"));
    }

    /**
     * Schließt den Setup-Wizard regulär nach Kapitel 10 ab.
     */
    public static void finishSetup(Player player) {
        if (player == null) return;
        activeSessions.remove(player.getUniqueId());
        DataManager.setConfig("server.backbone.setup-completed", true);
        CommandRegistryManager.syncCommands();
        player.sendMessage(Lang.get("setup.completed.header"));
        player.sendMessage(Lang.get("setup.completed.line1"));
        player.sendMessage(Lang.get("setup.completed.line2"));
        player.sendMessage(Lang.get("setup.completed.footer"));
    }

    /**
     * Verarbeitet eine Eingabe des Spielers (entweder per Chat oder per Klick-Button).
     */
    public static void handleInput(Player player, String rawInput) {
        if (player == null || rawInput == null) return;
        SetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        String input = rawInput.trim();
        if (input.isEmpty()) return;

        // Überall verfügbar: "Stop" beendet den Setup-Wizard sofort
        if (input.equalsIgnoreCase("stop") || input.equalsIgnoreCase("_stop")) {
            stopSetup(player);
            return;
        }

        Step step = session.getCurrentStep();
        switch (step) {
            case CH1_1_LANGUAGE -> handleCh1_1(player, session, input);
            case CH1_2_TRANSLATE_DEFAULTS -> handleCh1_2(player, session, input);
            case CH2_1_SCOREBOARD -> handleCh2_1(player, session, input);
            case CH2_2_SCOREBOARD_HINT -> handleCh2_2(player, session, input);
            case CH3_1_RANKS -> handleCh3_1(player, session, input);
            case CH3_2_1_RANK_NAME -> handleCh3_2_1(player, session, input);
            case CH3_2_2_RANK_PREFIX -> handleCh3_2_2(player, session, input);
            case CH3_2_3_RANK_COLOR -> handleCh3_2_3(player, session, input);
            case CH3_2_4_RANK_HINT -> handleCh3_2_4(player, session, input);
            case CH3_3_RANK_BUY -> handleCh3_3(player, session, input);
            case CH3_4_RANK_HINT -> handleCh3_4(player, session, input);
            case CH4_1_AUTOMOD -> handleCh4_1(player, session, input);
            case CH4_2_AUTOMOD_HINT -> handleCh4_2(player, session, input);
            case CH5_1_ESSENTIALS -> handleCh5_1(player, session, input);
            case CH5_2_MESSAGING -> handleCh5_2(player, session, input);
            case CH6_1_ECONOMY -> handleCh6_1(player, session, input);
            case CH6_2_START_BALANCE -> handleCh6_2(player, session, input);
            case CH6_3_CURRENCY_SYMBOL -> handleCh6_3(player, session, input);
            case CH7_1_WARP -> handleCh7_1(player, session, input);
            case CH7_2_HOME -> handleCh7_2(player, session, input);
            case CH7_3_LOBBY -> handleCh7_3(player, session, input);
            case CH7_4_LOBBY_JOIN -> handleCh7_4(player, session, input);
            case CH7_5_LOBBY_LOCATION -> handleCh7_5(player, session, input);
            case CH7_6_WORLDSPAWN -> handleCh7_6(player, session, input);
            case CH8_MOTD -> handleCh8(player, session, input);
            case CH9_1_JOIN_MSG -> handleCh9_1(player, session, input);
            case CH9_2_LEAVE_MSG -> handleCh9_2(player, session, input);
            case CH10_RAM_COMPACTING -> handleCh10(player, session, input);
        }
    }

    // =========================================================================
    // STEP HANDLERS
    // =========================================================================

    private static void handleCh1_1(Player player, SetupSession session, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.equals("english") || lower.equals("englisch") || lower.equals("en")) {
            DataManager.setConfig("server.backbone.language", "englisch.lang");
            Lang.reload();
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch1_1.saved-en"));
            transitionTo(player, session, Step.CH2_1_SCOREBOARD);
        } else if (lower.equals("german") || lower.equals("deutsch") || lower.equals("de")) {
            DataManager.setConfig("server.backbone.language", "deutsch.lang");
            Lang.reload();
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch1_1.saved-de"));
            transitionTo(player, session, Step.CH1_2_TRANSLATE_DEFAULTS);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch1_1.invalid"));
        }
    }

    private static void handleCh1_2(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        if (yes) {
            translateDefaultConfigsToGerman();
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch1_2.saved-yes"));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch1_2.saved-no"));
        }
        transitionTo(player, session, Step.CH2_1_SCOREBOARD);
    }

    private static void handleCh2_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("scoreboard.enabled", yes);
        CommandRegistryManager.syncCommands();
        ScoreboardManager.reload();

        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch2_1.saved-yes"));
            transitionTo(player, session, Step.CH2_2_SCOREBOARD_HINT);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch2_1.saved-no"));
            transitionTo(player, session, Step.CH3_1_RANKS);
        }
    }

    private static void handleCh2_2(Player player, SetupSession session, String input) {
        if (isContinue(input)) {
            transitionTo(player, session, Step.CH3_1_RANKS);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.click-next"));
        }
    }

    private static void handleCh3_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        RankManager.setRanksEnabled(yes);
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_1.saved-yes"));
            transitionTo(player, session, Step.CH3_2_1_RANK_NAME);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_1.saved-no"));
            transitionTo(player, session, Step.CH4_1_AUTOMOD);
        }
    }

    private static void handleCh3_2_1(Player player, SetupSession session, String input) {
        if (isSkip(input)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.skipped"));
            transitionTo(player, session, Step.CH3_3_RANK_BUY);
            return;
        }
        if (!RankManager.isValidRankName(input)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_1.invalid"));
            return;
        }
        session.pendingRankName = input.trim();
        session.pendingRankId = session.pendingRankName;
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_1.saved", "name", session.pendingRankName));
        transitionTo(player, session, Step.CH3_2_2_RANK_PREFIX);
    }

    private static void handleCh3_2_2(Player player, SetupSession session, String input) {
        if (isSkip(input)) {
            session.pendingRankPrefix = "";
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_2.skipped"));
        } else {
            session.pendingRankPrefix = input.trim();
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_2.saved", "prefix", PrefixManager.formatColors(session.pendingRankPrefix)));
        }
        transitionTo(player, session, Step.CH3_2_3_RANK_COLOR);
    }

    private static void handleCh3_2_3(Player player, SetupSession session, String input) {
        String colorCode = parseColorChoice(input);
        if (colorCode == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_3.invalid"));
            return;
        }

        String name = session.pendingRankName.isBlank() ? "Player" : session.pendingRankName;
        String prefix = session.pendingRankPrefix;

        try {
            if (RankManager.exists(name)) {
                RankManager.setRankPrefix(name, prefix);
                RankManager.setRankColor(name, colorCode);
                RankManager.setDefaultRank(name);
            } else {
                Rank created = RankManager.createRank(name, colorCode, prefix);
                RankManager.setDefaultRank(created.getName());
            }
            Rank finalRank = RankManager.getRank(name);
            String displayColor = (finalRank != null) ? finalRank.getColor() : PrefixManager.formatColors(colorCode);
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_2_3.saved",
                    "color", displayColor,
                    "name", name,
                    "id", name));
            transitionTo(player, session, Step.CH3_2_4_RANK_HINT);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Lang.getPrefix() + Lang.get("rank.error", "error", e.getMessage()));
        }
    }

    private static void handleCh3_2_4(Player player, SetupSession session, String input) {
        if (isContinue(input)) {
            transitionTo(player, session, Step.CH3_3_RANK_BUY);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.click-next"));
        }
    }

    private static void handleCh3_3(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        de.basti.backbone.ranks.RankShop.setShopEnabled(yes);
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_3.saved-yes"));
            transitionTo(player, session, Step.CH3_4_RANK_HINT);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch3_3.saved-no"));
            transitionTo(player, session, Step.CH4_1_AUTOMOD);
        }
    }

    private static void handleCh3_4(Player player, SetupSession session, String input) {
        if (isContinue(input)) {
            transitionTo(player, session, Step.CH4_1_AUTOMOD);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.click-next"));
        }
    }

    private static void handleCh4_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.set("automod", "config.yml", "settings.enabled", yes);
        DataManager.set("automod", "config.yml", "settings.chat.scan", yes);
        AutoMod.initialize();
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch4_1.saved-yes"));
            transitionTo(player, session, Step.CH4_2_AUTOMOD_HINT);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch4_1.saved-no"));
            transitionTo(player, session, Step.CH5_1_ESSENTIALS);
        }
    }

    private static void handleCh4_2(Player player, SetupSession session, String input) {
        if (isContinue(input)) {
            transitionTo(player, session, Step.CH5_1_ESSENTIALS);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.click-next"));
        }
    }

    private static void handleCh5_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("essentials.enabled", yes);
        CommandRegistryManager.syncCommands();
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch5_1.saved-yes"));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch5_1.saved-no"));
        }
        transitionTo(player, session, Step.CH5_2_MESSAGING);
    }

    private static void handleCh5_2(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("chat.private-messages", yes);
        CommandRegistryManager.syncCommands();
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch5_2.saved-yes"));
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch5_2.saved-no"));
        }
        transitionTo(player, session, Step.CH6_1_ECONOMY);
    }

    private static void handleCh6_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("economy.enabled", yes);
        CommandRegistryManager.syncCommands();
        de.basti.backbone.util.vault.VaultManager.reload();
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_1.saved-yes"));
            transitionTo(player, session, Step.CH6_2_START_BALANCE);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_1.saved-no"));
            transitionTo(player, session, Step.CH7_1_WARP);
        }
    }

    private static void handleCh6_2(Player player, SetupSession session, String input) {
        String clean = input.replace(",", ".").trim();
        double amount;
        try {
            amount = Double.parseDouble(clean);
            if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_2.invalid"));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_2.invalid"));
            return;
        }
        double rounded = Math.round(amount * 100.0) / 100.0;
        DataManager.setConfig("economy.starting-balance", rounded);
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_2.saved", "amount", String.valueOf(rounded)));
        transitionTo(player, session, Step.CH6_3_CURRENCY_SYMBOL);
    }

    private static void handleCh6_3(Player player, SetupSession session, String input) {
        String clean = input.trim();
        if (clean.isEmpty() || clean.length() > 5) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_3.invalid"));
            return;
        }
        DataManager.setConfig("economy.currency-symbol", clean);
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch6_3.saved", "symbol", clean));
        transitionTo(player, session, Step.CH7_1_WARP);
    }

    private static void handleCh7_1(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("warps.enabled", yes);
        CommandRegistryManager.syncCommands();
        player.sendMessage(Lang.getPrefix() + Lang.get(yes ? "setup.ch7_1.saved-yes" : "setup.ch7_1.saved-no"));
        transitionTo(player, session, Step.CH7_2_HOME);
    }

    private static void handleCh7_2(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("homes.enabled", yes);
        CommandRegistryManager.syncCommands();
        player.sendMessage(Lang.getPrefix() + Lang.get(yes ? "setup.ch7_2.saved-yes" : "setup.ch7_2.saved-no"));
        transitionTo(player, session, Step.CH7_3_LOBBY);
    }

    private static void handleCh7_3(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("lobby.enabled", yes);
        CommandRegistryManager.syncCommands();
        if (yes) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch7_3.saved-yes"));
            transitionTo(player, session, Step.CH7_4_LOBBY_JOIN);
        } else {
            LobbyManager.setTeleportOnJoin(false);
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch7_3.saved-no"));
            transitionTo(player, session, Step.CH7_6_WORLDSPAWN);
        }
    }

    private static void handleCh7_4(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        LobbyManager.setTeleportOnJoin(yes);
        player.sendMessage(Lang.getPrefix() + Lang.get(yes ? "setup.ch7_4.saved-yes" : "setup.ch7_4.saved-no"));
        transitionTo(player, session, Step.CH7_5_LOBBY_LOCATION);
    }

    private static void handleCh7_5(Player player, SetupSession session, String input) {
        if (isSkip(input) || Boolean.FALSE.equals(parseYesNo(input))) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.skipped"));
            transitionTo(player, session, Step.CH7_6_WORLDSPAWN);
            return;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.equals("here") || lower.equals("_here") || lower.contains("steht") || lower.contains("position") || Boolean.TRUE.equals(parseYesNo(input))) {
            LobbyManager.setLobby(player.getLocation(), null);
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch7_5.saved"));
            transitionTo(player, session, Step.CH7_6_WORLDSPAWN);
        } else {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch7_5.invalid"));
        }
    }

    private static void handleCh7_6(Player player, SetupSession session, String input) {
        Boolean yes = parseYesNo(input);
        if (yes == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.invalid-yes-no"));
            return;
        }
        DataManager.setConfig("spawn.enabled", yes);
        CommandRegistryManager.syncCommands();
        player.sendMessage(Lang.getPrefix() + Lang.get(yes ? "setup.ch7_6.saved-yes" : "setup.ch7_6.saved-no"));
        transitionTo(player, session, Step.CH8_MOTD);
    }

    private static void handleCh8(Player player, SetupSession session, String input) {
        if (isSkip(input)) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.common.skipped"));
            transitionTo(player, session, Step.CH9_1_JOIN_MSG);
            return;
        }
        DataManager.setConfig("server.motd.normal", input);
        String preview = MotdFormatter.format(input);
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch8.saved"));
        player.sendMessage("§r" + preview);
        transitionTo(player, session, Step.CH9_1_JOIN_MSG);
    }

    private static void handleCh9_1(Player player, SetupSession session, String input) {
        if (isSkip(input) || Boolean.FALSE.equals(parseYesNo(input))) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch9_1.kept"));
            transitionTo(player, session, Step.CH9_2_LEAVE_MSG);
            return;
        }
        DataManager.setConfig("server.join", input);
        String preview = ScoreboardParser.formatJoinLeaveMessage(input, player);
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch9_1.saved", "preview", preview));
        transitionTo(player, session, Step.CH9_2_LEAVE_MSG);
    }

    private static void handleCh9_2(Player player, SetupSession session, String input) {
        if (isSkip(input) || Boolean.FALSE.equals(parseYesNo(input))) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch9_2.kept"));
            transitionTo(player, session, Step.CH10_RAM_COMPACTING);
            return;
        }
        DataManager.setConfig("server.leave", input);
        String preview = ScoreboardParser.formatJoinLeaveMessage(input, player);
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch9_2.saved", "preview", preview));
        transitionTo(player, session, Step.CH10_RAM_COMPACTING);
    }

    private static void handleCh10(Player player, SetupSession session, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        RamManager.RamMode mode = null;
        if (lower.equals("fast") || lower.equals("_fast") || lower.contains("schnell") || lower.contains("faster") || lower.equals("1")) {
            mode = RamManager.RamMode.FAST;
        } else if (lower.equals("balanced") || lower.equals("_balanced") || lower.contains("ausgewogen") || lower.equals("2")) {
            mode = RamManager.RamMode.BALANCED;
        } else if (lower.equals("light") || lower.equals("_light") || lower.contains("langsam") || lower.contains("leicht") || lower.contains("slower") || lower.equals("3")) {
            mode = RamManager.RamMode.LIGHT;
        }

        if (mode == null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch10.invalid"));
            return;
        }

        RamManager.setMode(mode);
        String modeNameKey = switch (mode) {
            case FAST -> "ram.mode.fast";
            case BALANCED -> "ram.mode.balanced";
            case LIGHT -> "ram.mode.light";
        };
        player.sendMessage(Lang.getPrefix() + Lang.get("setup.ch10.saved", "mode", Lang.get(modeNameKey)));
        finishSetup(player);
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    private static void transitionTo(Player player, SetupSession session, Step nextStep) {
        session.setCurrentStep(nextStep);
        player.sendMessage("");
        renderCurrentStep(player, session);
    }

    public static void renderCurrentStep(Player player, SetupSession session) {
        Step step = session.getCurrentStep();
        player.sendMessage(Lang.get("setup.divider"));

        switch (step) {
            case CH1_1_LANGUAGE -> {
                // Kapitel 1.1 fragt immer auf Englisch mit leuchtend grünen, dicken Buttons [English] [German]
                player.sendMessage(Lang.get("setup.ch1_1.title"));
                player.sendMessage(Lang.get("setup.ch1_1.question"));
                player.sendMessage(Lang.get("setup.ch1_1.hint"));
                sendButtonRow(player,
                        createButton(Lang.get("setup.ch1_1.btn-english"), "/backbone setup _input English", Lang.get("setup.ch1_1.hover-english")),
                        createButton(Lang.get("setup.ch1_1.btn-german"), "/backbone setup _input German", Lang.get("setup.ch1_1.hover-german"))
                );
            }
            case CH1_2_TRANSLATE_DEFAULTS -> {
                player.sendMessage(Lang.get("setup.ch1_2.title"));
                player.sendMessage(Lang.get("setup.ch1_2.question"));
                sendYesNoButtons(player);
            }
            case CH2_1_SCOREBOARD -> {
                player.sendMessage(Lang.get("setup.ch2_1.title"));
                player.sendMessage(Lang.get("setup.ch2_1.question"));
                sendYesNoButtons(player);
            }
            case CH2_2_SCOREBOARD_HINT -> {
                player.sendMessage(Lang.get("setup.ch2_2.title"));
                for (String line : Lang.getList("setup.ch2_2.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendNextButton(player);
            }
            case CH3_1_RANKS -> {
                player.sendMessage(Lang.get("setup.ch3_1.title"));
                player.sendMessage(Lang.get("setup.ch3_1.question"));
                sendYesNoButtons(player);
            }
            case CH3_2_1_RANK_NAME -> {
                player.sendMessage(Lang.get("setup.ch3_2_1.title"));
                player.sendMessage(Lang.get("setup.ch3_2_1.question"));
                player.sendMessage(Lang.get("setup.ch3_2_1.hint"));
                sendSkipButton(player);
            }
            case CH3_2_2_RANK_PREFIX -> {
                player.sendMessage(Lang.get("setup.ch3_2_2.title"));
                player.sendMessage(Lang.get("setup.ch3_2_2.question", "name", session.pendingRankName));
                player.sendMessage(Lang.get("setup.ch3_2_2.hint"));
                sendSkipButton(player);
            }
            case CH3_2_3_RANK_COLOR -> {
                player.sendMessage(Lang.get("setup.ch3_2_3.title"));
                player.sendMessage(Lang.get("setup.ch3_2_3.question", "name", session.pendingRankName));
                sendButtonRow(player,
                        createButton(Lang.get("setup.ch3_2_3.btn-white"), "/backbone setup _input &f", Lang.get("setup.ch3_2_3.hover-color", "code", "&f")),
                        createButton(Lang.get("setup.ch3_2_3.btn-blue"), "/backbone setup _input &9", Lang.get("setup.ch3_2_3.hover-color", "code", "&9")),
                        createButton(Lang.get("setup.ch3_2_3.btn-red"), "/backbone setup _input &c", Lang.get("setup.ch3_2_3.hover-color", "code", "&c")),
                        createButton(Lang.get("setup.ch3_2_3.btn-green"), "/backbone setup _input &a", Lang.get("setup.ch3_2_3.hover-color", "code", "&a")),
                        createButton(Lang.get("setup.ch3_2_3.btn-yellow"), "/backbone setup _input &e", Lang.get("setup.ch3_2_3.hover-color", "code", "&e")),
                        createButton(Lang.get("setup.ch3_2_3.btn-purple"), "/backbone setup _input &5", Lang.get("setup.ch3_2_3.hover-color", "code", "&5")),
                        createButton(Lang.get("setup.ch3_2_3.btn-black"), "/backbone setup _input &0", Lang.get("setup.ch3_2_3.hover-color", "code", "&0"))
                );
            }
            case CH3_2_4_RANK_HINT -> {
                String id = session.pendingRankId.isBlank() ? "default" : session.pendingRankId;
                player.sendMessage(Lang.get("setup.ch3_2_4.title"));
                for (String line : Lang.getList("setup.ch3_2_4.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line)
                            .replace("{rank}", session.pendingRankName)
                            .replace("{id}", id));
                }
                sendNextButton(player);
            }
            case CH3_3_RANK_BUY -> {
                player.sendMessage(Lang.get("setup.ch3_3.title"));
                player.sendMessage(Lang.get("setup.ch3_3.question"));
                sendYesNoButtons(player);
            }
            case CH3_4_RANK_HINT -> {
                player.sendMessage(Lang.get("setup.ch3_4.title"));
                for (String line : Lang.getList("setup.ch3_4.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendNextButton(player);
            }
            case CH4_1_AUTOMOD -> {
                player.sendMessage(Lang.get("setup.ch4_1.title"));
                player.sendMessage(Lang.get("setup.ch4_1.question"));
                sendYesNoButtons(player);
            }
            case CH4_2_AUTOMOD_HINT -> {
                player.sendMessage(Lang.get("setup.ch4_2.title"));
                for (String line : Lang.getList("setup.ch4_2.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendNextButton(player);
            }
            case CH5_1_ESSENTIALS -> {
                player.sendMessage(Lang.get("setup.ch5_1.title"));
                player.sendMessage(Lang.get("setup.ch5_1.question"));
                player.sendMessage(Lang.get("setup.ch5_1.hint"));
                sendYesNoButtons(player);
            }
            case CH5_2_MESSAGING -> {
                player.sendMessage(Lang.get("setup.ch5_2.title"));
                player.sendMessage(Lang.get("setup.ch5_2.question"));
                sendYesNoButtons(player);
            }
            case CH6_1_ECONOMY -> {
                player.sendMessage(Lang.get("setup.ch6_1.title"));
                player.sendMessage(Lang.get("setup.ch6_1.question"));
                sendYesNoButtons(player);
            }
            case CH6_2_START_BALANCE -> {
                player.sendMessage(Lang.get("setup.ch6_2.title"));
                player.sendMessage(Lang.get("setup.ch6_2.question"));
                sendButtonRow(player,
                        createButton("§a§l[1000]", "/backbone setup _input 1000", Lang.get("setup.ch6_2.hover-preset", "amount", "1000")),
                        createButton("§a§l[5000]", "/backbone setup _input 5000", Lang.get("setup.ch6_2.hover-preset", "amount", "5000")),
                        createButton("§a§l[10000]", "/backbone setup _input 10000", Lang.get("setup.ch6_2.hover-preset", "amount", "10000"))
                );
            }
            case CH6_3_CURRENCY_SYMBOL -> {
                player.sendMessage(Lang.get("setup.ch6_3.title"));
                player.sendMessage(Lang.get("setup.ch6_3.question"));
                sendButtonRow(player,
                        createButton("§a§l[€]", "/backbone setup _input €", Lang.get("setup.ch6_3.hover-preset", "symbol", "€")),
                        createButton("§a§l[$]", "/backbone setup _input $", Lang.get("setup.ch6_3.hover-preset", "symbol", "$")),
                        createButton("§a§l[C]", "/backbone setup _input C", Lang.get("setup.ch6_3.hover-preset", "symbol", "C"))
                );
            }
            case CH7_1_WARP -> {
                player.sendMessage(Lang.get("setup.ch7_1.title"));
                player.sendMessage(Lang.get("setup.ch7_1.question"));
                sendYesNoButtons(player);
            }
            case CH7_2_HOME -> {
                player.sendMessage(Lang.get("setup.ch7_2.title"));
                player.sendMessage(Lang.get("setup.ch7_2.question"));
                sendYesNoButtons(player);
            }
            case CH7_3_LOBBY -> {
                player.sendMessage(Lang.get("setup.ch7_3.title"));
                player.sendMessage(Lang.get("setup.ch7_3.question"));
                sendYesNoButtons(player);
            }
            case CH7_4_LOBBY_JOIN -> {
                player.sendMessage(Lang.get("setup.ch7_4.title"));
                player.sendMessage(Lang.get("setup.ch7_4.question"));
                sendYesNoButtons(player);
            }
            case CH7_5_LOBBY_LOCATION -> {
                player.sendMessage(Lang.get("setup.ch7_5.title"));
                player.sendMessage(Lang.get("setup.ch7_5.question"));
                sendButtonRow(player,
                        createButton(Lang.get("setup.ch7_5.btn-here"), "/backbone setup _input _here", Lang.get("setup.ch7_5.hover-here")),
                        createButton(Lang.get("setup.common.btn-skip"), "/backbone setup _input _skip", Lang.get("setup.common.hover-skip"))
                );
            }
            case CH7_6_WORLDSPAWN -> {
                player.sendMessage(Lang.get("setup.ch7_6.title"));
                player.sendMessage(Lang.get("setup.ch7_6.question"));
                sendYesNoButtons(player);
            }
            case CH8_MOTD -> {
                player.sendMessage(Lang.get("setup.ch8.title"));
                for (String line : Lang.getList("setup.ch8.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendSkipButton(player);
            }
            case CH9_1_JOIN_MSG -> {
                player.sendMessage(Lang.get("setup.ch9_1.title"));
                for (String line : Lang.getList("setup.ch9_1.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendNoButtonOnly(player);
            }
            case CH9_2_LEAVE_MSG -> {
                player.sendMessage(Lang.get("setup.ch9_2.title"));
                for (String line : Lang.getList("setup.ch9_2.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                sendNoButtonOnly(player);
            }
            case CH10_RAM_COMPACTING -> {
                player.sendMessage(Lang.get("setup.ch10.title"));
                for (String line : Lang.getList("setup.ch10.lines")) {
                    player.sendMessage(PrefixManager.formatColors(line));
                }
                player.spigot().sendMessage(createButton(Lang.get("setup.ch10.btn-fast"), "/backbone setup _input _fast", Lang.get("setup.ch10.hover-fast")));
                player.spigot().sendMessage(createButton(Lang.get("setup.ch10.btn-balanced"), "/backbone setup _input _balanced", Lang.get("setup.ch10.hover-balanced")));
                player.spigot().sendMessage(createButton(Lang.get("setup.ch10.btn-light"), "/backbone setup _input _light", Lang.get("setup.ch10.hover-light")));
            }
        }

        player.sendMessage(Lang.get("setup.stop-footer"));
        player.sendMessage(Lang.get("setup.divider"));
    }

    // =========================================================================
    // BUTTON & PARSING HELPERS
    // =========================================================================

    private static void sendYesNoButtons(Player player) {
        sendButtonRow(player,
                createButton(Lang.get("setup.common.btn-yes"), "/backbone setup _input yes", Lang.get("setup.common.hover-yes")),
                createButton(Lang.get("setup.common.btn-no"), "/backbone setup _input no", Lang.get("setup.common.hover-no"))
        );
    }

    private static void sendNoButtonOnly(Player player) {
        sendButtonRow(player,
                createButton(Lang.get("setup.common.btn-no"), "/backbone setup _input no", Lang.get("setup.common.hover-no"))
        );
    }

    private static void sendNextButton(Player player) {
        sendButtonRow(player,
                createButton(Lang.get("setup.common.btn-next"), "/backbone setup _input _next", Lang.get("setup.common.hover-next"))
        );
    }

    private static void sendSkipButton(Player player) {
        sendButtonRow(player,
                createButton(Lang.get("setup.common.btn-skip"), "/backbone setup _input _skip", Lang.get("setup.common.hover-skip"))
        );
    }

    private static TextComponent createButton(String text, String command, String hoverText) {
        TextComponent comp = new TextComponent(text);
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
        return comp;
    }

    private static void sendButtonRow(Player player, TextComponent... buttons) {
        TextComponent row = new TextComponent("");
        for (int i = 0; i < buttons.length; i++) {
            row.addExtra(buttons[i]);
            if (i + 1 < buttons.length) {
                row.addExtra(new TextComponent("  "));
            }
        }
        player.spigot().sendMessage(row);
    }

    private static void sendClearPadding(Player player) {
        for (int i = 0; i < 6; i++) {
            player.sendMessage("");
        }
    }

    private static Boolean parseYesNo(String input) {
        if (input == null) return null;
        String lower = input.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "ja", "yes", "j", "y", "true", "aktivieren", "enable", "an", "on" -> true;
            case "nein", "no", "n", "false", "deaktivieren", "disable", "aus", "off" -> false;
            default -> null;
        };
    }

    private static boolean isSkip(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase(Locale.ROOT);
        return lower.equals("_skip") || lower.equals("skip") || lower.equals("überspringen") || lower.equals("ueberspringen");
    }

    private static boolean isContinue(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase(Locale.ROOT);
        return lower.equals("_next") || lower.equals("weiter") || lower.equals("next")
                || lower.equals("continue") || lower.equals("ok") || lower.equals("ja") || lower.equals("yes");
    }

    private static String parseColorChoice(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "weiß", "weiss", "white", "&f", "§f", "f" -> "&f";
            case "blau", "blue", "&9", "§9", "9", "&1", "§1" -> "&9";
            case "rot", "red", "&c", "§c", "c", "&4", "§4" -> "&c";
            case "grün", "gruen", "green", "&a", "§a", "a", "&2", "§2" -> "&a";
            case "gelb", "yellow", "&e", "§e", "e", "&6", "§6" -> "&e";
            case "lila", "purple", "violett", "&5", "§5", "5", "&d", "§d" -> "&5";
            case "schwarz", "black", "&0", "§0", "0" -> "&0";
            default -> RankManager.isValidSingleColor(clean) ? clean : null;
        };
    }

    private static String generateRankId(String name) {
        if (name == null || name.isBlank()) return "default";
        String id = name.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "");
        if (id.isEmpty()) return "default";
        if (id.length() > 32) id = id.substring(0, 32);
        return id;
    }

    /**
     * Übersetzt die Standardwerte in config.yml, automod/config.yml und scoreboards/example.yml
     * automatisch auf Deutsch (Kapitel 1.2).
     */
    public static void translateDefaultConfigsToGerman() {
        // 1. config.yml
        FileConfiguration cfg = DataManager.getConfig();
        if ("My Minecraft Server".equals(cfg.getString("server.name"))) {
            cfg.set("server.name", "Mein Minecraft Server");
        }
        if ("A Minecraft Server".equals(cfg.getString("server.motd.normal"))) {
            cfg.set("server.motd.normal", "Ein Minecraft Server");
        }
        cfg.set("server.motd.maintenance.text", "&cDer Server befindet sich im Wartungsmodus!\n&7Wir sind bald wieder da!");
        cfg.set("server.maintenance-message", "&cDer Server befindet sich aktuell im Wartungsmodus!\n&7Bitte versuche es später erneut.");
        cfg.set("server.join", "{pluginPrefix} &e{playername} &7ist beigetreten!");
        cfg.set("server.leave", "{pluginPrefix} &e{playername} &7hat den Server verlassen!");
        if ("Coins".equals(cfg.getString("economy.currency-name"))) {
            cfg.set("economy.currency-name", "Münzen");
        }
        DataManager.saveConfig();

        // 2. automod/config.yml
        FileConfiguration automodCfg = DataManager.getConfiguration("automod", "config.yml");
        automodCfg.set("messages.automod.warn", "{automod_prefix} &rVerwarnung: Diese Nachricht wurde von AutoMod markiert. &7Grund: &e{reason}");
        automodCfg.set("messages.automod.mute", "{automod_prefix} &cDu wurdest aufgrund wiederholter Verstöße für {until} gemutet! &7Grund: &e{reason}");
        automodCfg.set("messages.automod.kick", "{automod_prefix}\n&cDu wurdest vom AutoMod gekickt!\n&7Grund: &e{reason}");
        automodCfg.set("messages.automod.ban", "{automod_prefix}\n\n&c&lDu wurdest vom Server gebannt!\n\n&7Grund: &e{reason}\n&7Verbleibende Zeit: &c{until}");
        automodCfg.set("messages.manual.warn", "&cDu wurdest von &e{operator} &cverwarnt! &7Grund: &e{reason}");
        automodCfg.set("messages.manual.mute", "&cDu wurdest von &e{operator} &cfür {until} gemutet! &7Grund: &e{reason}");
        automodCfg.set("messages.manual.kick", "&cDu wurdest von &e{operator} &cgekickt!\n&7Grund: &e{reason}");
        automodCfg.set("messages.manual.ban", "&c&lDu wurdest vom Server gebannt!\n\n&7Grund: &e{reason}\n&7Gebannt von: &e{operator}\n&7Verbleibende Zeit: &c{until}");
        automodCfg.set("messages.chat-muted", "{automod_prefix} &cDu bist momentan gemutet. &7({until})");
        DataManager.saveConfiguration("automod", "config.yml", automodCfg);
        AutoMod.initialize();

        // 3. scoreboards/example.yml (nur übersetzen, wenn noch auf dem unveränderten englischen Standard)
        File folder = DataManager.getFolder("scoreboards");
        File example = new File(folder, "example.yml");
        if (example.exists()) {
            FileConfiguration sbCfg = YamlConfiguration.loadConfiguration(example);
            if ("Example Scoreboard".equals(sbCfg.getString("name"))) {
                sbCfg.options().setHeader(List.of(
                        "===========================================================",
                        "             Backbone Scoreboard Konfiguration             ",
                        "===========================================================",
                        "Dieses Scoreboard dient als Vorlage und Dokumentation aller Optionen.",
                        "Standardmäßig ist 'enabled: false' gesetzt, damit es nicht sofort aktiv ist.",
                        "Setze 'enabled: true' und führe '/sb reload' aus, um es zu aktivieren.",
                        "==========================================================="
                ));
                sbCfg.set("name", "Beispiel Scoreboard");
                sbCfg.set("lines", List.of(
                        "&7&m---------------------",
                        "&fSpieler: &a{playername}",
                        "<if {rank} != \"\">&fRang: &e{rank}</if>",
                        "&fGeld: &e{cash} {cashSymbol}",
                        "<if {cash} >= 10000>&6★ VIP-Status<else>&7Spieler-Status</if>",
                        "",
                        "&fStatus: <if {mute} = true>&cGemutet ({muteUntil})<else>&aAktiv</if>",
                        "<switch sec=10>&fWelt: &b{worldName}</s>&fTPS: &e{TPS}</s>&fPing: &a{ping}ms</s>",
                        "<switch sec=15>&fOnline: &a{players}/{slots}</s>&fRAM: &e{usedmb}/{maxmb} MB</s>&fCPU: &e{cpu}%</s>",
                        "",
                        "&7&o{hh}:{mm}:{ss}",
                        "&7&m---------------------"
                ));
                try {
                    sbCfg.save(example);
                } catch (IOException ignored) {}
                ScoreboardManager.reload();
            }
        }
    }
}
