package relish.relishAuthVelocity.updater;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import relish.relishAuthVelocity.updater.ConfigUpdater;
import relish.relishAuthVelocity.updater.UpdateChecker;

public class UpdateManager {
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfigUpdater configUpdater;
    private final UpdateChecker updateChecker;

    public UpdateManager(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configUpdater = new ConfigUpdater(logger, dataDirectory.resolve("config.yml"));
        this.updateChecker = new UpdateChecker(logger, "1.1.0");
    }

    public boolean updateConfigurationFiles() {
        try (InputStream defaultConfig = this.getClass().getResourceAsStream("/config.yml");){
            if (defaultConfig == null) {
                boolean bl2 = false;
                return bl2;
            }
            byte[] defaultBytes = defaultConfig.readAllBytes();
            int latestVersion = this.readConfigVersion(new String(defaultBytes, StandardCharsets.UTF_8));
            int currentVersion = -1;
            Path cfgPath = this.configUpdaterPath();
            if (Files.exists(cfgPath, new LinkOption[0])) {
                currentVersion = this.readConfigVersion(Files.readString(cfgPath, StandardCharsets.UTF_8));
            }
            boolean changed = this.configUpdater.updateConfigWithDefaults(new ByteArrayInputStream(defaultBytes));
            if (latestVersion > 0 && currentVersion != latestVersion) {
                changed |= this.configUpdater.setRootScalar("config-version", Integer.toString(latestVersion));
                if (currentVersion < latestVersion) {
                    this.logger.info("[CONFIG] Updated config.yml schema version {} -> {}", (Object)currentVersion, (Object)latestVersion);
                }
            }
            boolean bl = changed;
            return bl;
        }
        catch (Exception e) {
            this.logger.debug("Failed to update configuration: {}", (Object)e.getMessage());
            return false;
        }
    }

    private Path configUpdaterPath() {
        return this.configUpdater.getConfigPath();
    }

    private int readConfigVersion(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return -1;
        }
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setAllowDuplicateKeys(false);
            loaderOptions.setProcessComments(false);
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
            Object loaded = yaml.load(yamlContent);
            if (!(loaded instanceof Map)) {
                return -1;
            }
            Map map = (Map)loaded;
            Object value = map.get("config-version");
            if (value instanceof Number) {
                Number n = (Number)value;
                return n.intValue();
            }
            if (value instanceof String) {
                String s = (String)value;
                return Integer.parseInt(s.trim());
            }
            return -1;
        }
        catch (Exception ignored) {
            return -1;
        }
    }

    public CompletableFuture<UpdateChecker.UpdateInfo> checkForPluginUpdates() {
        return this.updateChecker.checkForUpdates().thenApply(info -> {
            if (info.isUpdateAvailable()) {
                this.logger.info("\u001b[93m  \u26a0 Update available: v{} \u2192 v{}\u001b[0m", (Object)"1.1.0", (Object)info.getLatestVersion());
                if (info.getDownloadUrl() != null) {
                    this.logger.info("\u001b[96m    Download: \u001b[0m\u001b[94m{}\u001b[0m", (Object)info.getDownloadUrl());
                }
            }
            return info;
        }).exceptionally(throwable -> {
            this.logger.debug("Failed to check for updates: {}", (Object)throwable.getMessage());
            return new UpdateChecker.UpdateInfo(false, "1.1.0", null, null);
        });
    }

    public ConfigUpdater getConfigUpdater() {
        return this.configUpdater;
    }

    public UpdateChecker getUpdateChecker() {
        return this.updateChecker;
    }

    public boolean updateLanguageFiles(String lang) {
        String targetLang = lang == null || lang.isBlank() ? "en" : lang.trim();
        boolean changed = false;
        changed |= this.updateOneLangFile(targetLang, "plugin.yml");
        return changed |= this.updateOneLangFile(targetLang, "discord.yml");
    }

    private boolean updateOneLangFile(String lang, String fileName) {
        try {
            Path langDir = this.dataDirectory.resolve("lang").resolve(lang);
            Files.createDirectories(langDir, new FileAttribute[0]);
            Path targetPath = langDir.resolve(fileName);
            if (!Files.exists(targetPath, new LinkOption[0])) {
                try (InputStream bundled = this.getBundledLangStream(lang, fileName);){
                    if (bundled == null) {
                        boolean bl = false;
                        return bl;
                    }
                    Files.copy(bundled, targetPath, new CopyOption[0]);
                    this.logger.info("[LANG] Created {}/{} from bundled defaults", (Object)lang, (Object)fileName);
                    boolean bl = true;
                    return bl;
                }
            }
            try (InputStream bundled = this.getBundledLangStream(lang, fileName);){
                if (bundled == null) {
                    boolean bl = false;
                    return bl;
                }
                ConfigUpdater updater = new ConfigUpdater(this.logger, targetPath);
                boolean updated = updater.updateConfigWithDefaults(bundled);
                if (updated) {
                    this.logger.info("[LANG] Updated {}/{} with missing keys", (Object)lang, (Object)fileName);
                }
                boolean bl = updated;
                return bl;
            }
        }
        catch (Exception e) {
            this.logger.debug("Failed to update language file {}/{}: {}", lang, fileName, e.getMessage());
            return false;
        }
    }

    private InputStream getBundledLangStream(String lang, String fileName) {
        InputStream stream = this.getClass().getResourceAsStream("/lang/" + lang + "/" + fileName);
        if (stream != null) {
            return stream;
        }
        return this.getClass().getResourceAsStream("/lang/en/" + fileName);
    }
}
