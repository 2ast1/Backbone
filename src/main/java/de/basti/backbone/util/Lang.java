package de.basti.backbone.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Central language manager for Backbone.
 * Manages the /languages/ folder (example.lang, englisch.lang, deutsch.lang)
 * and loads the active language configured under "server.backbone.language" in config.yml.
 */
public final class Lang {

    public static final String DEFAULT_LANGUAGE = "englisch.lang";
    private static final String[] BUNDLED_LANGUAGES = {
            "example.lang",
            "englisch.lang",
            "deutsch.lang"
    };

    private static JavaPlugin plugin;
    private static File languagesFolder;
    private static FileConfiguration activeConfig;
    private static FileConfiguration fallbackConfig;
    private static String activeLanguageName = DEFAULT_LANGUAGE;

    private Lang() {
    }

    /**
     * Initializes the language system, extracts default language files, and loads the configured language.
     */
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        languagesFolder = new File(plugin.getDataFolder(), "languages");
        if (!languagesFolder.exists()) {
            languagesFolder.mkdirs();
        }

        ensureFallbackLoaded();
        extractAndSyncBundledLanguages();
        reload();
    }

    /**
     * Reloads the language files from disk and re-reads "server.backbone.language" from config.yml.
     */
    public static void reload() {
        ensureFallbackLoaded();

        if (plugin == null) {
            activeConfig = fallbackConfig;
            activeLanguageName = DEFAULT_LANGUAGE;
            return;
        }

        if (languagesFolder == null) {
            languagesFolder = new File(plugin.getDataFolder(), "languages");
        }
        if (!languagesFolder.exists()) {
            languagesFolder.mkdirs();
        }

        extractAndSyncBundledLanguages();

        String configuredLang = DEFAULT_LANGUAGE;
        if (DataManager.getConfig() != null) {
            configuredLang = DataManager.getConfig().getString("server.backbone.language", DEFAULT_LANGUAGE);
        }

        String fileName = normalizeLangFileName(configuredLang);
        File langFile = resolveLanguageFile(fileName);

        if (langFile != null && langFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
                activeConfig = YamlConfiguration.loadConfiguration(reader);
                activeLanguageName = langFile.getName();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load language file: " + langFile.getName(), e);
                activeConfig = fallbackConfig;
                activeLanguageName = DEFAULT_LANGUAGE;
            }
        } else {
            plugin.getLogger().warning("Language file '" + fileName + "' was not found in /languages/! Falling back to " + DEFAULT_LANGUAGE + ".");
            activeConfig = fallbackConfig;
            activeLanguageName = DEFAULT_LANGUAGE;
        }
    }

    private static void ensureFallbackLoaded() {
        if (fallbackConfig != null) {
            return;
        }
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("languages/" + DEFAULT_LANGUAGE)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    fallbackConfig = YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                fallbackConfig = new YamlConfiguration();
            }
        } catch (IOException e) {
            fallbackConfig = new YamlConfiguration();
        }
    }

    private static void extractAndSyncBundledLanguages() {
        if (languagesFolder == null) {
            return;
        }

        for (String bundled : BUNDLED_LANGUAGES) {
            File targetFile = new File(languagesFolder, bundled);
            if (!targetFile.exists()) {
                try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("languages/" + bundled)) {
                    if (in != null) {
                        Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not create default language file: " + bundled, e);
                    }
                }
            } else if (!bundled.equalsIgnoreCase("example.lang")) {
                // Merge any newly added keys into existing englisch.lang / deutsch.lang without overwriting user values
                syncMissingKeys(bundled, targetFile);
            }
        }
    }

    private static void syncMissingKeys(String resourceName, File targetFile) {
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("languages/" + resourceName)) {
            if (in == null) {
                return;
            }
            FileConfiguration bundledCfg;
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                bundledCfg = YamlConfiguration.loadConfiguration(reader);
            }

            FileConfiguration diskCfg;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8)) {
                diskCfg = YamlConfiguration.loadConfiguration(reader);
            }

            boolean modified = false;
            for (String key : bundledCfg.getKeys(true)) {
                if (!bundledCfg.isConfigurationSection(key)) {
                    if (!diskCfg.contains(key)) {
                        diskCfg.set(key, bundledCfg.get(key));
                        modified = true;
                    } else if (key.equals("setup.ch9_1.lines") || key.equals("setup.ch9_2.lines")) {
                        List<String> existingLines = diskCfg.getStringList(key);
                        for (String l : existingLines) {
                            if (l.contains("(Plugin-Präfix)") || l.contains("(Plugin Prefix)")) {
                                diskCfg.set(key, bundledCfg.get(key));
                                modified = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (modified) {
                diskCfg.save(targetFile);
            }
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Could not synchronize language file: " + targetFile.getName(), e);
            }
        }
    }

    private static String normalizeLangFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        String clean = fileName.trim();
        if (!clean.contains(".")) {
            clean = clean + ".lang";
        }
        return clean;
    }

    private static File resolveLanguageFile(String fileName) {
        File direct = new File(languagesFolder, fileName);
        if (direct.exists()) {
            return direct;
        }

        // Case-insensitive search in languagesFolder
        File[] files = languagesFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().equalsIgnoreCase(fileName)) {
                    return f;
                }
            }
        }

        // Common alias fallbacks
        String lower = fileName.toLowerCase();
        if (lower.equals("english.lang") || lower.equals("en.lang") || lower.equals("en_us.lang")) {
            File fallback = new File(languagesFolder, "englisch.lang");
            if (fallback.exists()) return fallback;
        } else if (lower.equals("german.lang") || lower.equals("de.lang") || lower.equals("de_de.lang")) {
            File fallback = new File(languagesFolder, "deutsch.lang");
            if (fallback.exists()) return fallback;
        }

        return direct;
    }

    /**
     * Returns the formatted plugin prefix from the active language file.
     */
    public static String getPrefix() {
        return get("prefix");
    }

    /**
     * Retrieves a raw message string (with '&' color codes unformatted) and applies placeholder replacements.
     * Replacements are passed in key-value pairs, e.g. Lang.getRaw("key", "player", playerName, "amount", "10").
     */
    public static String getRaw(String key, String... replacements) {
        ensureFallbackLoaded();

        String value = null;
        if (activeConfig != null && activeConfig.contains(key)) {
            value = activeConfig.getString(key);
        }
        if (value == null && fallbackConfig != null && fallbackConfig.contains(key)) {
            value = fallbackConfig.getString(key);
        }
        if (value == null) {
            value = key;
        }

        value = value.replace("\\n", "\n");

        return applyReplacements(value, replacements);
    }

    /**
     * Retrieves a color-formatted message string (translating '&' and '&#RRGGBB' in the language template)
     * and then applies placeholder replacements so raw user inputs or color code strings in replacements
     * are not accidentally translated as color codes.
     * Replacements are passed in key-value pairs, e.g. Lang.get("key", "player", playerName).
     */
    public static String get(String key, String... replacements) {
        String formattedTemplate = PrefixManager.formatColors(getRaw(key));
        return applyReplacements(formattedTemplate, replacements);
    }

    private static String applyReplacements(String value, String... replacements) {
        if (value == null) {
            return "";
        }
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                String k = replacements[i];
                String v = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (k != null) {
                    String token = (k.startsWith("{") && k.endsWith("}")) ? k : "{" + k + "}";
                    value = value.replace(token, v);
                }
            }
        }
        return value;
    }

    /**
     * Retrieves a string list from the active language file (or falls back to the default English list).
     */
    public static List<String> getList(String key) {
        ensureFallbackLoaded();

        if (activeConfig != null && activeConfig.contains(key)) {
            List<String> list = activeConfig.getStringList(key);
            if (!list.isEmpty()) {
                return list;
            }
        }
        if (fallbackConfig != null && fallbackConfig.contains(key)) {
            return fallbackConfig.getStringList(key);
        }
        return Collections.emptyList();
    }

    /**
     * Returns the currently active language file name (e.g., "englisch.lang").
     */
    public static String getActiveLanguageName() {
        return activeLanguageName;
    }

    /**
     * Loads a specific bundled language directly from classpath (mainly for testing).
     */
    public static void loadBundledLanguageForTest(String bundledFileName) {
        ensureFallbackLoaded();
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("languages/" + bundledFileName)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    activeConfig = YamlConfiguration.loadConfiguration(reader);
                    activeLanguageName = bundledFileName;
                }
            }
        } catch (IOException ignored) {
        }
    }
}
