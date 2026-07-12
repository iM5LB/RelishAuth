package relish.relishAuthVelocity.utils;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.slf4j.Logger;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.utils.BackendServerResolver;

public class ConnectionUtil {
    public static void connectPlayerToBackend(Player player, ProxyServer server, Config config, Logger logger, boolean debugEnabled) {
        Optional<RegisteredServer> backend = BackendServerResolver.resolvePostAuthServer(server, config);
        if (backend.isPresent()) {
            RegisteredServer targetServer = backend.get();
            if (debugEnabled) {
                logger.info("[CONNECTION] Connecting {} to backend server {}", (Object)player.getUsername(), (Object)targetServer.getServerInfo().getName());
            }
            player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    if (debugEnabled) {
                        logger.info("[CONNECTION] Successfully connected {} to {}", (Object)player.getUsername(), (Object)targetServer.getServerInfo().getName());
                    }
                } else {
                    logger.error("[CONNECTION] Failed to connect {} to backend: {}", (Object)player.getUsername(), (Object)result.getReasonComponent().orElse(Component.text((String)"Unknown reason")));
                    player.disconnect((Component)Component.text((String)"Failed to connect to server", (TextColor)NamedTextColor.RED));
                }
            });
        } else {
            logger.error("[CONNECTION] No backend server available for {}", (Object)player.getUsername());
            player.disconnect((Component)Component.text((String)"No backend server available", (TextColor)NamedTextColor.RED));
        }
    }
}
