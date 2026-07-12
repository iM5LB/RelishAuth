package relish.relishAuthVelocity.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class ConfigUpdater {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int INDENT_SIZE = 2;
    private final Logger logger;
    private final Path configPath;

    public ConfigUpdater(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
    }

    public Path getConfigPath() {
        return this.configPath;
    }

    public boolean updateConfigWithDefaults(InputStream defaultConfigStream) {
        if (defaultConfigStream == null) {
            return false;
        }
        if (!Files.exists(this.configPath, new LinkOption[0])) {
            this.logger.warn("Config file not found at {}, skipping update", (Object)this.configPath);
            return false;
        }
        try {
            Map<String, Object> userConfig;
            byte[] defaultBytes = defaultConfigStream.readAllBytes();
            String defaultContent = ConfigUpdater.sanitizeYamlText(new String(defaultBytes, StandardCharsets.UTF_8));
            List<String> defaultLines = ConfigUpdater.splitToLines(defaultContent);
            Yaml yaml = this.createSafeYaml();
            Map<String, Object> defaultConfig = ConfigUpdater.loadYamlMap(yaml, defaultContent);
            if (defaultConfig.isEmpty()) {
                return false;
            }
            List<String> userLines = Files.readAllLines(this.configPath, StandardCharsets.UTF_8);
            String userContent = ConfigUpdater.sanitizeYamlText(String.join((CharSequence)"\n", userLines));
            try {
                userConfig = ConfigUpdater.loadYamlMap(yaml, userContent);
            }
            catch (Exception badYaml) {
                ConfigUpdater.backupFile(this.configPath);
                Files.writeString(this.configPath, (CharSequence)new String(defaultBytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8, new OpenOption[0]);
                this.logger.warn("Config updater: detected invalid config.yml, restored defaults (old file backed up)");
                return true;
            }
            int missingLeafCount = ConfigUpdater.countMissingLeafKeys(userConfig, defaultConfig);
            if (missingLeafCount <= 0) {
                return false;
            }
            ArrayList<String> missingInsertionPaths = new ArrayList<String>();
            ConfigUpdater.collectMissingInsertionPaths(userConfig, defaultConfig, "", missingInsertionPaths);
            if (missingInsertionPaths.isEmpty()) {
                return false;
            }
            TemplateIndex templateIndex = TemplateIndex.fromLines(defaultLines);
            UserIndex userIndex = UserIndex.fromLines(userLines);
            ArrayList<Insertion> insertions = new ArrayList<Insertion>();
            for (String missingPath : missingInsertionPaths) {
                TemplateKey templateKey = templateIndex.keysByPath.get(missingPath);
                if (templateKey == null) {
                    this.logger.debug("Config updater: missing template snippet for key {}", (Object)missingPath);
                    continue;
                }
                ParentAnchor anchor = userIndex.findParentAnchor(missingPath);
                if (!anchor.valid) {
                    this.logger.debug("Config updater: could not find parent section for missing key {}", (Object)missingPath);
                    continue;
                }
                List<String> snippet = templateIndex.extractSnippet(missingPath);
                if (snippet.isEmpty()) continue;
                List<String> adjustedSnippet = ConfigUpdater.adjustIndent(snippet, templateKey.indent, anchor.targetIndent);
                insertions.add(new Insertion(anchor.insertAtLine, templateKey.lineIndex, adjustedSnippet));
            }
            if (insertions.isEmpty()) {
                return false;
            }
            insertions.sort(Comparator.comparingInt((Insertion i) -> i.insertAtLine).reversed().thenComparingInt((Insertion i) -> i.templateLineIndex).reversed());
            for (Insertion insertion : insertions) {
                userLines.addAll(insertion.insertAtLine, insertion.lines);
            }
            ConfigUpdater.backupFile(this.configPath);
            Files.write(this.configPath, userLines, StandardCharsets.UTF_8, new OpenOption[0]);
            this.logger.info("Config updater: merged {} missing config key(s) into config.yml (old file backed up)", (Object)missingLeafCount);
            return true;
        }
        catch (Exception e) {
            this.logger.error("Failed to update config: {}", (Object)e.getMessage(), (Object)e);
            return false;
        }
    }

    public boolean setRootScalar(String key, String value) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (value == null) {
            return false;
        }
        if (!Files.exists(this.configPath, new LinkOption[0])) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(this.configPath, StandardCharsets.UTF_8);
            Pattern keyPattern = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*:\\s*.*$");
            int keyLineIndex = -1;
            for (int i = 0; i < lines.size(); ++i) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !keyPattern.matcher(line).matches()) continue;
                keyLineIndex = i;
                break;
            }
            String newLine = key + ": " + value;
            boolean changed = false;
            if (keyLineIndex >= 0) {
                if (!lines.get(keyLineIndex).trim().equals(newLine)) {
                    lines.set(keyLineIndex, newLine);
                    changed = true;
                }
            } else {
                String trimmed;
                int insertAt;
                for (insertAt = 0; insertAt < lines.size() && ((trimmed = lines.get(insertAt).trim()).isEmpty() || trimmed.startsWith("#")); ++insertAt) {
                }
                lines.add(insertAt, newLine);
                changed = true;
            }
            if (!changed) {
                return false;
            }
            ConfigUpdater.backupFile(this.configPath);
            Files.write(this.configPath, lines, StandardCharsets.UTF_8, new OpenOption[0]);
            return true;
        }
        catch (Exception e) {
            this.logger.debug("Config updater: failed setting root key {}: {}", (Object)key, (Object)e.getMessage());
            return false;
        }
    }

    private Yaml createSafeYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setProcessComments(false);
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    private static List<String> splitToLines(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<String>();
        }
        return Arrays.asList(content.split("\\r?\\n", -1));
    }

    private static Map<String, Object> loadYamlMap(Yaml yaml, String content) {
        Object loaded = yaml.load(content);
        if (loaded == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (!(loaded instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        return (Map)ConfigUpdater.normalizeYamlValue(loaded);
    }

    private static Object normalizeYamlValue(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>)value;
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) continue;
                normalized.put(entry.getKey().toString(), ConfigUpdater.normalizeYamlValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List) {
            List list = (List)value;
            ArrayList<Object> normalized = new ArrayList<Object>(list.size());
            for (Object item : list) {
                normalized.add(ConfigUpdater.normalizeYamlValue(item));
            }
            return normalized;
        }
        return value;
    }

    private static int countMissingLeafKeys(Map<String, Object> user, Map<String, Object> defaults) {
        int missing = 0;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();
            if (!user.containsKey(key)) {
                missing += ConfigUpdater.countLeafKeys(defaultValue);
                continue;
            }
            Object userValue = user.get(key);
            if (!(defaultValue instanceof Map) || !(userValue instanceof Map)) continue;
            Map defaultMap = (Map)defaultValue;
            Map userMap = (Map)userValue;
            missing += ConfigUpdater.countMissingLeafKeys(userMap, defaultMap);
        }
        return missing;
    }

    private static int countLeafKeys(Object value) {
        if (value instanceof Map) {
            Map map = (Map)value;
            int sum = 0;
            for (Object sub : map.values()) {
                sum += ConfigUpdater.countLeafKeys(sub);
            }
            return sum;
        }
        return 1;
    }

    private static void collectMissingInsertionPaths(Map<String, Object> user, Map<String, Object> defaults, String prefix, List<String> out) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String path;
            String key = entry.getKey();
            Object defaultValue = entry.getValue();
            String string = path = prefix.isEmpty() ? key : prefix + "." + key;
            if (!user.containsKey(key)) {
                out.add(path);
                continue;
            }
            Object userValue = user.get(key);
            if (!(defaultValue instanceof Map) || !(userValue instanceof Map)) continue;
            Map defaultMap = (Map)defaultValue;
            Map userMap = (Map)userValue;
            ConfigUpdater.collectMissingInsertionPaths(userMap, defaultMap, path, out);
        }
    }

    private static void backupFile(Path file) throws IOException {
        if (file == null || !Files.exists(file, new LinkOption[0])) {
            return;
        }
        String originalName = file.getFileName().toString();
        int dot = originalName.lastIndexOf(46);
        String baseName = dot > 0 ? originalName.substring(0, dot) : originalName;
        String extension = dot > 0 ? originalName.substring(dot) : "";
        String stamp = LocalDateTime.now().format(BACKUP_STAMP);
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        Path backup = parent.resolve(baseName + "-backup-" + stamp + extension);
        int suffix = 1;
        while (Files.exists(backup, new LinkOption[0])) {
            backup = parent.resolve(baseName + "-backup-" + stamp + "-" + suffix + extension);
            ++suffix;
        }
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sanitizeYamlText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); ++i) {
            boolean isC1Control;
            char ch = input.charAt(i);
            boolean isAllowedControl = ch == '\n' || ch == '\r' || ch == '\t';
            boolean isPrintable = ch >= ' ' && ch != '\u007f';
            boolean bl = isC1Control = ch >= '\u0080' && ch <= '\u009f';
            if (!isAllowedControl && (!isPrintable || isC1Control)) continue;
            out.append(ch);
        }
        return out.toString();
    }

    private static int findEndOfBlock(List<String> lines, int keyLineIndex, int keyIndent) {
        int prevIndent;
        String prev;
        ParsedKeyLine parsed;
        int end;
        for (end = keyLineIndex + 1; end < lines.size() && ((parsed = ParsedKeyLine.parse(lines.get(end))) == null || parsed.indent > keyIndent); ++end) {
        }
        while (end > keyLineIndex + 1 && ConfigUpdater.isCommentOrBlank(prev = lines.get(end - 1)) && (prevIndent = ConfigUpdater.countLeadingSpaces(prev)) <= keyIndent) {
            --end;
        }
        return end;
    }

    private static List<String> adjustIndent(List<String> snippet, int fromIndent, int toIndent) {
        if (snippet == null || snippet.isEmpty() || fromIndent == toIndent) {
            return snippet == null ? List.of() : new ArrayList<String>(snippet);
        }
        int shift = toIndent - fromIndent;
        ArrayList<String> adjusted = new ArrayList<String>(snippet.size());
        for (String line : snippet) {
            if (line == null || line.trim().isEmpty()) {
                adjusted.add(line == null ? "" : line);
                continue;
            }
            int leading = ConfigUpdater.countLeadingSpaces(line);
            if (leading < fromIndent) {
                adjusted.add(line);
                continue;
            }
            int newLeading = Math.max(0, leading + shift);
            adjusted.add(" ".repeat(newLeading) + line.substring(leading));
        }
        return adjusted;
    }

    private static boolean isCommentOrBlank(String line) {
        if (line == null) {
            return true;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int countLeadingSpaces(String line) {
        int count;
        for (count = 0; count < line.length() && line.charAt(count) == ' '; ++count) {
        }
        return count;
    }

    private static final class TemplateIndex {
        private final List<String> lines;
        private final Map<String, TemplateKey> keysByPath;

        private TemplateIndex(List<String> lines, Map<String, TemplateKey> keysByPath) {
            this.lines = lines;
            this.keysByPath = keysByPath;
        }

        static TemplateIndex fromLines(List<String> lines) {
            LinkedHashMap<String, TemplateKey> keys = new LinkedHashMap<String, TemplateKey>();
            ArrayDeque<Section> stack = new ArrayDeque<Section>();
            for (int i = 0; i < lines.size(); ++i) {
                ParsedKeyLine parsed = ParsedKeyLine.parse(lines.get(i));
                if (parsed == null) continue;
                while (!stack.isEmpty() && parsed.indent <= ((Section)stack.peek()).indent) {
                    stack.pop();
                }
                String path = stack.isEmpty() ? parsed.key : ((Section)stack.peek()).path + "." + parsed.key;
                keys.put(path, new TemplateKey(i, parsed.indent));
                if (!parsed.isSection) continue;
                stack.push(new Section(path, parsed.indent));
            }
            return new TemplateIndex(lines, keys);
        }

        List<String> extractSnippet(String path) {
            int prevIndent;
            String prev;
            String line;
            ParsedKeyLine parsed;
            int end;
            String prev2;
            int start;
            TemplateKey key = this.keysByPath.get(path);
            if (key == null) {
                return List.of();
            }
            int keyLine = key.lineIndex;
            int keyIndent = key.indent;
            for (start = keyLine; start > 0 && ConfigUpdater.isCommentOrBlank(prev2 = this.lines.get(start - 1)); --start) {
                int prevIndent2 = ConfigUpdater.countLeadingSpaces(prev2);
                if (!prev2.trim().isEmpty() && prevIndent2 < keyIndent) break;
            }
            for (end = keyLine + 1; end < this.lines.size() && ((parsed = ParsedKeyLine.parse(line = this.lines.get(end))) == null || parsed.indent > keyIndent); ++end) {
            }
            while (end > keyLine + 1 && ConfigUpdater.isCommentOrBlank(prev = this.lines.get(end - 1)) && (prevIndent = ConfigUpdater.countLeadingSpaces(prev)) <= keyIndent) {
                --end;
            }
            return new ArrayList<String>(this.lines.subList(start, end));
        }
    }

    private static final class UserIndex {
        private final List<String> lines;
        private final Map<String, UserSection> sectionsByPath;

        private UserIndex(List<String> lines, Map<String, UserSection> sectionsByPath) {
            this.lines = lines;
            this.sectionsByPath = sectionsByPath;
        }

        static UserIndex fromLines(List<String> lines) {
            LinkedHashMap<String, UserSection> sections = new LinkedHashMap<String, UserSection>();
            ArrayDeque<Section> stack = new ArrayDeque<Section>();
            for (int i = 0; i < lines.size(); ++i) {
                String path;
                ParsedKeyLine parsed = ParsedKeyLine.parse(lines.get(i));
                if (parsed == null) continue;
                while (!stack.isEmpty() && parsed.indent <= ((Section)stack.peek()).indent) {
                    stack.pop();
                }
                String string = path = stack.isEmpty() ? parsed.key : ((Section)stack.peek()).path + "." + parsed.key;
                if (!parsed.isSection) continue;
                sections.put(path, new UserSection(i, parsed.indent));
                stack.push(new Section(path, parsed.indent));
            }
            return new UserIndex(lines, sections);
        }

        ParentAnchor findParentAnchor(String missingKeyPath) {
            if (missingKeyPath == null || missingKeyPath.isBlank() || !missingKeyPath.contains(".")) {
                return new ParentAnchor(true, this.lines.size(), 0);
            }
            int lastDot = missingKeyPath.lastIndexOf(46);
            if (lastDot <= 0) {
                return new ParentAnchor(true, this.lines.size(), 0);
            }
            String parentPath = missingKeyPath.substring(0, lastDot);
            UserSection parentSection = this.sectionsByPath.get(parentPath);
            if (parentSection == null) {
                return new ParentAnchor(false, 0, 0);
            }
            int insertAt = ConfigUpdater.findEndOfBlock(this.lines, parentSection.lineIndex, parentSection.indent);
            int targetIndent = parentSection.indent + 2;
            return new ParentAnchor(true, insertAt, targetIndent);
        }
    }

    private static final class TemplateKey {
        private final int lineIndex;
        private final int indent;

        private TemplateKey(int lineIndex, int indent) {
            this.lineIndex = lineIndex;
            this.indent = indent;
        }
    }

    private static final class ParentAnchor {
        private final boolean valid;
        private final int insertAtLine;
        private final int targetIndent;

        private ParentAnchor(boolean valid, int insertAtLine, int targetIndent) {
            this.valid = valid;
            this.insertAtLine = insertAtLine;
            this.targetIndent = targetIndent;
        }
    }

    private static final class Insertion {
        private final int insertAtLine;
        private final int templateLineIndex;
        private final List<String> lines;

        private Insertion(int insertAtLine, int templateLineIndex, List<String> lines) {
            this.insertAtLine = insertAtLine;
            this.templateLineIndex = templateLineIndex;
            this.lines = lines;
        }
    }

    private static final class ParsedKeyLine {
        private final String key;
        private final int indent;
        private final boolean isSection;

        private ParsedKeyLine(String key, int indent, boolean isSection) {
            this.key = key;
            this.indent = indent;
            this.isSection = isSection;
        }

        static ParsedKeyLine parse(String line) {
            if (line == null) {
                return null;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                return null;
            }
            int colon = trimmed.indexOf(58);
            if (colon <= 0) {
                return null;
            }
            String key = trimmed.substring(0, colon).trim();
            if (key.isEmpty()) {
                return null;
            }
            int indent = ConfigUpdater.countLeadingSpaces(line);
            String afterColon = trimmed.substring(colon + 1);
            boolean section = afterColon.trim().isEmpty() || afterColon.trim().startsWith("#");
            return new ParsedKeyLine(key, indent, section);
        }
    }

    private static final class Section {
        private final String path;
        private final int indent;

        private Section(String path, int indent) {
            this.path = path;
            this.indent = indent;
        }
    }

    private static final class UserSection {
        private final int lineIndex;
        private final int indent;

        private UserSection(int lineIndex, int indent) {
            this.lineIndex = lineIndex;
            this.indent = indent;
        }
    }
}
