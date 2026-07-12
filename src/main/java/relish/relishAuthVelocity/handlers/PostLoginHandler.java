package relish.relishAuthVelocity.handlers;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.utils.BackendServerResolver;

public class PostLoginHandler {
    private final RelishAuthVelocity plugin;

    public PostLoginHandler(RelishAuthVelocity plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
    }

    @Subscribe(order=PostOrder.FIRST)
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            this.plugin.getLogger().warn("[POST-LOGIN] Received event with null player");
            return;
        }
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        try {
            this.plugin.debug("[POST-LOGIN] PlayerChooseInitialServer for {} (UUID: {})", username, uuid);
            if (this.plugin.getAuthManager() != null && this.plugin.getAuthManager().isAuthenticated(uuid, username)) {
                this.plugin.debug("[POST-LOGIN] Player {} already authenticated, routing to backend", username);
                this.routeToBackend(event, player, username);
                return;
            }
            if (this.plugin.getLimboHandler() != null && this.plugin.getLimboHandler().isPendingLimbo(uuid, username)) {
                this.plugin.debug("[POST-LOGIN] Player {} pending limbo handoff, deferring backend routing", username);
                return;
            }
            this.plugin.debug("[POST-LOGIN] Player {} will be handled by limbo", username);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[POST-LOGIN] Error processing initial server for {}: {}", username, e.getMessage(), e);
        }
    }

    private void routeToBackend(PlayerChooseInitialServerEvent event, Player player, String username) {
        try {
            Optional<RegisteredServer> backend = this.findBackendServer(event);
            if (backend.isPresent()) {
                event.setInitialServer(backend.get());
                this.plugin.debug("[POST-LOGIN] Set initial server to {} for {}", backend.get().getServerInfo().getName(), username);
            } else {
                this.plugin.getLogger().error("[POST-LOGIN] No backend server available for {}", (Object)username);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[POST-LOGIN] Error routing {} to backend: {}", username, e.getMessage(), e);
        }
    }

    private Optional<RegisteredServer> findBackendServer(PlayerChooseInitialServerEvent event) {
        Optional initialServer;
        String configured;
        String string = configured = this.plugin.getConfig() != null ? this.plugin.getConfig().getString("routing.post-auth-server", "") : "";
        if ((configured == null || configured.isBlank()) && (initialServer = event.getInitialServer()).isPresent() && this.isBackendServer((RegisteredServer)initialServer.get())) {
            this.plugin.debug("[POST-LOGIN] Respecting Velocity initial server: {}", ((RegisteredServer)initialServer.get()).getServerInfo().getName());
            return initialServer;
        }
        return BackendServerResolver.resolvePostAuthServer(this.plugin.getServer(), this.plugin.getConfig());
    }

    private boolean isBackendServer(RegisteredServer server) {
        if (server == null || server.getServerInfo() == null) {
            return false;
        }
        String name = server.getServerInfo().getName();
        return name != null && !name.equalsIgnoreCase("limbo") && !name.equalsIgnoreCase("auth");
    }
}
