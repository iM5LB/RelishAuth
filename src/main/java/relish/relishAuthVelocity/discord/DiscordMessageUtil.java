package relish.relishAuthVelocity.discord;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.yaml.snakeyaml.Yaml;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.utils.ColorConfig;

public class DiscordMessageUtil {
    private final RelishAuthVelocity plugin;
    private Map<String, Object> discordConfig;
    private String currentLang;
    private final Map<String, Color> colorCache = new HashMap<String, Color>();
    private final Path dataDirectory;

    public DiscordMessageUtil(RelishAuthVelocity plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataDirectory();
        String configLang = plugin.getConfig().getString("language", "en");
        this.loadLanguage(configLang);
    }

    public void loadLanguage(String lang) {
        File langFolder = this.dataDirectory.resolve("lang").resolve(lang).toFile();
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            System.err.println("Failed to create language folder: " + langFolder.getPath());
        }
        File discordLangFile = new File(langFolder, "discord.yml");
        String finalLang = lang;
        if (!discordLangFile.exists()) {
            try {
                InputStream defaultLang = this.getClass().getResourceAsStream("/lang/" + lang + "/discord.yml");
                if (defaultLang != null) {
                    Files.copy(defaultLang, discordLangFile.toPath(), new CopyOption[0]);
                } else {
                    finalLang = "en";
                    langFolder = this.dataDirectory.resolve("lang").resolve(finalLang).toFile();
                    if (!langFolder.exists() && !langFolder.mkdirs()) {
                        System.err.println("Failed to create English language folder: " + langFolder.getPath());
                    }
                    if (!(discordLangFile = new File(langFolder, "discord.yml")).exists() && (defaultLang = this.getClass().getResourceAsStream("/lang/en/discord.yml")) != null) {
                        Files.copy(defaultLang, discordLangFile.toPath(), new CopyOption[0]);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("Failed to create Discord language file: " + e.getMessage());
            }
        }
        try {
            Yaml yaml = new Yaml();
            try (FileInputStream fis = new FileInputStream(discordLangFile);){
                this.discordConfig = (Map)yaml.load(fis);
            }
            this.currentLang = finalLang;
        }
        catch (Exception e) {
            System.err.println("Failed to load Discord language file: " + e.getMessage());
            this.discordConfig = new HashMap<String, Object>();
        }
    }

    public MessageEmbed buildVerificationEmbed(String playerName, String serverIp, boolean isNewAccount, int timeoutSeconds) {
        String sectionPath = "verification-request";
        String title = isNewAccount ? this.getString(sectionPath + ".title.new-account", "New Account Verification") : this.getString(sectionPath + ".title.returning", "Session Verification");
        String description = isNewAccount ? this.getString(sectionPath + ".description.new-account", "") : this.getString(sectionPath + ".description.returning", "");
        String colorHex = isNewAccount ? this.getString(sectionPath + ".color.new-account", "#5865F2") : this.getString(sectionPath + ".color.returning", "#57F287");
        String serverName = this.getString("defaults.server-name", serverIp);
        description = description.replace("{player}", playerName).replace("{server}", serverName);
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            value = value.replace("{player}", playerName).replace("{server}", serverName).replace("{server_ip}", serverIp);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "Click Verify").replace("{timeout}", this.formatTimeout(timeoutSeconds));
        embed.setFooter(footer, null);
        return embed.build();
    }

    public List<Button> buildVerificationButtons(String sessionId) {
        ArrayList<Button> buttons = new ArrayList<Button>();
        String sectionPath = "verification-request.buttons";
        String verifyLabel = this.getString(sectionPath + ".verify.label", "Verify");
        String verifyEmoji = this.getString(sectionPath + ".verify.emoji", "\u2705");
        String verifyStyle = this.getString(sectionPath + ".verify.style", "success");
        buttons.add(this.createButton(verifyStyle, "verify:" + sessionId, verifyLabel, verifyEmoji));
        String denyLabel = this.getString(sectionPath + ".deny.label", "Not Me");
        String denyEmoji = this.getString(sectionPath + ".deny.emoji", "\ud83d\udeab");
        String denyStyle = this.getString(sectionPath + ".deny.style", "danger");
        buttons.add(this.createButton(denyStyle, "deny:" + sessionId, denyLabel, denyEmoji));
        return buttons;
    }

    public MessageEmbed buildSessionControlEmbed(String playerName, String serverIp, String sessionDuration) {
        String sectionPath = "session-control";
        String title = this.getString(sectionPath + ".title", "Successfully Authenticated");
        String description = this.getString(sectionPath + ".description", "You're now connected!").replace("{server}", serverIp);
        String colorHex = this.getString(sectionPath + ".color", "#57F287");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            value = value.replace("{player}", playerName).replace("{server}", serverIp);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public String getResponse(String key) {
        return this.getString("responses." + key + ".message", "Response not found");
    }

    public MessageEmbed buildResponseEmbed(String key, Map<String, String> replacements) {
        String message = this.getResponse(key);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        String colorHex = this.getString("responses." + key + ".color", "#5865F2");
        return new EmbedBuilder().setDescription(message).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now()).build();
    }

    private Button createButton(String style, String id, String label, String emojiStr) {
        ButtonStyle buttonStyle = switch (style.toLowerCase()) {
            case "success" -> ButtonStyle.SUCCESS;
            case "danger" -> ButtonStyle.DANGER;
            case "secondary" -> ButtonStyle.SECONDARY;
            case "primary" -> ButtonStyle.PRIMARY;
            default -> ButtonStyle.SECONDARY;
        };
        Button button = Button.of(buttonStyle, id, label);
        if (emojiStr != null && !emojiStr.isEmpty()) {
            button = button.withEmoji(Emoji.fromUnicode(emojiStr));
        }
        return button;
    }

    private String getString(String path, String defaultValue) {
        String[] parts = path.split("\\.");
        Map current = this.discordConfig;
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

    private List<Map<String, Object>> getFields(String path) {
        String[] parts = path.split("\\.");
        Map current = this.discordConfig;
        for (int i = 0; i < parts.length - 1; ++i) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return Collections.emptyList();
            }
            current = (Map)next;
        }
        Object value = current.get(parts[parts.length - 1]);
        if (value instanceof List) {
            return (List)value;
        }
        return Collections.emptyList();
    }

    private Color parseColor(String hex) {
        if (this.colorCache.containsKey(hex)) {
            return this.colorCache.get(hex);
        }
        try {
            hex = hex.replace("#", "");
            Color color = new Color(Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16));
            this.colorCache.put(hex, color);
            return color;
        }
        catch (Exception e) {
            return ColorConfig.getBlue(this.plugin.getConfig());
        }
    }

    private String formatTimeout(int seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        }
        int minutes = seconds / 60;
        return minutes + " minute" + (minutes > 1 ? "s" : "");
    }

    public List<Button> buildSessionControlButtons(String sessionId, String discordId) {
        ArrayList<Button> buttons = new ArrayList<Button>();
        String sectionPath = "session-control.buttons";
        String durationLabel = this.getString(sectionPath + ".duration.label", "Session Duration");
        String durationEmoji = this.getString(sectionPath + ".duration.emoji", "\u23f1\ufe0f");
        String durationStyle = this.getString(sectionPath + ".duration.style", "secondary");
        buttons.add(this.createButton(durationStyle, "duration:" + sessionId, durationLabel, durationEmoji));
        String passwordLabel = this.getString(sectionPath + ".password.label", "Password");
        String passwordEmoji = this.getString(sectionPath + ".password.emoji", "\ud83d\udd11");
        String passwordStyle = this.getString(sectionPath + ".password.style", "primary");
        buttons.add(this.createButton(passwordStyle, "set_password:" + discordId, passwordLabel, passwordEmoji));
        String logoutLabel = this.getString(sectionPath + ".logout.label", "Logout");
        String logoutEmoji = this.getString(sectionPath + ".logout.emoji", "\ud83d\udeaa");
        String logoutStyle = this.getString(sectionPath + ".logout.style", "danger");
        buttons.add(this.createButton(logoutStyle, "kickself:" + sessionId, logoutLabel, logoutEmoji));
        return buttons;
    }

    public MessageEmbed buildLogoutEmbed() {
        String sectionPath = "logout";
        String title = this.getString(sectionPath + ".title", "\ud83d\udeaa Logged Out");
        String description = this.getString(sectionPath + ".description", "You have been logged out successfully.");
        String colorHex = this.getString(sectionPath + ".color", "#FFA300");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildSessionDurationPickerEmbed(String currentDuration) {
        String sectionPath = "session-duration-picker";
        String title = this.getString(sectionPath + ".title", "\u23f1\ufe0f Set Session Duration");
        String description = this.getString(sectionPath + ".description", "Choose how long you want to stay logged in");
        String colorHex = this.getString(sectionPath + ".color", "#5865F2");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            value = value.replace("{current_duration}", currentDuration);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildDurationUpdatedEmbed(String durationKey) {
        String sectionPath = "duration-updated";
        String title = this.getString(sectionPath + ".title", "\u2705 Duration Updated");
        String description = this.getString(sectionPath + ".description", "Session duration updated successfully");
        String colorHex = this.getString(sectionPath + ".color", "#57F287");
        description = description.replace("{duration}", durationKey);
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildServerJoinAlertEmbed(String playerName, boolean hasPassword) {
        String sectionPath = "server-join-alert";
        String title = this.getString(sectionPath + ".title", "\ud83d\udd14 Server Join Alert");
        String description = this.getString(sectionPath + ".description", "Player has joined the server");
        String colorHex = this.getString(sectionPath + ".color", "#57F287");
        description = description.replace("{player}", playerName);
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        String passwordStatus = hasPassword ? this.getString("server-join-alert.password-status-set", "\ud83d\udd12 Set") : this.getString("server-join-alert.password-status-not-set", "\u274c Not Set");
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            String condition = (String)field.get("condition");
            if (condition != null && condition.equals("no_password") && hasPassword) continue;
            value = value.replace("{player}", playerName).replace("{password_status}", passwordStatus).replace("{timestamp}", String.valueOf(Instant.now().getEpochSecond()));
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildSessionsClearedEmbed(boolean playerOnline) {
        String sectionPath = playerOnline ? "sessions-cleared" : "sessions-cleared-offline";
        String title = this.getString(sectionPath + ".title", "\u2705 Sessions Cleared");
        String description = this.getString(sectionPath + ".description", "All sessions have been cleared");
        String colorHex = this.getString(sectionPath + ".color", "#57F287");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth Security");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildServerMembershipRequiredEmbed(String playerName, String inviteLink) {
        String sectionPath = "server-membership-required";
        String title = this.getString(sectionPath + ".title", "\ud83d\udeab Discord Server Membership Required");
        String description = this.getString(sectionPath + ".description", "You must be a member of the Discord server");
        String colorHex = this.getString(sectionPath + ".color", "#FFA500");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        boolean hasInvite = inviteLink != null && !inviteLink.isEmpty();
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            String condition = (String)field.get("condition");
            if (condition != null && (condition.equals("has_invite") && !hasInvite || condition.equals("no_invite") && hasInvite)) continue;
            value = value.replace("{player}", playerName).replace("{invite_link}", inviteLink != null ? inviteLink : "");
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth Authentication");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public MessageEmbed buildSecurityAlertEmbed(String playerName, String ipAddress, int attempts) {
        String sectionPath = "security-alert";
        String title = this.getString(sectionPath + ".title", "\ud83d\udea8 Security Alert");
        String description = this.getString(sectionPath + ".description", "Suspicious activity detected");
        String colorHex = this.getString(sectionPath + ".color", "#ED4245");
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(description).setColor(this.parseColor(colorHex)).setTimestamp(Instant.now());
        List<Map<String, Object>> fields = this.getFields(sectionPath + ".fields");
        for (Map<String, Object> field : fields) {
            String name = (String)field.getOrDefault("name", "");
            String value = (String)field.getOrDefault("value", "");
            boolean inline = (Boolean)field.getOrDefault("inline", false);
            value = value.replace("{player}", playerName).replace("{ip}", ipAddress).replace("{attempts}", String.valueOf(attempts)).replace("{timestamp}", String.valueOf(Instant.now().getEpochSecond()));
            if (name.isEmpty() || value.isEmpty()) continue;
            embed.addField(name, value, inline);
        }
        String footer = this.getString(sectionPath + ".footer", "RelishAuth Security");
        embed.setFooter(footer, null);
        return embed.build();
    }

    public void reload() {
        String lang = this.plugin.getConfig().getString("language", "en");
        this.loadLanguage(lang);
    }
}
