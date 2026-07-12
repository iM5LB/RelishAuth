package relish.relishAuthVelocity.integrations;

import org.slf4j.Logger;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.integrations.DiscordIntegration;
import relish.relishAuthVelocity.integrations.NoopDiscordIntegration;

public final class DiscordIntegrationLoader {
    private static final String DEFAULT_TOKEN_PLACEHOLDER = "YOUR_BOT_TOKEN_HERE";
    private static final String DISCORD_IMPL_CLASS = "relish.relishAuthVelocity.discord.DiscordBot";

    private DiscordIntegrationLoader() {
    }

    public static DiscordIntegration load(RelishAuthVelocity plugin, Config config, Logger logger) {
        NoopDiscordIntegration noop = new NoopDiscordIntegration();
        if (plugin == null || config == null || logger == null) {
            return noop;
        }
        boolean required = "discord".equalsIgnoreCase(config.getString("authentication.method", "password"));
        boolean configured = DiscordIntegrationLoader.isDiscordTokenConfigured(config);
        if (!required && !configured) {
            return noop;
        }
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            Class<?> implClass = Class.forName(DISCORD_IMPL_CLASS, true, classLoader);
            Object instance = implClass.getConstructor(RelishAuthVelocity.class, Config.class, Logger.class).newInstance(plugin, config, logger);
            if (instance instanceof DiscordIntegration) {
                DiscordIntegration integration = (DiscordIntegration)instance;
                return integration;
            }
            logger.warn("[DISCORD] Discord integration class does not implement DiscordIntegration ({}), disabled", (Object)DISCORD_IMPL_CLASS);
            return noop;
        }
        catch (ClassNotFoundException e) {
            if (required) {
                throw new IllegalStateException("authentication.method=discord but this RelishAuth build does not include Discord support. Use the full jar build.", e);
            }
            logger.info("[DISCORD] Discord support not bundled in this build, skipping Discord init");
            return noop;
        }
        catch (Throwable t) {
            if (required) {
                throw new IllegalStateException("Discord authentication is enabled but Discord bot failed to initialize: " + t.getMessage(), t);
            }
            logger.warn("[DISCORD] Failed to initialize Discord bot (disabled): {}", (Object)t.getMessage());
            return noop;
        }
    }

    private static boolean isDiscordTokenConfigured(Config config) {
        String token = config.getString("discord.bot-token", DEFAULT_TOKEN_PLACEHOLDER);
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        return !trimmed.isEmpty() && !DEFAULT_TOKEN_PLACEHOLDER.equals(trimmed);
    }
}
