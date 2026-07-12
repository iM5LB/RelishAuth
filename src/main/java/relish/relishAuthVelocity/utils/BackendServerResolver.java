package relish.relishAuthVelocity.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import relish.relishAuthVelocity.config.Config;

public final class BackendServerResolver {
    private BackendServerResolver() {
    }

    public static Optional<RegisteredServer> resolvePostAuthServer(ProxyServer proxyServer, Config config) {
        Objects.requireNonNull(proxyServer, "proxyServer");
        String configured = config != null ? config.getString("routing.post-auth-server", "") : "";
        Optional<RegisteredServer> configuredServer = BackendServerResolver.resolveNamedServer(proxyServer, configured);
        if (configuredServer.isPresent()) {
            return configuredServer;
        }
        List<String> attemptOrder = proxyServer.getConfiguration().getAttemptConnectionOrder();
        for (String serverName : attemptOrder) {
            Optional<RegisteredServer> server = BackendServerResolver.resolveNamedServer(proxyServer, serverName);
            if (!server.isPresent()) continue;
            return server;
        }
        return proxyServer.getAllServers().stream().filter(s -> !BackendServerResolver.isAuthServerName(s.getServerInfo().getName())).sorted(Comparator.comparing(s -> s.getServerInfo().getName().toLowerCase(Locale.ROOT))).findFirst();
    }

    private static Optional<RegisteredServer> resolveNamedServer(ProxyServer proxyServer, String serverName) {
        if (serverName == null) {
            return Optional.empty();
        }
        String trimmed = serverName.trim();
        if (trimmed.isEmpty() || BackendServerResolver.isAuthServerName(trimmed)) {
            return Optional.empty();
        }
        Optional<RegisteredServer> direct = proxyServer.getServer(trimmed);
        if (direct.isPresent()) {
            return direct;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return proxyServer.getAllServers().stream().filter(s -> s.getServerInfo().getName().toLowerCase(Locale.ROOT).equals(lower)).findFirst();
    }

    private static boolean isAuthServerName(String name) {
        if (name == null) {
            return true;
        }
        return name.equalsIgnoreCase("limbo") || name.equalsIgnoreCase("auth");
    }
}
