package relish.relishAuthVelocity.handlers;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.util.GameProfile;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.limbo.LimboAuthHandler;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.services.SkinCacheResolver;
import relish.relishAuthVelocity.services.SkinFetchService;
import relish.relishAuthVelocity.utils.MessageManager;
import relish.relishAuthVelocity.services.SkinPayloadUtil;

public class ServerConnectEventHandler {
    private final RelishAuthVelocity plugin;
    private final LimboAuthHandler limboHandler;
    private final SkinCacheResolver skinCacheResolver;
    private volatile Method setGameProfilePropertiesMethod;
    private volatile boolean setGameProfilePropertiesMethodChecked;
    private final Set<UUID> pendingAuthSuccess = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingFirstJoin = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingAutoAuth = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSessionValid = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPremiumAutoAuth = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPremiumAutoLogin = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingBedrockAutoAuthFirst = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingBedrockAutoAuthReturning = ConcurrentHashMap.newKeySet();

    public ServerConnectEventHandler(RelishAuthVelocity plugin, LimboAuthHandler limboHandler) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.limboHandler = Objects.requireNonNull(limboHandler, "LimboHandler cannot be null");
        this.skinCacheResolver = new SkinCacheResolver(plugin, plugin.getLogger());
    }

    @Subscribe(order=PostOrder.LAST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        try {
            boolean isBackend;
            String targetServer = event.getOriginalServer().getServerInfo().getName();
            if (this.limboHandler.isInLimbo(uuid) || this.limboHandler.isPendingLimbo(uuid, username)) {
                this.plugin.debug("[SERVER-CONNECT] Denying {} connection to {} - still in limbo", username, targetServer);
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-auth-complete") : Component.text((String)"Please complete authentication first.", (TextColor)NamedTextColor.YELLOW);
                player.sendMessage((Component)msg);
                return;
            }
            boolean bl = isBackend = !targetServer.equalsIgnoreCase("limbo") && !targetServer.equalsIgnoreCase("auth");
            if (!isBackend) {
                return;
            }
            this.tryApplySkinToPlayerProfile(player);
            this.plugin.debug("[SERVER-CONNECT] {} connecting to backend {}", username, targetServer);
            this.limboHandler.cleanup(uuid);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[SERVER-CONNECT] Error processing pre-connect for {}: {}", username, e.getMessage(), e);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        try {
            boolean isBackend;
            if (player.getCurrentServer().isEmpty()) {
                return;
            }
            String serverName = ((ServerConnection)player.getCurrentServer().get()).getServerInfo().getName();
            boolean bl = isBackend = !serverName.equalsIgnoreCase("limbo") && !serverName.equalsIgnoreCase("auth");
            if (isBackend && this.plugin.getAuthManager().isAuthenticated(uuid, player.getUsername())) {
                boolean justJoinedBackend = false;
                if (this.pendingFirstJoin.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending first join to {} on {}", player.getUsername(), serverName);
                    this.sendFirstJoinEffects(player);
                    this.sendJoinNotification(player);
                    justJoinedBackend = true;
                } else if (this.pendingAuthSuccess.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending auth success to {} on {}", player.getUsername(), serverName);
                    this.sendAuthSuccessEffects(player);
                    this.sendJoinNotification(player);
                    justJoinedBackend = true;
                } else if (this.pendingAutoAuth.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending auto-auth success to {} on {}", player.getUsername(), serverName);
                    this.sendAutoAuthMessage(player);
                    this.sendJoinNotification(player);
                } else if (this.pendingSessionValid.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending session valid to {} on {}", player.getUsername(), serverName);
                    this.sendSessionValidMessage(player);
                    this.sendJoinNotification(player);
                } else if (this.pendingPremiumAutoAuth.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending premium auto-auth to {} on {}", player.getUsername(), serverName);
                    this.sendPremiumAutoAuthMessage(player);
                    this.sendJoinNotification(player);
                } else if (this.pendingPremiumAutoLogin.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending premium auto-login to {} on {}", player.getUsername(), serverName);
                    this.sendPremiumAutoLoginMessage(player);
                    this.sendJoinNotification(player);
                } else if (this.pendingBedrockAutoAuthFirst.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending bedrock auto-auth first to {} on {}", player.getUsername(), serverName);
                    this.sendBedrockAutoAuthFirstMessage(player);
                    this.sendJoinNotification(player);
                    justJoinedBackend = true;
                } else if (this.pendingBedrockAutoAuthReturning.remove(uuid)) {
                    this.plugin.debug("[SERVER-POST-CONNECT] Sending bedrock auto-auth returning to {} on {}", player.getUsername(), serverName);
                    this.sendBedrockAutoAuthReturningMessage(player);
                    this.sendJoinNotification(player);
                }
                if (justJoinedBackend) {
                    this.maybeSendSetPasswordTip(player);
                }
                if (this.plugin.getGroupSyncService() != null) {
                    this.plugin.getGroupSyncService().syncPlayer(player, "backend connect");
                }
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[SERVER-POST-CONNECT] Error processing post-connect for {}: {}", player.getUsername(), e.getMessage(), e);
        }
    }

    public void markAuthSuccess(UUID uuid) {
        if (uuid != null) {
            this.pendingAuthSuccess.add(uuid);
        }
    }

    public void markFirstJoin(UUID uuid) {
        if (uuid != null) {
            this.pendingFirstJoin.add(uuid);
        }
    }

    public void markAutoAuth(UUID uuid) {
        if (uuid != null) {
            this.pendingAutoAuth.add(uuid);
        }
    }

    public void markSessionValid(UUID uuid) {
        if (uuid != null) {
            this.pendingSessionValid.add(uuid);
        }
    }

    public void markPremiumAutoAuth(UUID uuid) {
        if (uuid != null) {
            this.pendingPremiumAutoAuth.add(uuid);
        }
    }

    public void markPremiumAutoLogin(UUID uuid) {
        if (uuid != null) {
            this.pendingPremiumAutoLogin.add(uuid);
        }
    }

    public void markBedrockAutoAuthFirst(UUID uuid) {
        if (uuid != null) {
            this.pendingBedrockAutoAuthFirst.add(uuid);
        }
    }

    public void markBedrockAutoAuthReturning(UUID uuid) {
        if (uuid != null) {
            this.pendingBedrockAutoAuthReturning.add(uuid);
        }
    }

    private void sendAutoAuthMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("auto-auth-success"));
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending auto-auth message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendSessionValidMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("session-valid"));
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending session valid message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendPremiumAutoAuthMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("premium-auto-auth"));
                    if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                        this.sendWelcomeTitle(player);
                    }
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending premium auto-auth message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendPremiumAutoLoginMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("premium-auto-login"));
                    if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                        this.sendWelcomeTitle(player);
                    }
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending premium auto-login message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendBedrockAutoAuthFirstMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
                    player.sendMessage(this.plugin.getMessageManager().getMessage("bedrock-auto-auth-first", "{server}", serverName));
                    if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                        this.sendWelcomeTitle(player);
                    }
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending bedrock auto-auth first message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendBedrockAutoAuthReturningMessage(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("bedrock-auto-auth-returning"));
                    if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                        this.sendWelcomeTitle(player);
                    }
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending bedrock auto-auth returning message to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendFirstJoinEffects(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
                    player.sendMessage(this.plugin.getMessageManager().getMessage("first-join", "{player}", player.getUsername(), "{server}", serverName));
                }
                if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                    this.sendWelcomeTitle(player);
                }
                this.spawnSuccessParticles(player);
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending first join effects to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    /**
     * Chat tip (like Discord join-alert) when the account has no password yet.
     */
    private void maybeSendSetPasswordTip(Player player) {
        if (!this.plugin.getConfig().getBoolean("authentication.set-password-tip-on-join", true)) {
            return;
        }
        if (this.plugin.getAuthService() == null || this.plugin.getMessageManager() == null) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object) this.plugin, () -> {
            try {
                UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(player.getUniqueId(), player.getUsername());
                if (this.plugin.getAuthService().hasPassword(accountUuid)) {
                    return;
                }
                for (String line : this.plugin.getMessageManager().getRawMessageList("auth.set-password-tip")) {
                    player.sendMessage(MessageManager.parseColors(line));
                }
            } catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending set-password tip to {}: {}", player.getUsername(), e.getMessage());
            }
        }).delay(1500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendAuthSuccessEffects(Player player) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                if (this.plugin.getMessageManager() != null) {
                    player.sendMessage(this.plugin.getMessageManager().getMessage("authenticated", "{player}", player.getUsername()));
                }
                if (this.plugin.getConfig().getBoolean("welcome.enabled", true)) {
                    this.sendWelcomeTitle(player);
                }
                this.spawnSuccessParticles(player);
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[SERVER-POST-CONNECT] Error sending auth success effects to {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
            }
        }).delay(500L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendWelcomeTitle(Player player) {
        try {
            String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
            Component title = this.plugin.getMessageManager().getMessage("welcome.title", "{player}", player.getUsername());
            Component subtitle = this.plugin.getMessageManager().getMessage("welcome.subtitle", "{server}", serverName);
            int duration = this.plugin.getConfig().getInt("welcome.duration", 3);
            Title welcomeTitle = Title.title((Component)title, (Component)subtitle, (Title.Times)Title.Times.times((Duration)Duration.ofMillis(500L), (Duration)Duration.ofSeconds(duration), (Duration)Duration.ofMillis(500L)));
            player.showTitle(welcomeTitle);
            this.plugin.debug("[WELCOME] Sent welcome title to {}", player.getUsername());
        }
        catch (Exception e) {
            this.plugin.debug("[WELCOME] Could not send welcome title: {}", e.getMessage());
        }
    }

    private void spawnSuccessParticles(Player player) {
        if (player.getCurrentServer().isEmpty()) {
            return;
        }
        try {
            MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from((String)"relish:auth_success");
            ((ServerConnection)player.getCurrentServer().get()).sendPluginMessage((ChannelIdentifier)channel, new byte[]{1});
            this.plugin.debug("[SERVER-POST-CONNECT] Sent particle message to backend for {}", player.getUsername());
        }
        catch (Exception e) {
            this.plugin.debug("[SERVER-POST-CONNECT] Could not send particle message to backend: {}", e.getMessage());
        }
    }

    private void sendJoinNotification(Player player) {
        if (this.plugin.getDiscordBot() == null || !this.plugin.getDiscordBot().isEnabled()) {
            return;
        }
        try {
            User user = this.plugin.getAuthService().getUser(player.getUniqueId());
            if (user == null || user.getDiscordId() == null || !user.isJoinNotifications()) {
                return;
            }
            this.plugin.getDiscordBot().sendJoinNotification(user.getDiscordId(), player.getUsername(), player.getUniqueId());
            this.plugin.debug("[JOIN-NOTIFY] Sent join notification for {}", player.getUsername());
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[JOIN-NOTIFY] Error sending join notification for {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
        }
    }

    private void tryApplySkinToPlayerProfile(Player player) {
        boolean isBedrock;
        if (player == null || this.plugin.getConfig() == null || !this.plugin.getConfig().getBoolean("skins.enabled", true)) {
            return;
        }
        boolean preserveExistingTextures = this.plugin.getConfig().getBoolean("skins.preserve-existing-textures", true);
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        boolean bl = isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(uuid);
        if (isBedrock) {
            return;
        }
        SkinFetchService.SkinData skinData = this.skinCacheResolver.resolve(uuid, username, "[SKIN-PRECONNECT]", false);
        if (skinData == null || skinData.textureData == null || skinData.textureData.isBlank()) {
            return;
        }
        skinData = this.skinCacheResolver.normalizeUnsignedSkinData(skinData, uuid, username);
        String signature = skinData.signature == null ? "" : skinData.signature;
        try {
            boolean hasTexturesProperty;
            GameProfile currentProfile = player.getGameProfile();
            if (currentProfile == null) {
                return;
            }
            if (preserveExistingTextures && (hasTexturesProperty = currentProfile.getProperties().stream().anyMatch(prop -> prop != null && "textures".equalsIgnoreCase(prop.getName()) && prop.getValue() != null && !prop.getValue().isBlank()))) {
                return;
            }
            ArrayList<GameProfile.Property> properties = new ArrayList<GameProfile.Property>(currentProfile.getProperties());
            properties.removeIf(prop -> "textures".equalsIgnoreCase(prop.getName()));
            properties.add(new GameProfile.Property("textures", skinData.textureData, signature));
            boolean applied = this.invokeSetGameProfileProperties(player, properties);
            if (applied) {
                String skinUrl = SkinPayloadUtil.tryExtractSkinUrl(skinData.textureData);
                if (skinUrl != null && !skinUrl.isBlank()) {
                    this.plugin.debug("[SKIN-PRECONNECT] Applied skin for {} (uuid: {}) (source: {}) (skinUrl: {})", username, uuid, skinData.source, skinUrl);
                } else {
                    this.plugin.debug("[SKIN-PRECONNECT] Applied skin for {} (uuid: {}) (source: {})", username, uuid, skinData.source);
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private boolean invokeSetGameProfileProperties(Player player, List<GameProfile.Property> properties) {
        try {
            Method method = this.setGameProfilePropertiesMethod;
            if (method == null && !this.setGameProfilePropertiesMethodChecked) {
                try {
                    this.setGameProfilePropertiesMethod = method = player.getClass().getMethod("setGameProfileProperties", List.class);
                }
                catch (NoSuchMethodException noSuchMethodException) {
                }
                finally {
                    this.setGameProfilePropertiesMethodChecked = true;
                }
            }
            if (method == null) {
                return false;
            }
            method.invoke((Object)player, properties);
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }
}
