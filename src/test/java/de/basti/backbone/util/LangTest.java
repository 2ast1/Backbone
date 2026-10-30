package de.basti.backbone.util;

import de.basti.backbone.util.automod.punish.BanUtil;
import de.basti.backbone.util.automod.punish.MuteUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LangTest {

    private FileConfiguration loadLangResource(String fileName) throws IOException {
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("languages/" + fileName)) {
            assertNotNull(in, "Resource languages/" + fileName + " must exist");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    private Set<String> getLeafKeys(FileConfiguration cfg) {
        Set<String> leaves = new HashSet<>();
        for (String key : cfg.getKeys(true)) {
            if (!cfg.isConfigurationSection(key)) {
                leaves.add(key);
            }
        }
        return leaves;
    }

    @Test
    void testAllBundledLanguageFilesHaveIdenticalKeys() throws IOException {
        FileConfiguration en = loadLangResource("englisch.lang");
        FileConfiguration de = loadLangResource("deutsch.lang");
        FileConfiguration ex = loadLangResource("example.lang");

        Set<String> enKeys = getLeafKeys(en);
        Set<String> deKeys = getLeafKeys(de);
        Set<String> exKeys = getLeafKeys(ex);

        assertFalse(enKeys.isEmpty(), "englisch.lang must not be empty");

        Set<String> missingInDe = new HashSet<>(enKeys);
        missingInDe.removeAll(deKeys);
        assertTrue(missingInDe.isEmpty(), "Keys missing in deutsch.lang: " + missingInDe);

        Set<String> extraInDe = new HashSet<>(deKeys);
        extraInDe.removeAll(enKeys);
        assertTrue(extraInDe.isEmpty(), "Extra keys in deutsch.lang not in englisch.lang: " + extraInDe);

        Set<String> missingInEx = new HashSet<>(enKeys);
        missingInEx.removeAll(exKeys);
        assertTrue(missingInEx.isEmpty(), "Keys missing in example.lang: " + missingInEx);

        Set<String> extraInEx = new HashSet<>(exKeys);
        extraInEx.removeAll(enKeys);
        assertTrue(extraInEx.isEmpty(), "Extra keys in example.lang not in englisch.lang: " + extraInEx);
    }

    @Test
    void testAllCodeLangKeysExistInLanguageFiles() throws IOException {
        FileConfiguration en = loadLangResource("englisch.lang");
        Set<String> enKeys = getLeafKeys(en);

        Pattern langCallPattern = Pattern.compile("Lang\\.(?:get|getRaw|getList)\\(\\s*\"([^\"]+)\"");
        Path srcMainJava = Paths.get("src/main/java");
        assertTrue(Files.exists(srcMainJava), "src/main/java should exist");

        Set<String> missingKeys = new HashSet<>();
        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            paths.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("Lang.java")).forEach(file -> {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher m = langCallPattern.matcher(content);
                    while (m.find()) {
                        String key = m.group(1);
                        if (!enKeys.contains(key)) {
                            missingKeys.add(key + " (in " + file.getFileName() + ")");
                        }
                    }
                } catch (IOException e) {
                    fail("Failed reading " + file + ": " + e.getMessage());
                }
            });
        }

        assertTrue(missingKeys.isEmpty(), "Missing translation keys referenced in Java code: " + missingKeys);
    }

    @Test
    void testLanguageSwitchingAndPlaceholders() {
        Lang.loadBundledLanguageForTest("englisch.lang");
        assertEquals("englisch.lang", Lang.getActiveLanguageName());
        assertTrue(Lang.get("common.no-permission").contains("permission"));
        assertEquals("1 day, 2 hours, 5 seconds", BanUtil.formatDuration((24 * 3600 + 2 * 3600 + 5) * 1000L));
        assertEquals("0 seconds", MuteUtil.formatDuration(0));

        String enTeleport = Lang.get("warp.teleported", "warp", "Spawn");
        assertTrue(enTeleport.contains("Spawn"));
        assertTrue(enTeleport.contains("teleported"));

        Lang.loadBundledLanguageForTest("deutsch.lang");
        assertEquals("deutsch.lang", Lang.getActiveLanguageName());
        assertTrue(Lang.get("common.no-permission").contains("Berechtigung"));
        assertEquals("1 Tag, 2 Stunden, 5 Sekunden", BanUtil.formatDuration((24 * 3600 + 2 * 3600 + 5) * 1000L));
        assertEquals("0 Sekunden", MuteUtil.formatDuration(0));

        String deTeleport = Lang.get("warp.teleported", "warp", "Spawn");
        assertTrue(deTeleport.contains("Spawn"));
        assertTrue(deTeleport.contains("teleportiert"));

        // Ensure color codes in replacement arguments (e.g. /prefix &6H) are NOT converted into colors
        String noneOther = Lang.get("prefix_cmd.none-other", "player", "&6H");
        assertTrue(noneOther.contains("&6H"), "Replacement argument &6H should remain literal and not be color-formatted");
        assertFalse(noneOther.contains("§6H"), "Replacement argument &6H must not be turned into §6H");

        // Reset back to default English
        Lang.loadBundledLanguageForTest("englisch.lang");
    }
}
