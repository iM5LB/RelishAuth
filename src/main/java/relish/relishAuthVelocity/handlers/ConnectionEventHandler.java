package relish.relishAuthVelocity.handlers;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.premium.PremiumVerificationManager;
import relish.relishAuthVelocity.premium.PremiumVerificationSession;
import relish.relishAuthVelocity.services.PremiumVerificationService;

public class ConnectionEventHandler {
    private final RelishAuthVelocity plugin;
    private final PremiumVerificationService premiumVerificationService;
    private final PremiumVerificationManager premiumManager;

    public ConnectionEventHandler(RelishAuthVelocity plugin, PremiumVerificationService premiumVerificationService, PremiumVerificationManager premiumManager) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.premiumVerificationService = Objects.requireNonNull(premiumVerificationService, "PremiumVerificationService cannot be null");
        this.premiumManager = Objects.requireNonNull(premiumManager, "PremiumVerificationManager cannot be null");
    }

    @Subscribe(order=PostOrder.FIRST)
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        try {
            boolean allowBedrock;
            UUID uuid;
            if (username == null || username.isEmpty()) {
                this.plugin.getLogger().warn("[PRE-LOGIN] Rejected connection with empty username");
                Component errorMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-invalid-username") : Component.text((String)"Invalid username", (TextColor)NamedTextColor.RED);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied((Component)errorMsg));
                return;
            }
            try {
                uuid = event.getUniqueId();
            }
            catch (IllegalStateException e) {
                this.plugin.getLogger().warn("[PRE-LOGIN] UUID not available for {} during pre-login", (Object)username);
                return;
            }
            if (uuid == null) {
                this.plugin.getLogger().warn("[PRE-LOGIN] Null UUID for {}", (Object)username);
                return;
            }
            this.plugin.debug("[PRE-LOGIN] {} connecting with UUID {}", username, uuid);
            if (this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePresent() && !(allowBedrock = this.plugin.getConfig().getBoolean("authentication.allow-bedrock-players", true))) {
                this.plugin.debug("[PRE-LOGIN] {} - will check bedrock status after login", username);
            }
            InetAddress address = null;
            try {
                if (event.getConnection() != null && event.getConnection().getRemoteAddress() != null) {
                    address = event.getConnection().getRemoteAddress().getAddress();
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[PRE-LOGIN] Could not get address for {}: {}", (Object)username, (Object)e.getMessage());
            }
            if (this.plugin.getAuthManager() != null) {
                this.plugin.getAuthManager().recordConnection(uuid);
            }
            if (address != null) {
                this.premiumVerificationService.handlePreLogin(event, uuid, username, address);
            }
        }
        catch (PluginException e) {
            this.plugin.getLogger().error("[PRE-LOGIN] Plugin error for {}: {} ({})", username, e.getMessage(), e.getErrorCode().getCode());
            this.handlePreLoginError(event, username, e);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[PRE-LOGIN] Unexpected error for {}: {}", username, e.getMessage(), e);
            this.handlePreLoginError(event, username, e);
        }
    }

    private void handlePreLoginError(PreLoginEvent event, String username, Exception e) {
        this.plugin.getLogger().warn("[PRE-LOGIN] Error processing {}, allowing connection to continue", (Object)username);
    }

    @Subscribe(order=PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        try {
            if (player == null) {
                this.plugin.getLogger().warn("[LOGIN] Received login event with null player");
                return;
            }
            UUID uuid = player.getUniqueId();
            String username = player.getUsername();
            this.plugin.debug("[LOGIN] {} logged in with UUID {}, online mode: {}", username, uuid, player.isOnlineMode());
            if (this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(player)) {
                boolean allowBedrock = this.plugin.getConfig().getBoolean("authentication.allow-bedrock-players", true);
                if (!allowBedrock) {
                    this.plugin.getLogger().info("[LOGIN] Blocking Bedrock player {} - Bedrock not supported", (Object)username);
                    event.setResult(ResultedEvent.ComponentResult.denied((Component)(this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("bedrock-not-supported") : Component.text((String)"Bedrock Edition is not supported on this server.", (TextColor)NamedTextColor.RED))));
                    return;
                }
                this.plugin.debug("[LOGIN] {} is a Bedrock player (allowed)", username);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[LOGIN] Error processing login event: {}", (Object)e.getMessage(), (Object)e);
        }
    }

    @Subscribe(order=PostOrder.LATE)
    public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            this.plugin.getLogger().warn("[LIMBO-REGISTER] Received event with null player");
            return;
        }
        UUID offlineUuid = player.getUniqueId();
        String username = player.getUsername();
        this.plugin.debug("[LIMBO-REGISTER] {} registering to limbo with UUID {}", username, offlineUuid);
        try {
            if (this.plugin.getAuthManager() != null) {
                this.plugin.getAuthManager().recordConnection(offlineUuid);
            }
            if (this.plugin.getLimboHandler() == null) {
                this.plugin.getLogger().error("[LIMBO-REGISTER] LimboHandler not initialized for {}", (Object)username);
                Component errorMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-auth-system-unavailable") : Component.text((String)"Authentication system unavailable. Please try again later.", (TextColor)NamedTextColor.RED);
                player.disconnect((Component)errorMsg);
                return;
            }
            boolean needsAuth = this.plugin.getLimboHandler().checkAuthImmediate(player);
            if (!needsAuth) {
                this.plugin.debug("[LIMBO-REGISTER] {} auto-authenticated, skipping limbo entirely", username);
                return;
            }
            this.plugin.getLimboHandler().markPendingLimbo(offlineUuid, username);
            this.plugin.debug("[LIMBO-REGISTER] {} needs auth, adding limbo callback", username);
            event.addOnJoinCallback(() -> {
                try {
                    this.plugin.debug("[JOIN-CALLBACK] Spawning {} to limbo for authentication", username);
                    this.plugin.getLimboHandler().authPlayer(player);
                }
                catch (Exception e) {
                    this.plugin.getLogger().error("[JOIN-CALLBACK] Error spawning {} to limbo: {}", username, e.getMessage(), e);
                    this.plugin.getLimboHandler().clearPendingLimbo(offlineUuid, username);
                    Component errorMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-auth-init-failed") : Component.text((String)"Failed to initialize authentication. Please try again.", (TextColor)NamedTextColor.RED);
                    player.disconnect((Component)errorMsg);
                }
            });
        }
        catch (PluginException e) {
            this.plugin.getLogger().error("[LIMBO-REGISTER] Plugin error for {}: {} ({})", username, e.getMessage(), e.getErrorCode().getCode());
            Component errorMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-auth-init-failed") : Component.text((String)"Authentication error. Please try again.", (TextColor)NamedTextColor.RED);
            player.disconnect((Component)errorMsg);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[LIMBO-REGISTER] Error processing {}: {}", username, e.getMessage(), e);
            Component errorMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-auth-system-unavailable") : Component.text((String)"Authentication system error. Please try again.", (TextColor)NamedTextColor.RED);
            player.disconnect((Component)errorMsg);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        this.plugin.debug("[DISCONNECT] {} ({}) disconnecting", username, uuid);
        try {
            try {
                this.premiumManager.removeVerificationSession(uuid);
                PremiumVerificationSession sessionByUsername = this.premiumManager.getVerificationSessionByUsername(username);
                if (sessionByUsername != null) {
                    this.premiumManager.removeVerificationSession(sessionByUsername.getUuid());
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[DISCONNECT] Error cleaning up premium session for {}: {}", (Object)username, (Object)e.getMessage());
            }
            if (this.plugin.getLimboHandler() != null) {
                try {
                    this.plugin.getLimboHandler().clearPendingLimbo(uuid, username);
                    this.plugin.getLimboHandler().cleanup(uuid);
                }
                catch (Exception e) {
                    this.plugin.getLogger().warn("[DISCONNECT] Error cleaning up limbo for {}: {}", (Object)username, (Object)e.getMessage());
                }
            }
            if (this.plugin.getAuthManager() != null) {
                try {
                    this.plugin.getAuthManager().cleanupOnDisconnect(uuid, username);
                    this.plugin.getAuthManager().cleanupUsername(username);
                }
                catch (Exception e) {
                    this.plugin.getLogger().warn("[DISCONNECT] Error cleaning up auth for {}: {}", (Object)username, (Object)e.getMessage());
                }
            }
            if (this.plugin.getAuthService() != null) {
                try {
                    this.plugin.getAuthService().clearMemorySession(uuid);
                }
                catch (Exception e) {
                    this.plugin.getLogger().warn("[DISCONNECT] Error clearing memory session for {}: {}", (Object)username, (Object)e.getMessage());
                }
            }
            this.plugin.debug("[DISCONNECT] Cleanup complete for {}", username);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[DISCONNECT] Error during cleanup for {}: {}", username, e.getMessage(), e);
        }
    }
}
