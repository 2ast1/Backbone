package de.basti.backbone.util;

import de.basti.backbone.util.scoreboard.ScoreboardParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SetupAndPerformanceTest {

    @Test
    void testResolveChapterSteps() {
        assertEquals(SetupWizardManager.Step.CH1_1_LANGUAGE, SetupWizardManager.resolveChapterStep("Sprache"));
        assertEquals(SetupWizardManager.Step.CH1_1_LANGUAGE, SetupWizardManager.resolveChapterStep("language"));
        assertEquals(SetupWizardManager.Step.CH2_1_SCOREBOARD, SetupWizardManager.resolveChapterStep("Scoreboard"));
        assertEquals(SetupWizardManager.Step.CH3_1_RANKS, SetupWizardManager.resolveChapterStep("Ränge"));
        assertEquals(SetupWizardManager.Step.CH3_1_RANKS, SetupWizardManager.resolveChapterStep("Ranks"));
        assertEquals(SetupWizardManager.Step.CH4_1_AUTOMOD, SetupWizardManager.resolveChapterStep("Automod"));
        assertEquals(SetupWizardManager.Step.CH5_1_ESSENTIALS, SetupWizardManager.resolveChapterStep("Befehle"));
        assertEquals(SetupWizardManager.Step.CH5_1_ESSENTIALS, SetupWizardManager.resolveChapterStep("Essentials"));
        assertEquals(SetupWizardManager.Step.CH6_1_ECONOMY, SetupWizardManager.resolveChapterStep("Economy"));
        assertEquals(SetupWizardManager.Step.CH7_1_WARP, SetupWizardManager.resolveChapterStep("Teleportation"));
        assertEquals(SetupWizardManager.Step.CH8_MOTD, SetupWizardManager.resolveChapterStep("MotD"));
        assertEquals(SetupWizardManager.Step.CH9_1_JOIN_MSG, SetupWizardManager.resolveChapterStep("Nachrichten"));
        assertEquals(SetupWizardManager.Step.CH10_RAM_COMPACTING, SetupWizardManager.resolveChapterStep("Compating"));
        assertEquals(SetupWizardManager.Step.CH10_RAM_COMPACTING, SetupWizardManager.resolveChapterStep("Compacting"));
        assertNull(SetupWizardManager.resolveChapterStep("nonexistent"));
    }

    @Test
    void testRamModesAndCompaction() {
        assertEquals(RamManager.RamMode.FAST, RamManager.RamMode.fromString("FAST"));
        assertEquals(RamManager.RamMode.FAST, RamManager.RamMode.fromString("schneller"));
        assertEquals(RamManager.RamMode.BALANCED, RamManager.RamMode.fromString("BALANCED"));
        assertEquals(RamManager.RamMode.BALANCED, RamManager.RamMode.fromString("ausgewogen"));
        assertEquals(RamManager.RamMode.LIGHT, RamManager.RamMode.fromString("LIGHT"));
        assertEquals(RamManager.RamMode.LIGHT, RamManager.RamMode.fromString("langsamer"));

        RamManager.CompactionResult result = RamManager.executeCompact(true);
        assertNotNull(result);
        assertTrue(result.beforeMb() >= 0);
        assertTrue(result.afterMb() >= 0);
        assertTrue(result.savedMb() >= 0);
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void testFormatJoinLeaveMessage() {
        Lang.loadBundledLanguageForTest("englisch.lang");
        String formatted = ScoreboardParser.formatJoinLeaveMessage("{prefix} &e{playername} &7joined the server!", null);
        assertTrue(formatted.contains("§9[Backbone]"));
        assertTrue(formatted.contains("joined the server!"));

        java.util.UUID testUuid = java.util.UUID.randomUUID();
        org.bukkit.entity.Player mockPlayer = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "Versteuert";
                    case "getUniqueId" -> testUuid;
                    case "getPing", "getFoodLevel", "getLevel" -> 0;
                    case "getHealth" -> 20.0;
                    case "isFlying" -> false;
                    default -> null;
                }
        );

        String withPlayer = ScoreboardParser.formatJoinLeaveMessage("{prefix} &e{playername} &7joined the server!", mockPlayer);
        assertTrue(withPlayer.contains("§9[Backbone]"));
        assertTrue(withPlayer.contains("§eVersteuert §7joined the server!"));

        // Test {pluginPrefix} in Join/Leave, Scoreboard, and MOTD
        String withPluginPrefix = ScoreboardParser.formatJoinLeaveMessage("{pluginPrefix} &e{playername} &7joined!", mockPlayer);
        assertEquals("§9[Backbone] §eVersteuert §7joined!", withPluginPrefix);

        String sbWithPluginPrefix = ScoreboardParser.parse("{pluginPrefix} &aOnline", mockPlayer);
        assertEquals("§9[Backbone] §aOnline", sbWithPluginPrefix);

        String motdWithPluginPrefix = MotdFormatter.format("{pluginPrefix} &7Welcome!");
        assertEquals("§9[Backbone] §7Welcome!", motdWithPluginPrefix);
    }

    @Test
    void testWordFilterNoCrossWordFalsePositivesAndCatchesObfuscation() {
        String[] messagesClean = {
                "Viel Spaß trotzdem",
                "Viel Spas trotzdem",
                "was passt dir heute",
                "achso sechs wachsen"
        };

        for (String msg : messagesClean) {
            String normalizedBase = de.basti.backbone.util.automod.chat.filter.ChatNormalizer.stripAccents(msg.toLowerCase(java.util.Locale.ROOT));
            String deLeeted = de.basti.backbone.util.automod.chat.filter.ChatNormalizer.convertLeetspeak(normalizedBase);
            String collapsed = de.basti.backbone.util.automod.chat.filter.ChatNormalizer.collapseRepeatedChars(deLeeted);
            String strippedAll = de.basti.backbone.util.automod.chat.filter.ChatNormalizer.stripNonLetters(collapsed);
            String[] tokens = de.basti.backbone.util.automod.chat.filter.ChatNormalizer.extractTokens(collapsed);

            assertFalse(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedWord(collapsed, "spast"),
                    "Should not false-positive on: " + msg);
            assertFalse(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedShortWord(deLeeted, "hs"),
                    "Should not false-positive on short word in: " + msg);
            assertNotNull(strippedAll);
            assertNotNull(tokens);
        }

        assertTrue(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedWord("du s p a s t", "spast"));
        assertTrue(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedWord("f u c k you", "fuck"));
        assertTrue(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedShortWord("du h.s", "hs"));
        assertTrue(de.basti.backbone.util.automod.chat.filter.ChatNormalizer.containsSpacedShortWord("k y s", "kys"));
    }

    @Test
    void testDurationParsingRejectsReasonWordsAndPlayerResolverRejectsColorCodes() {
        assertEquals(0L, de.basti.backbone.util.automod.punish.BanUtil.parseDuration("3d-Model"));
        assertEquals(0L, de.basti.backbone.util.automod.punish.BanUtil.parseDuration("R2D2"));
        assertEquals(0L, de.basti.backbone.util.automod.punish.MuteUtil.parseDuration("100m-Sprint"));
        assertEquals(15 * 60 * 1000L, de.basti.backbone.util.automod.punish.BanUtil.parseDuration("15m"));
        assertEquals((24 + 12) * 3600 * 1000L, de.basti.backbone.util.automod.punish.MuteUtil.parseDuration("1d12h"));

        assertNull(PlayerResolver.resolveUuid("&6H"));
        assertNull(PlayerResolver.resolveUuid("&#FF5555Test"));
        assertFalse(PlayerResolver.isValidUsername("&6H"));
        assertTrue(PlayerResolver.isValidUsername("Versteuert"));
    }

    @Test
    void testPluginYmlPermissionsDefaultToOp() throws Exception {
        java.io.File file = new java.io.File("src/main/resources/plugin.yml");
        assertTrue(file.exists(), "plugin.yml must exist");

        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            org.bukkit.plugin.PluginDescriptionFile pdf = new org.bukkit.plugin.PluginDescriptionFile(in);
            java.util.List<org.bukkit.permissions.Permission> perms = pdf.getPermissions();
            assertFalse(perms.isEmpty(), "Permissions list must not be empty");
            assertTrue(perms.size() >= 76, "All permissions must be defined in plugin.yml");

            for (org.bukkit.permissions.Permission perm : perms) {
                assertEquals(org.bukkit.permissions.PermissionDefault.OP, perm.getDefault(),
                        "Permission " + perm.getName() + " must default to OP");
            }
        }
    }
}
