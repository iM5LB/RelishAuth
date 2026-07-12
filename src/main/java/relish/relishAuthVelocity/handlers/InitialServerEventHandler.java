package relish.relishAuthVelocity.handlers;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import relish.relishAuthVelocity.RelishAuthVelocity;

public final class InitialServerEventHandler {
    private final RelishAuthVelocity plugin;
    private final Map<UUID, String> initialServerByUuid = new ConcurrentHashMap<UUID, String>();

    public InitialServerEventHandler(RelishAuthVelocity plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Optional server = event.getInitialServer();
        if (server.isEmpty()) {
            return;
        }
        String name = ((RegisteredServer)server.get()).getServerInfo().getName();
        if (name == null || name.isBlank()) {
            return;
        }
        this.initialServerByUuid.put(player.getUniqueId(), name);
        this.plugin.debug("[FORCED-HOST] {} initial server selected: {}", player.getUsername(), name);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        this.initialServerByUuid.remove(player.getUniqueId());
    }

    public String consumeInitialServer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return this.initialServerByUuid.remove(uuid);
    }
}
