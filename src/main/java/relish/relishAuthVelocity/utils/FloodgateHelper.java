package relish.relishAuthVelocity.utils;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.util.UUID;
import org.slf4j.Logger;
import relish.relishAuthVelocity.config.Config;

public class FloodgateHelper {
    private final ProxyServer server;
    private final Logger logger;
    private final Config config;
    private boolean floodgatePresent = false;
    private Object floodgateApi = null;

    public FloodgateHelper(ProxyServer server, Logger logger, Config config) {
        this.server = server;
        this.logger = logger;
        this.config = config;
        this.detectFloodgate();
    }

    private void detectFloodgate() {
        try {
            Class<?> floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstanceMethod = floodgateClass.getMethod("getInstance", new Class[0]);
            this.floodgateApi = getInstanceMethod.invoke(null, new Object[0]);
            this.floodgatePresent = true;
            this.logger.info("[FLOODGATE] Floodgate detected and integrated");
        }
        catch (Exception e) {
            this.logger.info("[FLOODGATE] Floodgate not detected, continuing without Bedrock support");
        }
    }

    public boolean isFloodgatePlayer(Player player) {
        if (player == null) {
            return false;
        }
        return this.isFloodgatePlayer(player.getUniqueId());
    }

    public boolean isFloodgatePlayer(UUID uuid) {
        if (!this.floodgatePresent || this.floodgateApi == null) {
            return false;
        }
        try {
            Method isFloodgatePlayerMethod = this.floodgateApi.getClass().getMethod("isFloodgatePlayer", UUID.class);
            return (Boolean)isFloodgatePlayerMethod.invoke(this.floodgateApi, uuid);
        }
        catch (Exception e) {
            this.logger.error("[FLOODGATE] Error checking if player is Floodgate player", e);
            return false;
        }
    }

    public boolean isFloodgatePresent() {
        return this.floodgatePresent;
    }
}
