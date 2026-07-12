package relish.relishAuthVelocity.utils;

import relish.relishAuthVelocity.config.Config;

public class EmojiConfig {
    public static String getLock(Config config) {
        return config.getString("discord.button-emojis.lock", "\ud83d\udd12");
    }

    public static String getClock(Config config) {
        return config.getString("discord.button-emojis.clock", "\u23f0");
    }

    public static String getHourGlass(Config config) {
        return config.getString("discord.button-emojis.hour-glass", "\ud83d\udd50");
    }

    public static String getProhibited(Config config) {
        return config.getString("discord.button-emojis.prohibited", "\ud83d\udeab");
    }

    public static String getKey(Config config) {
        return config.getString("discord.button-emojis.key", "\ud83d\udd11");
    }

    public static String getBellSlash(Config config) {
        return config.getString("discord.button-emojis.bell-slash", "\ud83d\udd15");
    }

    public static String getCheckmark(Config config) {
        return config.getString("discord.button-emojis.checkmark", "\u2705");
    }

    public static String getXMark(Config config) {
        return config.getString("discord.button-emojis.x-mark", "\u274c");
    }
}
