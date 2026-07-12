package relish.relishAuthVelocity.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import relish.relishAuthVelocity.config.Config;

public class MessageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageManager.class);
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final Map<String, Component> STATIC_PARSE_CACHE = new ConcurrentHashMap<String, Component>();
    private final Path dataDirectory;
    private final Config config;
    private Map<String, Object> langConfig;
    private String currentLang;
    private final Map<String, Component> messageCache;
    private final Set<String> missingKeys;

    public MessageManager(Path dataDirectory, Config config) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.messageCache = new ConcurrentHashMap<String, Component>();
        this.missingKeys = ConcurrentHashMap.newKeySet();
        String configLang = config.getString("language", "en");
        this.loadLanguage(configLang);
    }

    public void loadLanguage(String lang) {
        this.messageCache.clear();
        this.missingKeys.clear();
        File langFolder = this.dataDirectory.resolve("lang").resolve(lang).toFile();
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            LOGGER.error("Failed to create language folder: {}", (Object)langFolder.getPath());
        }
        File pluginLangFile = new File(langFolder, "plugin.yml");
        String finalLang = lang;
        if (!pluginLangFile.exists()) {
            try {
                InputStream defaultLang = this.getClass().getResourceAsStream("/lang/" + lang + "/plugin.yml");
                if (defaultLang != null) {
                    Files.copy(defaultLang, pluginLangFile.toPath(), new CopyOption[0]);
                } else {
                    LOGGER.warn("Language '{}' not found, using English", (Object)lang);
                    finalLang = "en";
                    langFolder = this.dataDirectory.resolve("lang").resolve(finalLang).toFile();
                    if (!langFolder.exists() && !langFolder.mkdirs()) {
                        LOGGER.error("Failed to create language folder: {}", (Object)langFolder.getPath());
                    }
                    if (!(pluginLangFile = new File(langFolder, "plugin.yml")).exists() && (defaultLang = this.getClass().getResourceAsStream("/lang/en/plugin.yml")) != null) {
                        Files.copy(defaultLang, pluginLangFile.toPath(), new CopyOption[0]);
                    }
                }
            }
            catch (Exception e) {
                LOGGER.error("Failed to create language file: {}", (Object)e.getMessage());
            }
        }
        try {
            Yaml yaml = new Yaml();
            try (FileInputStream fis = new FileInputStream(pluginLangFile);){
                this.langConfig = (Map)yaml.load(fis);
            }
            this.currentLang = finalLang;
        }
        catch (Exception e) {
            LOGGER.error("Failed to load language file: {}", (Object)e.getMessage());
            this.langConfig = new HashMap<String, Object>();
        }
    }

    public Component getMessage(String path) {
        if (path == null || path.trim().isEmpty()) {
            this.logMissingKey("<empty>");
            return Component.text((String)"\u00a7cEmpty message key");
        }
        if (this.messageCache.containsKey(path)) {
            return this.messageCache.get(path);
        }
        Component message = this.buildMessage(path, new String[0]);
        return message;
    }

    public Component getMessage(String path, String ... replacements) {
        if (path == null || path.trim().isEmpty()) {
            this.logMissingKey("<empty>");
            return Component.text((String)"\u00a7cEmpty message key");
        }
        String cacheKey = path + Arrays.toString(replacements);
        if (this.messageCache.containsKey(cacheKey)) {
            return this.messageCache.get(cacheKey);
        }
        Component message = this.buildMessage(path, replacements);
        if (replacements.length == 0) {
            this.messageCache.put(path, message);
        }
        return message;
    }

    public List<Component> getMessageList(String path, String ... replacements) {
        ArrayList<Component> result = new ArrayList<Component>();
        Object value = this.getNestedValue(path);
        if (value instanceof List) {
            List lines = (List)value;
            String serverName = this.getConfigValue("server-name", "My Server");
            Iterator iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line;
                String processed = line = (String)iterator.next();
                processed = processed.replace("{server}", serverName);
                for (int i = 0; i < replacements.length; i += 2) {
                    if (i + 1 >= replacements.length) continue;
                    processed = processed.replace(replacements[i], replacements[i + 1]);
                }
                result.add(MessageManager.parseColors(processed));
            }
        }
        return result;
    }

    private Component buildMessage(String path, String ... replacements) {
        if (this.langConfig == null) {
            this.logMissingKey(path);
            return Component.text((String)"\u00a7cLanguage not loaded");
        }
        String serverName = this.getConfigValue("server-name", "My Server");
        Object value = this.getNestedValue(path);
        if (value instanceof List) {
            List lines = (List)value;
            if (lines.isEmpty()) {
                this.logMissingKey(path);
                return Component.text((String)("\u00a7cMessage not found: " + path));
            }
            ArrayList<String> processedLines = new ArrayList<String>();
            Iterator iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line;
                String processed = line = (String)iterator.next();
                processed = processed.replace("{server}", serverName);
                for (int i = 0; i < replacements.length; i += 2) {
                    if (i + 1 >= replacements.length) continue;
                    processed = processed.replace(replacements[i], replacements[i + 1]);
                }
                processedLines.add(processed);
            }
            Component result = Component.empty();
            for (int i = 0; i < processedLines.size(); ++i) {
                result = result.append(MessageManager.parseColors((String)processedLines.get(i)));
                if (i >= processedLines.size() - 1) continue;
                result = result.append((Component)Component.newline());
            }
            return result;
        }
        if (value instanceof String) {
            String text = (String)value;
            text = text.replace("{server}", serverName);
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 >= replacements.length) continue;
                text = text.replace(replacements[i], replacements[i + 1]);
            }
            return MessageManager.parseColors(text);
        }
        this.logMissingKey(path);
        return Component.text((String)("\u00a7cMessage not found: " + path));
    }

    private Object getNestedValue(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Map current = this.langConfig;
        for (int i = 0; i < parts.length - 1; ++i) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return null;
            }
            current = (Map)next;
        }
        return current.get(parts[parts.length - 1]);
    }

    private String getConfigValue(String path, String defaultValue) {
        String[] parts = path.split("\\.");
        Map current = this.langConfig;
        for (int i = 0; i < parts.length - 1; ++i) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return defaultValue;
            }
            current = (Map)next;
        }
        Object value = current.get(parts[parts.length - 1]);
        return value != null ? value.toString() : defaultValue;
    }

    public static Component parseColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component cached = STATIC_PARSE_CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        text = text.replace("&", "\u00a7");
        text = text.replace("\u00a70", "<black>").replace("\u00a71", "<dark_blue>").replace("\u00a72", "<dark_green>").replace("\u00a73", "<dark_aqua>").replace("\u00a74", "<dark_red>").replace("\u00a75", "<dark_purple>").replace("\u00a76", "<gold>").replace("\u00a77", "<gray>").replace("\u00a78", "<dark_gray>").replace("\u00a79", "<blue>").replace("\u00a7a", "<green>").replace("\u00a7b", "<aqua>").replace("\u00a7c", "<red>").replace("\u00a7d", "<light_purple>").replace("\u00a7e", "<yellow>").replace("\u00a7f", "<white>").replace("\u00a7l", "<bold>").replace("\u00a7m", "<strikethrough>").replace("\u00a7n", "<underlined>").replace("\u00a7o", "<italic>").replace("\u00a7k", "<obfuscated>").replace("\u00a7r", "<reset>");
        try {
            Component result = miniMessage.deserialize(text).decoration(TextDecoration.ITALIC, false);
            STATIC_PARSE_CACHE.put(text, result);
            return result;
        }
        catch (Exception e) {
            Component fallback = Component.text((String)text.replaceAll("<[^>]+>", ""));
            STATIC_PARSE_CACHE.put(text, (Component)fallback);
            return fallback;
        }
    }

    public String getRawMessage(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        Object value = this.getNestedValue(path);
        return value != null ? value.toString() : "";
    }

    public List<String> getRawMessageList(String path) {
        Object value = this.getNestedValue(path);
        if (value instanceof List) {
            List list = (List)value;
            return list;
        }
        return new ArrayList<String>();
    }

    public List<String> getMessageStrings(String path) {
        return this.getRawMessageList(path);
    }

    public boolean hasMessage(String path) {
        return this.getNestedValue(path) != null;
    }

    public String getCurrentLanguage() {
        return this.currentLang;
    }

    public void clearCache() {
        this.messageCache.clear();
    }

    public int getCacheSize() {
        return this.messageCache.size();
    }

    public Set<String> getMissingKeys() {
        return new HashSet<String>(this.missingKeys);
    }

    private void logMissingKey(String key) {
        if (!this.missingKeys.contains(key)) {
            this.missingKeys.add(key);
            LOGGER.warn("Missing message key: {} (language: {})", (Object)key, (Object)this.currentLang);
        }
    }

    public void reload() {
        String lang = this.config.getString("language", "en");
        if (!lang.equals(this.currentLang)) {
            this.loadLanguage(lang);
        } else {
            this.clearCache();
            this.loadLanguage(lang);
        }
    }
}
