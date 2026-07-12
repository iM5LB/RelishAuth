package relish.relishAuthVelocity.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;
import relish.relishAuthVelocity.exceptions.ConfigurationException;
import relish.relishAuthVelocity.exceptions.PluginException;

public class Config {
    private Map<String, Object> config;
    private final Path configPath;
    private final List<String> validationErrors = new ArrayList<String>();
    private static final Set<String> REQUIRED_KEYS = Set.of("database.type");

    public Config(Path dataDirectory) throws ConfigurationException {
        this.configPath = dataDirectory.resolve("config.yml");
        this.load(dataDirectory);
        this.validate();
    }

    private void load(Path dataDirectory) throws ConfigurationException {
        File configFile = this.configPath.toFile();
        try {
            if (!Files.exists(dataDirectory, new LinkOption[0])) {
                Files.createDirectories(dataDirectory, new FileAttribute[0]);
            }
            if (!configFile.exists()) {
                try (InputStream in = this.getClass().getResourceAsStream("/config.yml");){
                    if (in == null) {
                        throw new ConfigurationException(PluginException.ErrorCode.CONFIG_LOAD_FAILED, "Default config.yml not found in resources");
                    }
                    Files.copy(in, configFile.toPath(), new CopyOption[0]);
                }
            }
            Yaml yaml = new Yaml();
            try (FileInputStream fis = new FileInputStream(configFile);){
                Map configMap;
                Object loaded = yaml.load(fis);
                if (loaded == null) {
                    throw new ConfigurationException(PluginException.ErrorCode.CONFIG_LOAD_FAILED, "Configuration file is empty or invalid");
                }
                if (!(loaded instanceof Map)) {
                    throw new ConfigurationException(PluginException.ErrorCode.CONFIG_LOAD_FAILED, "Configuration file has invalid format");
                }
                this.config = configMap = (Map)loaded;
            }
        }
        catch (ConfigurationException e) {
            throw e;
        }
        catch (IOException e) {
            throw new ConfigurationException(PluginException.ErrorCode.CONFIG_LOAD_FAILED, "Failed to load configuration file: " + e.getMessage(), e);
        }
        catch (Exception e) {
            throw new ConfigurationException(PluginException.ErrorCode.CONFIG_LOAD_FAILED, "Unexpected error loading configuration: " + e.getMessage(), e);
        }
    }

    private void validate() throws ConfigurationException {
        this.validationErrors.clear();
        for (String key : REQUIRED_KEYS) {
            if (this.getNestedValue(key) != null) continue;
            this.validationErrors.add("Missing required configuration: " + key);
        }
        this.validateDatabaseConfig();
        this.validateAuthConfig();
        this.validateSessionConfig();
        this.validateSecurityConfig();
        this.validateApiConfig();
        this.validateDiscordConfig();
        this.validateGroupSyncConfig();
        this.validateCustomizationConfig();
        this.validateCommandsConfig();
    }

    private void validateDatabaseConfig() {
        String dbType = this.getString("database.type", "sqlite").toLowerCase();
        if (!(dbType.equals("sqlite") || dbType.equals("mysql") || dbType.equals("mariadb") || dbType.equals("postgresql") || dbType.equals("postgres"))) {
            this.validationErrors.add("Invalid database.type: " + dbType + ". Must be sqlite, mysql, mariadb, or postgresql");
        }
        if (dbType.equals("mysql") || dbType.equals("mariadb")) {
            long maxLifetime;
            long idleTimeout;
            long connectionTimeout;
            int minIdle;
            int maxPoolSize;
            int port;
            if (this.getString("database.mysql.host", "").isEmpty()) {
                this.validationErrors.add("database.mysql.host is required for MySQL/MariaDB");
            }
            if ((port = this.getInt("database.mysql.port", 3306)) < 1 || port > 65535) {
                this.validationErrors.add("database.mysql.port must be between 1 and 65535");
            }
            if (this.getString("database.mysql.database", "").isEmpty()) {
                this.validationErrors.add("database.mysql.database name is required for MySQL/MariaDB");
            }
            if (this.getString("database.mysql.username", "").isEmpty()) {
                this.validationErrors.add("database.mysql.username is required for MySQL/MariaDB");
            }
            if ((maxPoolSize = this.getInt("database.mysql.pool.maximum-pool-size", 10)) < 1 || maxPoolSize > 100) {
                this.validationErrors.add("database.mysql.pool.maximum-pool-size must be between 1 and 100");
            }
            if ((minIdle = this.getInt("database.mysql.pool.minimum-idle", 5)) < 0 || minIdle > maxPoolSize) {
                this.validationErrors.add("database.mysql.pool.minimum-idle must be between 0 and maximum-pool-size");
            }
            if ((connectionTimeout = this.getLong("database.mysql.pool.connection-timeout", 30000L)) < 1000L || connectionTimeout > 300000L) {
                this.validationErrors.add("database.mysql.pool.connection-timeout must be between 1000 and 300000 milliseconds");
            }
            if ((idleTimeout = this.getLong("database.mysql.pool.idle-timeout", 600000L)) < 10000L || idleTimeout > 3600000L) {
                this.validationErrors.add("database.mysql.pool.idle-timeout must be between 10000 and 3600000 milliseconds");
            }
            if ((maxLifetime = this.getLong("database.mysql.pool.max-lifetime", 1800000L)) < 30000L || maxLifetime > 0x6DDD00L) {
                this.validationErrors.add("database.mysql.pool.max-lifetime must be between 30000 and 7200000 milliseconds");
            }
        }
        if (dbType.equals("sqlite")) {
            String path = this.getString("database.sqlite.path", "data.db");
            if (path.isEmpty()) {
                this.validationErrors.add("database.sqlite.path cannot be empty");
            }
            if (!path.endsWith(".db")) {
                this.validationErrors.add("database.sqlite.path should end with .db extension");
            }
        }
    }

    private void validateAuthConfig() {
        int rounds;
        String hashing;
        String botToken;
        String authMethod = this.getString("authentication.method", "password").toLowerCase();
        if (!authMethod.equals("password") && !authMethod.equals("discord")) {
            this.validationErrors.add("Invalid authentication.method: " + authMethod + ". Must be password or discord");
        }
        if (authMethod.equals("discord") && ((botToken = this.getString("discord.bot-token", "")).isEmpty() || botToken.equals("YOUR_BOT_TOKEN_HERE"))) {
            this.validationErrors.add("discord.bot-token is required when authentication.method is discord");
        }
        int minLength = this.getInt("authentication.password.min-length", 6);
        int maxLength = this.getInt("authentication.password.max-length", 32);
        if (minLength < 1) {
            this.validationErrors.add("authentication.password.min-length must be at least 1");
        }
        if (maxLength > 128) {
            this.validationErrors.add("authentication.password.max-length cannot exceed 128");
        }
        if (minLength > maxLength) {
            this.validationErrors.add("authentication.password.min-length cannot be greater than max-length");
        }
        if (!(hashing = this.getString("authentication.password.hashing", "argon2").toLowerCase()).equals("argon2") && !hashing.equals("bcrypt2y")) {
            this.validationErrors.add("Invalid authentication.password.hashing: " + hashing + ". Must be argon2 or bcrypt2y");
        }
        if (hashing.equals("argon2")) {
            int iterations = this.getInt("authentication.password.argon2.iterations", 10);
            int memory = this.getInt("authentication.password.argon2.memory", 65536);
            int parallelism = this.getInt("authentication.password.argon2.parallelism", 1);
            if (iterations < 1 || iterations > 100) {
                this.validationErrors.add("authentication.password.argon2.iterations must be between 1 and 100");
            }
            if (memory < 1024 || memory > 0x100000) {
                this.validationErrors.add("authentication.password.argon2.memory must be between 1024 and 1048576 KB");
            }
            if (parallelism < 1 || parallelism > 16) {
                this.validationErrors.add("authentication.password.argon2.parallelism must be between 1 and 16");
            }
        }
        if (hashing.equals("bcrypt2y") && ((rounds = this.getInt("authentication.password.bcrypt.rounds", 12)) < 4 || rounds > 31)) {
            this.validationErrors.add("authentication.password.bcrypt.rounds must be between 4 and 31");
        }
    }

    private void validateSessionConfig() {
        List<String> availableDurations;
        String duration = this.getString("session.duration", "1h");
        if (!this.isValidDuration(duration)) {
            this.validationErrors.add("Invalid session.duration: " + duration + ". Valid formats: 0, 5m, 15m, 30m, 1h");
        }
        if ((availableDurations = this.getStringList("session.available-durations")).isEmpty()) {
            this.validationErrors.add("session.available-durations must have at least one option");
        } else {
            for (String d : availableDurations) {
                if (this.isValidDuration(d)) continue;
                this.validationErrors.add("Invalid duration in session.available-durations: " + d);
            }
        }
    }

    private void validateSecurityConfig() {
        int apiReadTimeout;
        int apiConnectTimeout;
        int verificationTimeout;
        int warningInterval;
        int warningThreshold;
        int limboReadTimeout;
        int authTimeout;
        int lockDuration;
        int maxAttempts = this.getInt("security.password-attempts.max-attempts", 3);
        if (maxAttempts < 1 || maxAttempts > 100) {
            this.validationErrors.add("security.password-attempts.max-attempts must be between 1 and 100");
        }
        if ((lockDuration = this.getInt("security.password-attempts.lock-duration", 15)) < 1 || lockDuration > 1440) {
            this.validationErrors.add("security.password-attempts.lock-duration must be between 1 and 1440 minutes");
        }
        if ((authTimeout = this.getInt("security.authentication-timeout", 300)) < 30 || authTimeout > 3600) {
            this.validationErrors.add("security.authentication-timeout must be between 30 and 3600 seconds");
        }
        if ((limboReadTimeout = this.getInt("security.limbo-read-timeout-ms", 120000)) < 1000 || limboReadTimeout > 600000) {
            this.validationErrors.add("security.limbo-read-timeout-ms must be between 1000 and 600000 milliseconds");
        }
        if ((warningThreshold = this.getInt("security.timeout-warnings.warning-threshold", 30)) < 1 || warningThreshold > authTimeout) {
            this.validationErrors.add("security.timeout-warnings.warning-threshold must be between 1 and authentication-timeout");
        }
        if ((warningInterval = this.getInt("security.timeout-warnings.warning-interval", 10)) < 1 || warningInterval > 60) {
            this.validationErrors.add("security.timeout-warnings.warning-interval must be between 1 and 60 seconds");
        }
        if ((verificationTimeout = this.getInt("security.premium.verification-timeout", 5)) < 1 || verificationTimeout > 30) {
            this.validationErrors.add("security.premium.verification-timeout must be between 1 and 30 seconds");
        }
        if ((apiConnectTimeout = this.getInt("security.premium.api-connect-timeout", 5000)) < 1000 || apiConnectTimeout > 30000) {
            this.validationErrors.add("security.premium.api-connect-timeout must be between 1000 and 30000 milliseconds");
        }
        if ((apiReadTimeout = this.getInt("security.premium.api-read-timeout", 5000)) < 1000 || apiReadTimeout > 30000) {
            this.validationErrors.add("security.premium.api-read-timeout must be between 1000 and 30000 milliseconds");
        }
    }

    private void validateApiConfig() {
        this.validateOptionalUrl("skins.api.username-textures-endpoint");
        this.validateOptionalUrl("skins.api.username-uuid-lookup-endpoint");
        this.validateOptionalUrl("skins.api.uuid-session-endpoint");
        this.validateOptionalUrl("skins.api.elyby-textures-endpoint");
        this.validateOptionalUrl("skins.api.mojang-profile-lookup-endpoint");
        this.validateOptionalUrl("skins.api.mojang-session-endpoint");
        this.validateOptionalUrl("security.premium.api-url");
        String language = this.getString("language", "en");
        if (!language.matches("[a-z]{2}")) {
            this.validationErrors.add("language must be a 2-letter language code (e.g., en, ar)");
        }
    }

    private void validateOptionalUrl(String path) {
        String value = this.getString(path, "");
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            this.validationErrors.add(path + " must start with http:// or https://");
        }
    }

    private void validateDiscordConfig() {
        String botToken = this.getString("discord.bot-token", "");
        if (!botToken.isEmpty() && !botToken.equals("YOUR_BOT_TOKEN_HERE")) {
            int updateInterval;
            int cooldown;
            int memberLoadTimeout;
            int shutdownTimeout;
            int buttonExpiration = this.getInt("discord.button-expiration-minutes", 5);
            if (buttonExpiration < 1 || buttonExpiration > 60) {
                this.validationErrors.add("discord.button-expiration-minutes must be between 1 and 60");
            }
            if ((shutdownTimeout = this.getInt("discord.shutdown-timeout", 5)) < 1 || shutdownTimeout > 60) {
                this.validationErrors.add("discord.shutdown-timeout must be between 1 and 60 seconds");
            }
            this.validateRgbColor("discord.colors.blue", "88,101,242");
            this.validateRgbColor("discord.colors.red", "237,66,69");
            this.validateRgbColor("discord.colors.green", "87,242,135");
            this.validateRgbColor("discord.colors.warning", "255,193,7");
            int requestTimeout = this.getInt("discord.api.request-timeout", 10);
            if (requestTimeout < 1 || requestTimeout > 60) {
                this.validationErrors.add("discord.api.request-timeout must be between 1 and 60 seconds");
            }
            if ((memberLoadTimeout = this.getInt("discord.api.member-load-timeout", 5)) < 1 || memberLoadTimeout > 30) {
                this.validationErrors.add("discord.api.member-load-timeout must be between 1 and 30 seconds");
            }
            if ((cooldown = this.getInt("discord.join-notifications.cooldown", 60)) < 0 || cooldown > 3600) {
                this.validationErrors.add("discord.join-notifications.cooldown must be between 0 and 3600 seconds");
            }
            if (this.getBoolean("discord.status.enabled", true) && ((updateInterval = this.getInt("discord.status.update-interval", 120)) < 60 || updateInterval > 3600)) {
                this.validationErrors.add("discord.status.update-interval must be between 60 and 3600 seconds");
            }
        }
    }

    private void validateGroupSyncConfig() {
        Object raw = this.get("group-sync.role-to-group");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map)) {
            this.validationErrors.add("group-sync.role-to-group must be a map of Minecraft group names to Discord role IDs");
            return;
        }
        Map<?, ?> map = (Map<?, ?>)raw;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String roleId;
            String groupName = entry.getKey() == null ? "" : entry.getKey().toString().trim();
            String string = roleId = entry.getValue() == null ? "" : entry.getValue().toString().trim();
            if (groupName.isEmpty()) {
                this.validationErrors.add("group-sync.role-to-group contains an empty Minecraft group name");
            }
            if (roleId.isEmpty() || roleId.matches("\\d{17,20}")) continue;
            this.validationErrors.add("group-sync.role-to-group." + groupName + " must be a Discord role ID");
        }
    }

    private void validateRgbColor(String path, String defaultValue) {
        String colorStr = this.getString(path, defaultValue);
        String[] parts = colorStr.split(",");
        if (parts.length != 3) {
            this.validationErrors.add(path + " must be in RGB format (e.g., 255,255,255)");
            return;
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0 && value <= 255) continue;
                this.validationErrors.add(path + " RGB values must be between 0 and 255");
                return;
            }
            catch (NumberFormatException e) {
                this.validationErrors.add(path + " must contain valid RGB numbers");
                return;
            }
        }
    }

    private void validateCustomizationConfig() {
        String bossbarOverlay;
        Set<String> validBossbarOverlays;
        String bossbarColor;
        Set<String> validBossbarColors;
        int checkInterval;
        int discordPromptDelay;
        int authPromptDelay = this.getInt("customization.limbo.timing.auth-prompt-delay", 100);
        if (authPromptDelay < 0 || authPromptDelay > 10000) {
            this.validationErrors.add("customization.limbo.timing.auth-prompt-delay must be between 0 and 10000 milliseconds");
        }
        if ((discordPromptDelay = this.getInt("customization.limbo.timing.discord-prompt-delay", 1000)) < 0 || discordPromptDelay > 10000) {
            this.validationErrors.add("customization.limbo.timing.discord-prompt-delay must be between 0 and 10000 milliseconds");
        }
        if ((checkInterval = this.getInt("customization.limbo.monitor.check-interval", 1)) < 1 || checkInterval > 60) {
            this.validationErrors.add("customization.limbo.monitor.check-interval must be between 1 and 60 seconds");
        }
        if (!(validBossbarColors = Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE")).contains(bossbarColor = this.getString("customization.limbo.bossbar.color", "BLUE").toUpperCase())) {
            this.validationErrors.add("customization.limbo.bossbar.color must be one of: " + String.join((CharSequence)", ", validBossbarColors));
        }
        if (!(validBossbarOverlays = Set.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20")).contains(bossbarOverlay = this.getString("customization.limbo.bossbar.overlay", "PROGRESS").toUpperCase())) {
            this.validationErrors.add("customization.limbo.bossbar.overlay must be one of: " + String.join((CharSequence)", ", validBossbarOverlays));
        }
    }

    private void validateCommandsConfig() {
        int disconnectDelay = this.getInt("commands.logout.disconnect-delay", 100);
        if (disconnectDelay < 0 || disconnectDelay > 10000) {
            this.validationErrors.add("commands.logout.disconnect-delay must be between 0 and 10000 milliseconds");
        }
    }

    private boolean isValidDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return false;
        }
        if (duration.equals("0")) {
            return true;
        }
        return duration.matches("\\d+[smhd]");
    }

    public List<String> getValidationErrors() {
        return Collections.unmodifiableList(this.validationErrors);
    }

    public boolean hasValidationErrors() {
        return !this.validationErrors.isEmpty();
    }

    public String getString(String path) {
        return this.getString(path, "");
    }

    public String getString(String path, String def) {
        Object value = this.getNestedValue(path);
        return value != null ? value.toString() : def;
    }

    public int getInt(String path) {
        return this.getInt(path, 0);
    }

    public int getInt(String path, int def) {
        Object value = this.getNestedValue(path);
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String)value);
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return def;
    }

    public long getLong(String path) {
        return this.getLong(path, 0L);
    }

    public long getLong(String path, long def) {
        Object value = this.getNestedValue(path);
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String)value);
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return def;
    }

    public double getDouble(String path) {
        return this.getDouble(path, 0.0);
    }

    public double getDouble(String path, double def) {
        Object value = this.getNestedValue(path);
        if (value instanceof Number) {
            return ((Number)value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String)value);
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return def;
    }

    public boolean getBoolean(String path) {
        return this.getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = this.getNestedValue(path);
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            String str = ((String)value).toLowerCase();
            if (str.equals("true") || str.equals("yes") || str.equals("1")) {
                return true;
            }
            if (str.equals("false") || str.equals("no") || str.equals("0")) {
                return false;
            }
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object value = this.getNestedValue(path);
        if (value instanceof List) {
            ArrayList<String> result = new ArrayList<String>();
            for (Object item : (List)value) {
                if (item == null) continue;
                result.add(item.toString());
            }
            return result;
        }
        return new ArrayList<String>();
    }

    public Object get(String path) {
        return this.getNestedValue(path);
    }

    public boolean contains(String path) {
        return this.getNestedValue(path) != null;
    }

    private Object getNestedValue(String path) {
        if (this.config == null || path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Map current = this.config;
        for (int i = 0; i < parts.length - 1; ++i) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return null;
            }
            current = (Map)next;
        }
        return current.get(parts[parts.length - 1]);
    }

    public void reload() throws ConfigurationException {
        this.load(this.configPath.getParent());
        this.validate();
    }

    public Path getConfigPath() {
        return this.configPath;
    }
}
