package de.basti.backbone;

import de.basti.backbone.bstats.Metrics;
import de.basti.backbone.commands.core.BackboneCommand;
import de.basti.backbone.commands.core.CompactCommand;
import de.basti.backbone.commands.core.HealthCommand;
import de.basti.backbone.commands.core.RamCommand;
import de.basti.backbone.commands.economy.MoneyCommand;
import de.basti.backbone.commands.economy.PayCommand;
import de.basti.backbone.commands.gamemode.*;
import de.basti.backbone.commands.inventory.EnderchestCommand;
import de.basti.backbone.commands.inventory.InventoryCommand;
import de.basti.backbone.commands.maintenance.MaintenanceCommand;
import de.basti.backbone.commands.namecolor.NameColorCommand;
import de.basti.backbone.commands.namecolor.RemoveNameColorCommand;
import de.basti.backbone.commands.namecolor.SetNameColorCommand;
import de.basti.backbone.commands.prefix.PrefixCommand;
import de.basti.backbone.commands.prefix.RemoveprefixCommand;
import de.basti.backbone.commands.prefix.SetprefixCommand;
import de.basti.backbone.commands.punish.BanCommand;
import de.basti.backbone.commands.punish.MuteCommand;
import de.basti.backbone.commands.punish.UnbanCommand;
import de.basti.backbone.commands.punish.UnmuteCommand;
import de.basti.backbone.commands.rank.RankCommand;
import de.basti.backbone.commands.scoreboard.ScoreboardCommand;
import de.basti.backbone.commands.teleport.*;
import de.basti.backbone.commands.world.TimeCommand;
import de.basti.backbone.commands.world.WeatherCommand;
import de.basti.backbone.events.chat.ChatEvent;
import de.basti.backbone.events.inventory.InventoryClickEvent;
import de.basti.backbone.events.inventory.InventoryCloseEvent;
import de.basti.backbone.events.inventory.InventoryDragEvent;
import de.basti.backbone.events.player.*;
import de.basti.backbone.events.scoreboard.ScoreboardJoinEvent;
import de.basti.backbone.events.scoreboard.ScoreboardQuitEvent;
import de.basti.backbone.events.scoreboard.ScoreboardWorldChangeEvent;
import de.basti.backbone.events.server.ServerListPingEvent;
import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.CommandRegistryManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.SetupWizardManager;
import de.basti.backbone.util.HideManager;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.RamManager;
import de.basti.backbone.util.TpsTracker;
import de.basti.backbone.util.automod.core.AutoMod;
import de.basti.backbone.util.inventory.OfflineInventoryManager;
import de.basti.backbone.util.scoreboard.ScoreboardManager;
import de.basti.backbone.util.vault.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class Backbone extends JavaPlugin {

    private static Backbone instance;
    private static final Logger logger = Logger.getLogger("Backbone");

    @Override
    public void onEnable() {
        instance = this;
        infoMsg("Enabling Backbone v" + getDescription().getVersion() + "...");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        DataManager.init(this);
        Lang.init(this);
        AutoMod.initialize();
        RankManager.init(this);
        PrefixManager.init(this);
        VaultManager.init(this);
        HideManager.init(this);
        TpsTracker.start(this);
        ScoreboardManager.init(this);
        registerCommands();
        CommandRegistryManager.init(this);
        RamManager.init(this);
        bStats();
        registerListeners();
    }

    public static Backbone getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        RamManager.shutdown();
        HideManager.shutdown();
        ScoreboardManager.shutdown();
        VaultManager.shutdown();
        RankManager.shutdown();
        PrefixManager.shutdown();
        SetupWizardManager.shutdown();
        CommandRegistryManager.shutdown();
        de.basti.backbone.util.message.ActionbarManager.shutdown();

        // Falls JAR-Datei oder Datenordner gelöscht wurden (Deinstallation / Reset),
        // darf beim Herunterfahren keinesfalls etwas neu erstellt oder gespeichert werden!
        if (isPluginUninstalled()) {
            infoMsg("Plugin JAR or data folder was deleted. Skipping save on shutdown.");
            return;
        }

        // Inventare aller aktuell online befindlichen Spieler sichern
        for (Player player : Bukkit.getOnlinePlayers()) {
            OfflineInventoryManager.savePlayer(player);
        }

        DataManager.saveAll();
    }

    /**
     * Prüft, ob das Plugin während der Serverlaufzeit deinstalliert oder dessen Datenordner gelöscht wurde.
     * Wenn die JAR-Datei oder der Datenordner nicht mehr existieren, wird jegliches Neuerstellen
     * und Speichern beim Herunterfahren blockiert.
     */
    public boolean isPluginUninstalled() {
        try {
            java.io.File jarFile = getFile();
            if (jarFile != null && !jarFile.exists()) {
                return true;
            }
        } catch (Throwable ignored) {}

        java.io.File dataFolder = getDataFolder();
        if (dataFolder != null && !dataFolder.exists()) {
            return true;
        }

        return false;
    }

    private void registerCommands() {
        infoMsg("Registering commands...");

        // ==========================================
        // Core / Plugin-Befehle
        // ==========================================
        if (getCommand("backbone") != null) {
            BackboneCommand backboneCmd = new BackboneCommand();
            getCommand("backbone").setExecutor(backboneCmd);
            getCommand("backbone").setTabCompleter(backboneCmd);
        }
        if (getCommand("compact") != null) {
            CompactCommand compactCmd = new CompactCommand();
            getCommand("compact").setExecutor(compactCmd);
            getCommand("compact").setTabCompleter(compactCmd);
        }
        if (getCommand("ram") != null) {
            RamCommand ramCmd = new RamCommand();
            getCommand("ram").setExecutor(ramCmd);
            getCommand("ram").setTabCompleter(ramCmd);
        }
        if (getCommand("health") != null) {
            HealthCommand healthCmd = new HealthCommand();
            getCommand("health").setExecutor(healthCmd);
            getCommand("health").setTabCompleter(healthCmd);
        }

        // ==========================================
        // Moderation / Strafen (Punish)
        // ==========================================
        if (getCommand("ban") != null) {
            BanCommand banCmd = new BanCommand();
            getCommand("ban").setExecutor(banCmd);
            getCommand("ban").setTabCompleter(banCmd);
        }
        if (getCommand("unban") != null) {
            UnbanCommand unbanCmd = new UnbanCommand();
            getCommand("unban").setExecutor(unbanCmd);
            getCommand("unban").setTabCompleter(unbanCmd);
        }
        if (getCommand("mute") != null) {
            MuteCommand muteCmd = new MuteCommand();
            getCommand("mute").setExecutor(muteCmd);
            getCommand("mute").setTabCompleter(muteCmd);
        }
        if (getCommand("unmute") != null) {
            UnmuteCommand unmuteCmd = new UnmuteCommand();
            getCommand("unmute").setExecutor(unmuteCmd);
            getCommand("unmute").setTabCompleter(unmuteCmd);
        }
        if (getCommand("warn") != null) {
            de.basti.backbone.commands.punish.WarnCommand warnCmd = new de.basti.backbone.commands.punish.WarnCommand();
            getCommand("warn").setExecutor(warnCmd);
            getCommand("warn").setTabCompleter(warnCmd);
        }
        if (getCommand("warns") != null) {
            de.basti.backbone.commands.punish.WarnsCommand warnsCmd = new de.basti.backbone.commands.punish.WarnsCommand();
            getCommand("warns").setExecutor(warnsCmd);
            getCommand("warns").setTabCompleter(warnsCmd);
        }
        if (getCommand("kick") != null) {
            de.basti.backbone.commands.punish.KickCommand kickCmd = new de.basti.backbone.commands.punish.KickCommand();
            getCommand("kick").setExecutor(kickCmd);
            getCommand("kick").setTabCompleter(kickCmd);
        }

        // ==========================================
        // Präfix-Befehle
        // ==========================================
        if (getCommand("prefix") != null) {
            PrefixCommand prefixCmd = new PrefixCommand();
            getCommand("prefix").setExecutor(prefixCmd);
            getCommand("prefix").setTabCompleter(prefixCmd);
        }
        if (getCommand("setprefix") != null) {
            SetprefixCommand setprefixCmd = new SetprefixCommand();
            getCommand("setprefix").setExecutor(setprefixCmd);
            getCommand("setprefix").setTabCompleter(setprefixCmd);
        }
        if (getCommand("removeprefix") != null) {
            RemoveprefixCommand removeprefixCmd = new RemoveprefixCommand();
            getCommand("removeprefix").setExecutor(removeprefixCmd);
            getCommand("removeprefix").setTabCompleter(removeprefixCmd);
        }

        // ==========================================
        // Namensfarben-Befehle
        // ==========================================
        if (getCommand("namecolor") != null) {
            NameColorCommand nameColorCmd = new NameColorCommand();
            getCommand("namecolor").setExecutor(nameColorCmd);
            getCommand("namecolor").setTabCompleter(nameColorCmd);
        }
        if (getCommand("setnamecolor") != null) {
            SetNameColorCommand setNameColorCmd = new SetNameColorCommand();
            getCommand("setnamecolor").setExecutor(setNameColorCmd);
            getCommand("setnamecolor").setTabCompleter(setNameColorCmd);
        }
        if (getCommand("removenamecolor") != null) {
            RemoveNameColorCommand removeNameColorCmd = new RemoveNameColorCommand();
            getCommand("removenamecolor").setExecutor(removeNameColorCmd);
            getCommand("removenamecolor").setTabCompleter(removeNameColorCmd);
        }

        // ==========================================
        // Ränge & Berechtigungen
        // ==========================================
        if (getCommand("rank") != null) {
            RankCommand rankCmd = new RankCommand();
            getCommand("rank").setExecutor(rankCmd);
            getCommand("rank").setTabCompleter(rankCmd);
        }
        if (getCommand("setrank") != null) {
            RankCommand setrankCmd = new RankCommand();
            getCommand("setrank").setExecutor(setrankCmd);
            getCommand("setrank").setTabCompleter(setrankCmd);
        }

        // ==========================================
        // Inventar & Enderchest (Online & Offline)
        // ==========================================
        if (getCommand("inventory") != null) {
            InventoryCommand invCmd = new InventoryCommand();
            getCommand("inventory").setExecutor(invCmd);
            getCommand("inventory").setTabCompleter(invCmd);
        }
        if (getCommand("enderchest") != null) {
            EnderchestCommand ecCmd = new EnderchestCommand();
            getCommand("enderchest").setExecutor(ecCmd);
            getCommand("enderchest").setTabCompleter(ecCmd);
        }
        if (getCommand("craft") != null) {
            de.basti.backbone.commands.inventory.CraftCommand craftCmd = new de.basti.backbone.commands.inventory.CraftCommand();
            getCommand("craft").setExecutor(craftCmd);
            getCommand("craft").setTabCompleter(craftCmd);
        }
        if (getCommand("trash") != null) {
            de.basti.backbone.commands.inventory.TrashCommand trashCmd = new de.basti.backbone.commands.inventory.TrashCommand();
            getCommand("trash").setExecutor(trashCmd);
            getCommand("trash").setTabCompleter(trashCmd);
        }

        // ==========================================
        // Flug-, Unsichtbarkeits- & Spielmodus-Befehle
        // ==========================================
        if (getCommand("fly") != null) {
            FlyCommand flyCmd = new FlyCommand();
            getCommand("fly").setExecutor(flyCmd);
            getCommand("fly").setTabCompleter(flyCmd);
        }
        if (getCommand("hide") != null) {
            HideCommand hideCmd = new HideCommand();
            getCommand("hide").setExecutor(hideCmd);
            getCommand("hide").setTabCompleter(hideCmd);
        }
        if (getCommand("gamemode") != null) {
            GameModeCommand gmCmd = new GameModeCommand();
            getCommand("gamemode").setExecutor(gmCmd);
            getCommand("gamemode").setTabCompleter(gmCmd);
        }
        if (getCommand("creative") != null) {
            CreativeCommand creativeCmd = new CreativeCommand();
            getCommand("creative").setExecutor(creativeCmd);
            getCommand("creative").setTabCompleter(creativeCmd);
        }
        if (getCommand("survival") != null) {
            SurvivalCommand survivalCmd = new SurvivalCommand();
            getCommand("survival").setExecutor(survivalCmd);
            getCommand("survival").setTabCompleter(survivalCmd);
        }
        if (getCommand("adventure") != null) {
            AdventureCommand adventureCmd = new AdventureCommand();
            getCommand("adventure").setExecutor(adventureCmd);
            getCommand("adventure").setTabCompleter(adventureCmd);
        }
        if (getCommand("spectator") != null) {
            SpectatorCommand spectatorCmd = new SpectatorCommand();
            getCommand("spectator").setExecutor(spectatorCmd);
            getCommand("spectator").setTabCompleter(spectatorCmd);
        }

        // ==========================================
        // Spieler-Hilfsbefehle (Heal, Feed, Speed, Skull)
        // ==========================================
        if (getCommand("heal") != null) {
            de.basti.backbone.commands.player.HealCommand healCmd = new de.basti.backbone.commands.player.HealCommand();
            getCommand("heal").setExecutor(healCmd);
            getCommand("heal").setTabCompleter(healCmd);
        }
        if (getCommand("feed") != null) {
            de.basti.backbone.commands.player.FeedCommand feedCmd = new de.basti.backbone.commands.player.FeedCommand();
            getCommand("feed").setExecutor(feedCmd);
            getCommand("feed").setTabCompleter(feedCmd);
        }
        if (getCommand("speed") != null) {
            de.basti.backbone.commands.player.SpeedCommand speedCmd = new de.basti.backbone.commands.player.SpeedCommand();
            getCommand("speed").setExecutor(speedCmd);
            getCommand("speed").setTabCompleter(speedCmd);
        }
        if (getCommand("skull") != null) {
            de.basti.backbone.commands.player.SkullCommand skullCmd = new de.basti.backbone.commands.player.SkullCommand();
            getCommand("skull").setExecutor(skullCmd);
            getCommand("skull").setTabCompleter(skullCmd);
        }

        // ==========================================
        // Wartungsmodus (Maintenance)
        // ==========================================
        if (getCommand("maintenance") != null) {
            MaintenanceCommand maintCmd = new MaintenanceCommand();
            getCommand("maintenance").setExecutor(maintCmd);
            getCommand("maintenance").setTabCompleter(maintCmd);
        }

        // ==========================================
        // Zeit-Befehle
        // ==========================================
        if (getCommand("day") != null) {
            TimeCommand dayCmd = new TimeCommand(TimeCommand.TimeType.DAY);
            getCommand("day").setExecutor(dayCmd);
            getCommand("day").setTabCompleter(dayCmd);
        }
        if (getCommand("night") != null) {
            TimeCommand nightCmd = new TimeCommand(TimeCommand.TimeType.NIGHT);
            getCommand("night").setExecutor(nightCmd);
            getCommand("night").setTabCompleter(nightCmd);
        }
        if (getCommand("sunrise") != null) {
            TimeCommand sunriseCmd = new TimeCommand(TimeCommand.TimeType.SUNRISE);
            getCommand("sunrise").setExecutor(sunriseCmd);
            getCommand("sunrise").setTabCompleter(sunriseCmd);
        }
        if (getCommand("sunset") != null) {
            TimeCommand sunsetCmd = new TimeCommand(TimeCommand.TimeType.SUNSET);
            getCommand("sunset").setExecutor(sunsetCmd);
            getCommand("sunset").setTabCompleter(sunsetCmd);
        }

        // ==========================================
        // Wetter-Befehle
        // ==========================================
        if (getCommand("sun") != null) {
            WeatherCommand sunCmd = new WeatherCommand(WeatherCommand.WeatherType.SUN);
            getCommand("sun").setExecutor(sunCmd);
            getCommand("sun").setTabCompleter(sunCmd);
        }
        if (getCommand("rain") != null) {
            WeatherCommand rainCmd = new WeatherCommand(WeatherCommand.WeatherType.RAIN);
            getCommand("rain").setExecutor(rainCmd);
            getCommand("rain").setTabCompleter(rainCmd);
        }
        if (getCommand("storm") != null) {
            WeatherCommand stormCmd = new WeatherCommand(WeatherCommand.WeatherType.STORM);
            getCommand("storm").setExecutor(stormCmd);
            getCommand("storm").setTabCompleter(stormCmd);
        }

        // ==========================================
        // Teleport-Befehle (TPA)
        // ==========================================
        if (getCommand("tpa") != null) {
            TpaCommand tpaCmd = new TpaCommand();
            getCommand("tpa").setExecutor(tpaCmd);
            getCommand("tpa").setTabCompleter(tpaCmd);
        }
        if (getCommand("tpaccept") != null) {
            TpAcceptCommand tpAcceptCmd = new TpAcceptCommand();
            getCommand("tpaccept").setExecutor(tpAcceptCmd);
            getCommand("tpaccept").setTabCompleter(tpAcceptCmd);
        }
        if (getCommand("tpdeny") != null) {
            TpDenyCommand tpDenyCmd = new TpDenyCommand();
            getCommand("tpdeny").setExecutor(tpDenyCmd);
            getCommand("tpdeny").setTabCompleter(tpDenyCmd);
        }

        // ==========================================
        // Lobby-Befehle
        // ==========================================
        if (getCommand("lobby") != null) {
            LobbyCommand lobbyCmd = new LobbyCommand();
            getCommand("lobby").setExecutor(lobbyCmd);
            getCommand("lobby").setTabCompleter(lobbyCmd);
        }
        if (getCommand("setlobby") != null) {
            SetLobbyCommand setLobbyCmd = new SetLobbyCommand();
            getCommand("setlobby").setExecutor(setLobbyCmd);
            getCommand("setlobby").setTabCompleter(setLobbyCmd);
        }

        // ==========================================
        // Warp-Befehle
        // ==========================================
        if (getCommand("warp") != null) {
            WarpCommand warpCmd = new WarpCommand();
            getCommand("warp").setExecutor(warpCmd);
            getCommand("warp").setTabCompleter(warpCmd);
        }
        if (getCommand("warps") != null) {
            WarpCommand warpsCmd = new WarpCommand();
            getCommand("warps").setExecutor(warpsCmd);
            getCommand("warps").setTabCompleter(warpsCmd);
        }
        if (getCommand("setwarp") != null) {
            SetWarpCommand setWarpCmd = new SetWarpCommand();
            getCommand("setwarp").setExecutor(setWarpCmd);
            getCommand("setwarp").setTabCompleter(setWarpCmd);
        }
        if (getCommand("delwarp") != null) {
            DelWarpCommand delWarpCmd = new DelWarpCommand();
            getCommand("delwarp").setExecutor(delWarpCmd);
            getCommand("delwarp").setTabCompleter(delWarpCmd);
        }

        // ==========================================
        // Home-Befehle
        // ==========================================
        if (getCommand("home") != null) {
            HomeCommand homeCmd = new HomeCommand();
            getCommand("home").setExecutor(homeCmd);
            getCommand("home").setTabCompleter(homeCmd);
        }
        if (getCommand("homes") != null) {
            HomeCommand homesCmd = new HomeCommand();
            getCommand("homes").setExecutor(homesCmd);
            getCommand("homes").setTabCompleter(homesCmd);
        }
        if (getCommand("sethome") != null) {
            SetHomeCommand setHomeCmd = new SetHomeCommand();
            getCommand("sethome").setExecutor(setHomeCmd);
            getCommand("sethome").setTabCompleter(setHomeCmd);
        }
        if (getCommand("delhome") != null) {
            DelHomeCommand delHomeCmd = new DelHomeCommand();
            getCommand("delhome").setExecutor(delHomeCmd);
            getCommand("delhome").setTabCompleter(delHomeCmd);
        }

        // ==========================================
        // Spawn-Befehle
        // ==========================================
        if (getCommand("spawn") != null) {
            SpawnCommand spawnCmd = new SpawnCommand();
            getCommand("spawn").setExecutor(spawnCmd);
            getCommand("spawn").setTabCompleter(spawnCmd);
        }
        if (getCommand("setworldspawn") != null) {
            SetWorldSpawnCommand setWorldSpawnCmd = new SetWorldSpawnCommand();
            getCommand("setworldspawn").setExecutor(setWorldSpawnCmd);
            getCommand("setworldspawn").setTabCompleter(setWorldSpawnCmd);
        }
        if (getCommand("back") != null) {
            de.basti.backbone.commands.teleport.BackCommand backCmd = new de.basti.backbone.commands.teleport.BackCommand();
            getCommand("back").setExecutor(backCmd);
            getCommand("back").setTabCompleter(backCmd);
        }

        // ==========================================
        // Economy-Befehle (Geld)
        // ==========================================
        if (getCommand("money") != null) {
            MoneyCommand moneyCmd = new MoneyCommand();
            getCommand("money").setExecutor(moneyCmd);
            getCommand("money").setTabCompleter(moneyCmd);
        }
        if (getCommand("pay") != null) {
            PayCommand payCmd = new PayCommand();
            getCommand("pay").setExecutor(payCmd);
            getCommand("pay").setTabCompleter(payCmd);
        }
        if (getCommand("paytoggle") != null) {
            de.basti.backbone.commands.economy.PaytoggleCommand paytoggleCmd = new de.basti.backbone.commands.economy.PaytoggleCommand();
            getCommand("paytoggle").setExecutor(paytoggleCmd);
            getCommand("paytoggle").setTabCompleter(paytoggleCmd);
        }
        if (getCommand("baltop") != null) {
            de.basti.backbone.commands.economy.BaltopCommand baltopCmd = new de.basti.backbone.commands.economy.BaltopCommand();
            getCommand("baltop").setExecutor(baltopCmd);
            getCommand("baltop").setTabCompleter(baltopCmd);
        }

        // ==========================================
        // Scoreboard-Befehle
        // ==========================================
        if (getCommand("scoreboard") != null) {
            ScoreboardCommand scoreboardCmd = new ScoreboardCommand();
            getCommand("scoreboard").setExecutor(scoreboardCmd);
            getCommand("scoreboard").setTabCompleter(scoreboardCmd);
        }

        // ==========================================
        // Private Nachrichten (/msg & /reply)
        // ==========================================
        if (getCommand("msg") != null) {
            de.basti.backbone.commands.message.MsgCommand msgCmd = new de.basti.backbone.commands.message.MsgCommand();
            getCommand("msg").setExecutor(msgCmd);
            getCommand("msg").setTabCompleter(msgCmd);
        }
        if (getCommand("reply") != null) {
            de.basti.backbone.commands.message.ReplyCommand replyCmd = new de.basti.backbone.commands.message.ReplyCommand();
            getCommand("reply").setExecutor(replyCmd);
            getCommand("reply").setTabCompleter(replyCmd);
        }

        // ==========================================
        // Chat-Befehle (Slowchat, Staffchat, Chatclear)
        // ==========================================
        if (getCommand("slowchat") != null) {
            de.basti.backbone.commands.chat.SlowchatCommand slowchatCmd = new de.basti.backbone.commands.chat.SlowchatCommand();
            getCommand("slowchat").setExecutor(slowchatCmd);
            getCommand("slowchat").setTabCompleter(slowchatCmd);
        }
        if (getCommand("staffchat") != null) {
            de.basti.backbone.commands.chat.StaffchatCommand staffchatCmd = new de.basti.backbone.commands.chat.StaffchatCommand();
            getCommand("staffchat").setExecutor(staffchatCmd);
            getCommand("staffchat").setTabCompleter(staffchatCmd);
        }
        if (getCommand("chatclear") != null) {
            de.basti.backbone.commands.chat.ChatclearCommand chatclearCmd = new de.basti.backbone.commands.chat.ChatclearCommand();
            getCommand("chatclear").setExecutor(chatclearCmd);
            getCommand("chatclear").setTabCompleter(chatclearCmd);
        }
        if (getCommand("actionbar") != null) {
            de.basti.backbone.commands.message.ActionbarCommand actionbarCmd = new de.basti.backbone.commands.message.ActionbarCommand();
            getCommand("actionbar").setExecutor(actionbarCmd);
            getCommand("actionbar").setTabCompleter(actionbarCmd);
        }
    }

    private void registerListeners() {
        infoMsg("Registering event listeners...");
        PluginManager pm = getServer().getPluginManager();

        // Chat-Events
        pm.registerEvents(new ChatEvent(), this);

        // Spieler-Events (jeweils 1 Klasse pro Event)
        pm.registerEvents(new PreLoginEvent(), this);
        pm.registerEvents(new LoginEvent(), this);
        pm.registerEvents(new JoinEvent(), this);
        pm.registerEvents(new LobbyJoinEvent(), this);
        pm.registerEvents(new QuitEvent(), this);
        pm.registerEvents(new KickEvent(), this);
        pm.registerEvents(new RespawnEvent(), this);
        pm.registerEvents(new de.basti.backbone.events.player.DeathEvent(), this);
        pm.registerEvents(new WorldChangeEvent(), this);
        pm.registerEvents(new WorldSpawnJoinEvent(), this);

        // Inventar-Events
        pm.registerEvents(new InventoryClickEvent(), this);
        pm.registerEvents(new InventoryDragEvent(), this);
        pm.registerEvents(new InventoryCloseEvent(), this);

        // Server-Events (MOTD, ServerListPing)
        pm.registerEvents(new ServerListPingEvent(), this);

        // Scoreboard-Events
        pm.registerEvents(new ScoreboardJoinEvent(), this);
        pm.registerEvents(new ScoreboardQuitEvent(), this);
        pm.registerEvents(new ScoreboardWorldChangeEvent(), this);

        // Command-Synchronisation (Deaktivierte Befehle rot markieren) & Hide-Listener
        pm.registerEvents(new CommandRegistryManager(), this);
        pm.registerEvents(new HideManager(), this);
    }

    public static void warnMsg(String msg) {
        if (logger != null) {
            logger.warning(msg);
        }
    }

    public static void infoMsg(String msg) {
        if (logger != null) {
            logger.info(msg);
        }
    }

    private void bStats() {
        int pluginId = 34927;
        Metrics metrics = new Metrics(this, pluginId);
    }

}
