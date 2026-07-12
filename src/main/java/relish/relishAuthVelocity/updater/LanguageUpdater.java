package relish.relishAuthVelocity.updater;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

public class LanguageUpdater {
    private final Logger logger;
    private final Path langDirectory;

    public LanguageUpdater(Logger logger, Path langDirectory) {
        this.logger = logger;
        this.langDirectory = langDirectory;
    }

    public boolean updateLanguageFiles() {
        boolean updated = false;
        String[] languages = new String[]{"en", "ar"};
        String[] fileTypes = new String[]{"plugin.yml", "discord.yml"};
        for (String lang : languages) {
            for (String fileType : fileTypes) {
                try {
                    if (!this.updateLanguageFile(lang, fileType)) continue;
                    updated = true;
                }
                catch (Exception e) {
                    this.logger.error("Failed to update {}/{}: {}", lang, fileType, e.getMessage());
                }
            }
        }
        return updated;
    }

    private boolean updateLanguageFile(String lang, String fileName) throws IOException {
        LinkedHashMap<String, Object> userLang;
        Map defaultLang;
        InputStream defaultStream;
        Path langFolder = this.langDirectory.resolve(lang);
        Path langFile = langFolder.resolve(fileName);
        if (!Files.exists(langFolder, new LinkOption[0])) {
            Files.createDirectories(langFolder, new FileAttribute[0]);
        }
        if ((defaultStream = this.getClass().getResourceAsStream("/lang/" + lang + "/" + fileName)) == null) {
            this.logger.debug("No default language file found: /lang/{}/{}", (Object)lang, (Object)fileName);
            return false;
        }
        Yaml yaml = new Yaml();
        try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8);){
            defaultLang = (Map)yaml.load(reader);
        }
        if (defaultLang == null) {
            return false;
        }
        if (!Files.exists(langFile, new LinkOption[0])) {
            try (InputStream copyStream = this.getClass().getResourceAsStream("/lang/" + lang + "/" + fileName);){
                if (copyStream != null) {
                    Files.copy(copyStream, langFile, new CopyOption[0]);
                    this.logger.debug("Created language file: {}/{}", (Object)lang, (Object)fileName);
                    boolean bl = true;
                    return bl;
                }
            }
            return false;
        }
        try (FileInputStream fis = new FileInputStream(langFile.toFile());){
            userLang = (LinkedHashMap<String, Object>)yaml.load(fis);
        }
        if (userLang == null) {
            userLang = new LinkedHashMap<String, Object>();
        }
        MergeResult result = new MergeResult();
        this.mergeLanguageData(userLang, defaultLang, "", result);
        if (result.changed) {
            List<String> originalLines = Files.readAllLines(langFile, StandardCharsets.UTF_8);
            List<String> headerComments = this.extractHeader(originalLines);
            Map<String, String> keyComments = this.extractKeyComments(originalLines);
            this.saveLanguageFileWithFormat(langFile, userLang, headerComments, keyComments, defaultLang);
            this.logger.info("Added {} missing key(s) to {}/{}", result.addedCount, lang, fileName);
            return true;
        }
        return false;
    }

    private void mergeLanguageData(Map<String, Object> target, Map<String, Object> source, String path, MergeResult result) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String currentPath;
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            String string = currentPath = path.isEmpty() ? key : path + "." + key;
            if (!target.containsKey(key)) {
                target.put(key, this.deepCopy(sourceValue));
                result.changed = true;
                ++result.addedCount;
                this.logger.debug("Added: {}", (Object)currentPath);
                continue;
            }
            Object targetValue = target.get(key);
            if (sourceValue instanceof Map && targetValue instanceof Map) {
                Map sourceMap = (Map)sourceValue;
                Map targetMap = (Map)targetValue;
                this.mergeLanguageData(targetMap, sourceMap, currentPath, result);
                continue;
            }
            if (!(sourceValue instanceof List) || !(targetValue instanceof List)) continue;
            List sourceList = (List)sourceValue;
            List targetList = (List)targetValue;
            if (!targetList.isEmpty() || sourceList.isEmpty()) continue;
            targetList.addAll(sourceList);
            result.changed = true;
            this.logger.debug("Populated empty list: {}", (Object)currentPath);
        }
    }

    private Object deepCopy(Object value) {
        if (value instanceof Map) {
            LinkedHashMap copy = new LinkedHashMap();
            ((Map)value).forEach((k, v) -> copy.put(k, this.deepCopy(v)));
            return copy;
        }
        if (value instanceof List) {
            ArrayList copy = new ArrayList();
            ((List)value).forEach(item -> copy.add(this.deepCopy(item)));
            return copy;
        }
        return value;
    }

    private List<String> extractHeader(List<String> lines) {
        ArrayList<String> header = new ArrayList<String>();
        for (String line : lines) {
            if (!line.trim().startsWith("#") && !line.trim().isEmpty()) break;
            header.add(line);
        }
        return header;
    }

    private Map<String, String> extractKeyComments(List<String> lines) {
        LinkedHashMap<String, String> comments = new LinkedHashMap<String, String>();
        StringBuilder currentComment = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if (currentComment.length() > 0) {
                    currentComment.append("\n");
                }
                currentComment.append(line);
                continue;
            }
            if (!trimmed.contains(":") || trimmed.startsWith("-") || currentComment.length() <= 0) continue;
            String key = trimmed.split(":", 2)[0].trim();
            comments.put(key, currentComment.toString());
            currentComment = new StringBuilder();
        }
        return comments;
    }

    private void saveLanguageFileWithFormat(Path langFile, Map<String, Object> data, List<String> header, Map<String, String> keyComments, Map<String, Object> defaultData) throws IOException {
        ArrayList<String> result = new ArrayList<String>();
        if (!header.isEmpty()) {
            result.addAll(header);
            if (!header.get(header.size() - 1).isEmpty()) {
                result.add("");
            }
        }
        this.writeYamlWithComments(result, data, keyComments, defaultData, 0);
        Files.write(langFile, result, StandardCharsets.UTF_8, new OpenOption[0]);
    }

    private void writeYamlWithComments(List<String> output, Map<String, Object> data, Map<String, String> comments, Map<String, Object> defaultData, int indent) {
        String indentStr = "  ".repeat(indent);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (comments.containsKey(key)) {
                String comment = comments.get(key);
                for (String line : comment.split("\n")) {
                    output.add(line);
                }
            }
            if (value instanceof Map) {
                output.add(indentStr + key + ":");
                Object defaultSubData = defaultData != null ? defaultData.get(key) : null;
                Map subDefault = defaultSubData instanceof Map ? (Map)defaultSubData : null;
                this.writeYamlWithComments(output, (Map)value, comments, subDefault, indent + 1);
                continue;
            }
            if (value instanceof List) {
                List list = (List)value;
                if (list.isEmpty()) {
                    output.add(indentStr + key + ": []");
                    continue;
                }
                output.add(indentStr + key + ":");
                for (Object item : list) {
                    if (item instanceof Map) {
                        Map<?, ?> mapItem = (Map<?, ?>)item;
                        output.add(indentStr + "  -");
                        for (Map.Entry<?, ?> subEntry : mapItem.entrySet()) {
                            output.add(indentStr + "    " + subEntry.getKey() + ": " + this.formatValue(subEntry.getValue()));
                        }
                        continue;
                    }
                    output.add(indentStr + "  - " + this.formatValue(item));
                }
                continue;
            }
            output.add(indentStr + key + ": " + this.formatValue(value));
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            String str = (String)value;
            if (str.contains("\n") || str.contains("\"") || str.contains(":")) {
                return "\"" + str.replace("\"", "\\\"") + "\"";
            }
            return str;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "\"" + value.toString() + "\"";
    }

    private static class MergeResult {
        boolean changed = false;
        int addedCount = 0;

        private MergeResult() {
        }
    }
}
