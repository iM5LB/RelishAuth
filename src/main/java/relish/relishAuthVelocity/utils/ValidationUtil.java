package relish.relishAuthVelocity.utils;

import java.net.InetAddress;
import java.util.regex.Pattern;

public final class ValidationUtil {
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("\\d{17,19}");
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern IP_V4_PATTERN = Pattern.compile("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private ValidationUtil() {
        throw new AssertionError((Object)"Cannot instantiate utility class");
    }

    public static boolean isValidDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return false;
        }
        if (discordId.startsWith("unlinked_")) {
            return false;
        }
        return DISCORD_ID_PATTERN.matcher(discordId).matches();
    }

    public static boolean isValidMinecraftUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return MINECRAFT_USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            InetAddress.getByName(ip);
            return IP_V4_PATTERN.matcher(ip).matches();
        }
        catch (Exception e) {
            return false;
        }
    }

    public static String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        if ((username = username.trim()).startsWith("@")) {
            username = username.substring(1);
        }
        return (username = username.replaceAll("[^a-zA-Z0-9_]", "")).isEmpty() ? null : username;
    }

    public static boolean isRealDiscordId(String discordId) {
        return ValidationUtil.isValidDiscordId(discordId);
    }

    public static boolean isUnlinkedDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return false;
        }
        return discordId.startsWith("unlinked_");
    }
}
