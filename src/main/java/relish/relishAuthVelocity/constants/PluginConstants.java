package relish.relishAuthVelocity.constants;

public final class PluginConstants {
    public static final String DISCORD_UNLINKED_PREFIX = "unlinked_";
    public static final String DEFAULT_SESSION_DURATION = "1h";
    public static final String DISCORD_ID_REGEX = "\\d{17,19}";
    public static final int MESSAGE_CACHE_MAX_SIZE = 1000;
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 6;
    public static final int DEFAULT_PASSWORD_MAX_LENGTH = 32;
    public static final int DEFAULT_MAX_PASSWORD_ATTEMPTS = 3;
    public static final int DEFAULT_LOCKOUT_DURATION_MINUTES = 15;
    public static final int DEFAULT_AUTH_TIMEOUT_SECONDS = 300;
    public static final String[] VALID_SESSION_DURATIONS = new String[]{"0", "5m", "15m", "30m", "1h"};

    private PluginConstants() {
        throw new AssertionError((Object)"Cannot instantiate constants class");
    }
}
