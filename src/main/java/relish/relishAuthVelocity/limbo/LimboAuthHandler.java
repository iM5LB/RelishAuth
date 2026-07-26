package relish.relishAuthVelocity.limbo;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualBlock;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboapi.api.player.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.auth.AuthService;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.exceptions.LimboException;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.limbo.MinimalLimboSessionHandler;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.premium.PremiumStatus;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.services.SkinCacheResolver;
import relish.relishAuthVelocity.utils.BackendServerResolver;
import relish.relishAuthVelocity.utils.MessageManager;
import relish.relishAuthVelocity.utils.ValidationUtil;
import relish.relishAuthVelocity.validators.PasswordValidator;
import relish.relishAuthVelocity.constants.AuthMethod;

public class LimboAuthHandler {
    private final RelishAuthVelocity plugin;
    private final AuthService authService;
    private final Config config;
    private final PasswordValidator passwordValidator;
    private Limbo authLimbo;
    private final Map<UUID, MinimalLimboSessionHandler> sessionHandlers = new ConcurrentHashMap<UUID, MinimalLimboSessionHandler>();
    private final Set<UUID> pendingLimboPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final Dimension AUTH_WORLD_DIMENSION = Dimension.THE_END;
    private static final double AUTH_WORLD_SPAWN_X = 0.0;
    private static final double AUTH_WORLD_SPAWN_Y = 64.0;
    private static final double AUTH_WORLD_SPAWN_Z = 0.0;

    public LimboAuthHandler(RelishAuthVelocity plugin, AuthService authService, Config config) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.authService = Objects.requireNonNull(authService, "AuthService cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.passwordValidator = new PasswordValidator(config);
        this.initializeLimbo();
    }

    private void initializeLimbo() {
        this.plugin.debug("[LIMBO] Starting limbo initialization...", new Object[0]);
        try {
            LimboFactory factory = this.getLimboFactory();
            if (factory == null) {
                this.plugin.getLogger().error("[LIMBO] LimboAPI not found! Please install LimboAPI plugin.");
                return;
            }
            VirtualWorld world = this.createVirtualWorld(factory);
            this.authLimbo = this.createLimbo(factory, world);
            this.initialized.set(true);
            this.plugin.debug("[LIMBO] Limbo initialized successfully", new Object[0]);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[LIMBO] Failed to initialize limbo: {}", (Object)e.getMessage(), (Object)e);
            this.initialized.set(false);
        }
    }

    private LimboFactory getLimboFactory() {
        try {
            return this.plugin.getServer().getPluginManager().getPlugin("limboapi")
                .flatMap(container -> container.getInstance())
                .filter(LimboFactory.class::isInstance)
                .map(LimboFactory.class::cast)
                .orElse(null);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[LIMBO] Error getting LimboAPI factory: {}", (Object)e.getMessage());
            return null;
        }
    }

    private VirtualWorld createVirtualWorld(LimboFactory factory) {
        this.plugin.debug("[LIMBO] Creating auth world (dimension: {}, spawn: {}, {}, {})", AUTH_WORLD_DIMENSION, 0.0, 64.0, 0.0);
        VirtualWorld world = factory.createVirtualWorld(AUTH_WORLD_DIMENSION, 0.0, 64.0, 0.0, 0.0f, 0.0f);
        this.buildSpawnPlatform(factory, world);
        return world;
    }

    private void buildSpawnPlatform(LimboFactory factory, VirtualWorld world) {
        try {
            VirtualBlock platformBlock = factory.createSimpleBlock("minecraft:barrier");
            int baseY = 63;
            int centerX = 0;
            int centerZ = 0;
            for (int x = centerX - 2; x <= centerX + 2; ++x) {
                for (int z = centerZ - 2; z <= centerZ + 2; ++z) {
                    world.setBlock(x, baseY, z, platformBlock);
                }
            }
            world.fillSkyLight(15);
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[LIMBO] Failed to build auth spawn platform: {}", (Object)e.getMessage());
        }
    }

    private Limbo createLimbo(LimboFactory factory, VirtualWorld world) {
        Limbo limbo = factory.createLimbo(world).setName("Auth").setGameMode(GameMode.SPECTATOR);
        // Advertise chooser commands to the limbo client so ClickEvent.runCommand works.
        // Actual handling is in MinimalLimboSessionHandler#onChat (LimboAPI does not run Velocity commands).
        try {
            limbo.registerCommand(new LimboCommandMeta(List.of("password")));
            limbo.registerCommand(new LimboCommandMeta(List.of("discord")));
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[LIMBO] Failed to register login chooser commands: {}", (Object)e.getMessage());
        }
        int readTimeout = this.config.getInt("security.limbo-read-timeout-ms", 120000);
        try {
            limbo.getClass().getMethod("setReadTimeout", Integer.TYPE).invoke((Object)limbo, readTimeout);
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[LIMBO] setReadTimeout not available in this LimboAPI version");
        }
        return limbo;
    }

    public boolean isInitialized() {
        return this.initialized.get() && this.authLimbo != null;
    }

    public boolean checkAuthImmediate(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        UUID sessionUuid = player.getUniqueId();
        UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, player.getUsername());
        InetAddress address = this.getPlayerAddress(player);
        if (this.plugin.getAuthManager().isAuthenticated(sessionUuid, player.getUsername())) {
            this.plugin.debug("[AUTH-DECISION] {} is already authenticated", player.getUsername());
            return false;
        }
        String authMethod = this.config.getString("authentication.method", "password");
        PremiumVerificationResult premiumResult = this.getPremiumResult(player);
        boolean isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(player);
        if (premiumResult == null) {
            if (isBedrock) {
                // PreLogin often caches under the unprefixed name; Floodgate then adds '.' and a new UUID.
                // Treat Bedrock as non-premium and continue so Discord/password sessions can still apply.
                this.plugin.debug("[AUTH-DECISION] Premium verification missing for Bedrock {}; treating as non-premium", player.getUsername());
            } else {
                this.plugin.getLogger().warn("[AUTH-DECISION] Account verification result is not available yet for {}; requiring authentication", (Object)player.getUsername());
                return true;
            }
        }
        boolean isPremium = this.isPremiumVerified(premiumResult);
        boolean isRegistered = this.authService.isRegistered(accountUuid);
        boolean autoLoginEnabled = this.config.getBoolean("authentication.premium-auto-login", true);
        this.plugin.debug("[AUTH-DECISION] {} -> method={}, premium={}, registered={}, autoLogin={}, bedrock={}", player.getUsername(), authMethod, isPremium, isRegistered, autoLoginEnabled, isBedrock);
        try {
            AuthMethod method = AuthMethod.fromString(authMethod);
            if (method == AuthMethod.DISCORD) {
                return this.checkDiscordAuth(player, sessionUuid, accountUuid, address, isPremium, isRegistered, autoLoginEnabled);
            }
            if (method == AuthMethod.HYBRID) {
                return this.checkHybridAuth(player, sessionUuid, accountUuid, address, isPremium, isRegistered, autoLoginEnabled);
            }
            return this.checkPasswordAuth(player, sessionUuid, accountUuid, address, isPremium, isRegistered, autoLoginEnabled);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-DECISION] Error checking auth for {}: {}", player.getUsername(), e.getMessage(), e);
            return true;
        }
    }

    private InetAddress getPlayerAddress(Player player) {
        try {
            return player.getRemoteAddress().getAddress();
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[AUTH] Could not get address for {}", (Object)player.getUsername());
            return null;
        }
    }

    private PremiumVerificationResult getPremiumResult(Player player) {
        return this.plugin.getAuthManager().getPremiumVerificationResultByUsername(player.getUsername());
    }

    private boolean isPremiumVerified(PremiumVerificationResult result) {
        if (result == null) {
            return false;
        }
        PremiumStatus status = result.getStatus();
        return status == PremiumStatus.PREMIUM_VERIFIED || status == PremiumStatus.PREMIUM_PENDING_ENCRYPTION;
    }

    private boolean checkDiscordAuth(Player player, UUID sessionUuid, UUID accountUuid, InetAddress address, boolean isPremium, boolean isRegistered, boolean autoLoginEnabled) {
        boolean isBedrock;
        boolean bl = isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(player);
        if (isPremium && isRegistered && autoLoginEnabled) {
            String discordId = this.authService.getDiscordId(accountUuid);
            if (ValidationUtil.isRealDiscordId(discordId)) {
                this.plugin.debug("[AUTH-DECISION] {} is premium with linked Discord; auto-authenticating", player.getUsername());
                this.authService.authenticateDiscord(accountUuid, address);
                this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
                if (this.plugin.getServerConnectHandler() != null) {
                    if (isBedrock) {
                        this.plugin.getServerConnectHandler().markBedrockAutoAuthReturning(sessionUuid);
                    } else {
                        this.plugin.getServerConnectHandler().markPremiumAutoAuth(sessionUuid);
                    }
                }
                return false;
            }
            this.plugin.debug("[AUTH-DECISION] {} is premium but has no linked Discord; authentication required", player.getUsername());
            return true;
        }
        if (isRegistered && this.authService.isAuthenticated(accountUuid, address)) {
            String discordId = this.authService.getDiscordId(accountUuid);
            if (ValidationUtil.isRealDiscordId(discordId)) {
                this.plugin.debug("[AUTH-DECISION] {} has a valid Discord session", player.getUsername());
                this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
                if (this.plugin.getServerConnectHandler() != null) {
                    this.plugin.getServerConnectHandler().markSessionValid(sessionUuid);
                }
                return false;
            }
            this.plugin.debug("[AUTH-DECISION] {} has no linked Discord; authentication required", player.getUsername());
            this.authService.removeSession(accountUuid);
            return true;
        }
        this.plugin.debug("[AUTH-DECISION] {} requires Discord authentication", player.getUsername());
        return true;
    }

    private boolean checkPasswordAuth(Player player, UUID sessionUuid, UUID accountUuid, InetAddress address, boolean isPremium, boolean isRegistered, boolean autoLoginEnabled) {
        boolean isBedrock;
        boolean bl = isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(player);
        if (isPremium && autoLoginEnabled) {
            if (!isRegistered) {
                this.plugin.debug("[AUTH-DECISION] {} is premium and not registered; creating premium account", player.getUsername());
                this.authService.registerPremium(accountUuid, player.getUsername(), address);
                this.plugin.debug("[AUTH-DECISION] {} premium first join; auto-authenticating", player.getUsername());
                this.authService.authenticatePremium(accountUuid, address);
                this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
                if (this.plugin.getServerConnectHandler() != null) {
                    if (isBedrock) {
                        this.plugin.getServerConnectHandler().markBedrockAutoAuthFirst(sessionUuid);
                    } else {
                        this.plugin.getServerConnectHandler().markPremiumAutoLogin(sessionUuid);
                    }
                }
                return false;
            }
            this.plugin.debug("[AUTH-DECISION] {} premium returning player; auto-authenticating", player.getUsername());
            this.authService.authenticatePremium(accountUuid, address);
            this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
            if (this.plugin.getServerConnectHandler() != null) {
                if (isBedrock) {
                    this.plugin.getServerConnectHandler().markBedrockAutoAuthReturning(sessionUuid);
                } else {
                    this.plugin.getServerConnectHandler().markPremiumAutoAuth(sessionUuid);
                }
            }
            return false;
        }
        if (!isPremium && isRegistered && this.authService.isAuthenticated(accountUuid, address)) {
            this.plugin.debug("[AUTH-DECISION] {} has a valid offline session", player.getUsername());
            this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
            if (this.plugin.getServerConnectHandler() != null) {
                this.plugin.getServerConnectHandler().markSessionValid(sessionUuid);
            }
            return false;
        }
        this.plugin.debug("[AUTH-DECISION] {} requires password authentication", player.getUsername());
        return true;
    }

    private boolean checkHybridAuth(Player player, UUID sessionUuid, UUID accountUuid, InetAddress address, boolean isPremium, boolean isRegistered, boolean autoLoginEnabled) {
        String discordId = this.authService.getDiscordId(accountUuid);
        boolean discordLinked = ValidationUtil.isRealDiscordId(discordId);
        boolean isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(player);

        if (isPremium && autoLoginEnabled) {
            if (!discordLinked) {
                this.plugin.debug("[AUTH-DECISION] {} is premium but hybrid mode requires Discord; authentication required", player.getUsername());
                return true;
            }
            if (!isRegistered) {
                this.plugin.debug("[AUTH-DECISION] {} is premium+Discord and not registered; creating premium account", player.getUsername());
                this.authService.registerPremium(accountUuid, player.getUsername(), address);
            }
            this.plugin.debug("[AUTH-DECISION] {} premium hybrid auto-authenticating with linked Discord", player.getUsername());
            this.authService.authenticatePremium(accountUuid, address);
            this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
            if (this.plugin.getServerConnectHandler() != null) {
                if (isBedrock) {
                    this.plugin.getServerConnectHandler().markBedrockAutoAuthReturning(sessionUuid);
                } else {
                    this.plugin.getServerConnectHandler().markPremiumAutoAuth(sessionUuid);
                }
            }
            return false;
        }

        if (isRegistered && this.authService.isAuthenticated(accountUuid, address) && discordLinked) {
            this.plugin.debug("[AUTH-DECISION] {} has valid hybrid session (password + Discord)", player.getUsername());
            this.plugin.getAuthManager().setAuthenticated(sessionUuid, player.getUsername(), true);
            if (this.plugin.getServerConnectHandler() != null) {
                this.plugin.getServerConnectHandler().markSessionValid(sessionUuid);
            }
            return false;
        }

        this.plugin.debug("[AUTH-DECISION] {} requires hybrid authentication (password + Discord)", player.getUsername());
        return true;
    }

    public void authPlayer(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        if (!this.isInitialized()) {
            this.plugin.getLogger().error("[AUTH] Limbo not initialized, cannot authenticate {}", (Object)player.getUsername());
            this.disconnectWithError(player, "error-auth-system-unavailable");
            return;
        }
        UUID sessionUuid = player.getUniqueId();
        UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, player.getUsername());
        this.clearPendingLimbo(sessionUuid, player.getUsername());
        String authMethod = this.config.getString("authentication.method", "password");
        boolean isRegistered = this.authService.isRegistered(accountUuid);
        boolean isPremium = this.isPremiumPlayer(player, sessionUuid);
        this.plugin.debug("[AUTH] {} to limbo (registered: {}, premium: {})", player.getUsername(), isRegistered, isPremium);
        try {
            AuthMethod method = AuthMethod.fromString(authMethod);
            if (method == AuthMethod.DISCORD) {
                this.handleDiscordAuth(player, sessionUuid, accountUuid, isRegistered, isPremium);
            } else if (method == AuthMethod.HYBRID && isPremium && this.config.getBoolean("authentication.premium-auto-login", true)
                    && !ValidationUtil.isRealDiscordId(this.authService.getDiscordId(accountUuid))) {
                // Premium hybrid players without Discord skip password and link Discord directly.
                this.handleDiscordAuth(player, sessionUuid, accountUuid, isRegistered, isPremium);
            } else {
                this.sendToLimbo(player, isRegistered, isPremium);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Error authenticating {}: {}", player.getUsername(), e.getMessage(), e);
            this.disconnectWithError(player, "error-auth-failed-generic");
        }
    }

    private void handleDiscordAuth(Player player, UUID sessionUuid, UUID accountUuid, boolean isRegistered, boolean isPremium) {
        if (this.plugin.getDiscordBot() == null || !this.plugin.getDiscordBot().isEnabled()) {
            this.disconnectWithError(player, "discord-bot-not-configured");
            return;
        }
        // Limbo session handler owns Discord prompts/verify DMs (avoids double-send + chat spam).
        this.sendToLimbo(player, isRegistered, isPremium);
    }

    private boolean isPremiumPlayer(Player player, UUID uuid) {
        PremiumVerificationResult premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(player.getUsername());
        if (premiumResult == null) {
            this.plugin.debug("[AUTH] Premium verification result missing for {}; treating as non-premium", player.getUsername());
            return false;
        }
        return this.isPremiumVerified(premiumResult);
    }

    private void sendToLimbo(Player player, boolean isRegistered, boolean isPremium) {
        if (this.authLimbo == null) {
            this.plugin.getLogger().error("[AUTH] authLimbo is NULL! Cannot send player.");
            this.disconnectWithError(player, "error-auth-system-offline");
            return;
        }
        UUID uuid = player.getUniqueId();
        this.clearPendingLimbo(uuid, player.getUsername());
        MinimalLimboSessionHandler.AuthenticationCallback callback = this.createAuthCallback(uuid);
        MinimalLimboSessionHandler sessionHandler = new MinimalLimboSessionHandler(this.plugin, uuid, callback, this.config, isRegistered);
        this.sessionHandlers.put(uuid, sessionHandler);
        try {
            this.authLimbo.spawnPlayer(player, (LimboSessionHandler)sessionHandler);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Exception during spawnPlayer for {}: {}", player.getUsername(), e.getMessage(), e);
            this.sessionHandlers.remove(uuid);
            this.disconnectWithError(player, "error-auth-init-failed");
        }
    }

    private MinimalLimboSessionHandler.AuthenticationCallback createAuthCallback(UUID uuid) {
        return new MinimalLimboSessionHandler.AuthenticationCallback(){

            @Override
            public void onAuthenticated(UUID playerUuid) {
            }

            @Override
            public void onDisconnect(UUID playerUuid) {
                LimboAuthHandler.this.cleanup(playerUuid);
            }

            @Override
            public boolean isRegistered(UUID playerUuid) {
                String username = LimboAuthHandler.this.plugin.getServer().getPlayer(playerUuid).map(Player::getUsername).orElse(null);
                UUID accountUuid = LimboAuthHandler.this.plugin.getAuthManager().resolveAccountUuid(playerUuid, username);
                return LimboAuthHandler.this.authService.isRegistered(accountUuid);
            }
        };
    }

    public void processLogin(Player player, String password) {
        Objects.requireNonNull(player, "Player cannot be null");
        UUID sessionUuid = player.getUniqueId();
        UUID uuid = this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, player.getUsername());
        InetAddress address = this.getPlayerAddress(player);
        if (address == null) {
            Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-verification-failed") : Component.text((String)"Could not verify your connection. Please reconnect.", (TextColor)NamedTextColor.RED);
            player.sendMessage((Component)msg);
            return;
        }
        try {
            AuthService.AuthResult result = this.authService.login(uuid, password, address);
            if (result.isSuccess()) {
                player.sendMessage(this.plugin.getMessageManager().getMessage("auth.login.success"));
                this.plugin.debug("[AUTH] Login completed for {}", player.getUsername());
                this.onAuthSuccess(player);
            } else {
                this.handleLoginFailure(player, uuid, result);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Error processing login for {}: {}", player.getUsername(), e.getMessage(), e);
            Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-login-failed") : Component.text((String)"Login failed. Please try again.", (TextColor)NamedTextColor.RED);
            player.sendMessage((Component)msg);
        }
    }

    private void handleLoginFailure(Player player, UUID uuid, AuthService.AuthResult result) {
        switch (result.getMessage()) {
            case "not_registered": {
                player.sendMessage(this.plugin.getMessageManager().getMessage("commands.login.not-registered"));
                break;
            }
            case "wrong_password": {
                player.sendMessage(this.plugin.getMessageManager().getMessage("auth.login.wrong-password", "{attempts}", String.valueOf(result.getAttemptsRemaining())));
                MinimalLimboSessionHandler handler = this.sessionHandlers.get(player.getUniqueId());
                if (handler != null) {
                    handler.repromptPasswordAuth();
                }
                break;
            }
            case "locked_out": {
                long minutes = result.getLockoutTime() / 60000L;
                player.sendMessage(this.plugin.getMessageManager().getMessage("auth.login.locked-out", "{minutes}", String.valueOf(minutes)));
                break;
            }
            default: {
                Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-login-failed") : Component.text((String)"Login failed. Please try again.", (TextColor)NamedTextColor.RED);
                player.sendMessage((Component)msg);
            }
        }
    }

    public void processRegister(Player player, String password, String confirm) {
        Objects.requireNonNull(player, "Player cannot be null");
        this.plugin.debug("[AUTH] processRegister called for {}", player.getUsername());
        UUID sessionUuid = player.getUniqueId();
        UUID uuid = this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, player.getUsername());
        InetAddress address = this.getPlayerAddress(player);
        if (address == null) {
            Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-verification-failed") : Component.text((String)"Could not verify your connection. Please reconnect.", (TextColor)NamedTextColor.RED);
            player.sendMessage((Component)msg);
            return;
        }
        try {
            if (this.authService.isRegistered(uuid)) {
                player.sendMessage(this.plugin.getMessageManager().getMessage("auth.register.already-registered"));
                return;
            }
            PasswordValidator.ValidationResult validationResult = this.passwordValidator.validate(password, confirm);
            if (!validationResult.isValid()) {
                MinimalLimboSessionHandler handler = this.sessionHandlers.get(sessionUuid);
                if (handler != null) {
                    handler.firstInput = null;
                    handler.authState = MinimalLimboSessionHandler.AuthState.WAITING;
                }
                Component validationMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("password-validation-failed", "{message}", validationResult.getMessage()) : Component.text((String)validationResult.getMessage(), (TextColor)NamedTextColor.RED);
                player.sendMessage((Component)validationMsg);
                if (handler != null) {
                    handler.repromptPasswordAuth();
                } else {
                    for (Component line : this.plugin.getMessageManager().getMessageList("auth.register.confirm", new String[0])) {
                        player.sendMessage(line);
                    }
                }
                return;
            }
            boolean isPremium = this.isPremiumPlayer(player, sessionUuid);
            this.authService.register(uuid, player.getUsername(), password, isPremium, address);
            this.authService.login(uuid, password, address);
            player.sendMessage(this.plugin.getMessageManager().getMessage("auth.register.success"));
            this.plugin.debug("[AUTH] Registration completed for {}", player.getUsername());
            this.onAuthSuccessFirstTime(player);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Error processing registration for {}: {}", player.getUsername(), e.getMessage(), e);
            Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-registration-failed") : Component.text((String)"Registration failed. Please try again.", (TextColor)NamedTextColor.RED);
            player.sendMessage((Component)msg);
        }
    }

    public void onAuthSuccess(Player player) {
        this.completeAuthSuccess(player, false, "login");
    }

    public void onAuthSuccessFirstTime(Player player) {
        this.completeAuthSuccess(player, true, "first join");
    }

    public void onAuthSuccessCleanup(Player player) {
        this.completeAuthSuccess(player, false, "cleanup");
    }

    private void completeAuthSuccess(Player player, boolean firstJoin, String context) {
        Objects.requireNonNull(player, "Player cannot be null");
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        this.plugin.debug("[AUTH-SUCCESS] {} completed authentication ({})", username, context);

        if (this.requiresHybridDiscordLink(player, uuid, username)) {
            this.beginHybridDiscordLink(player, uuid, username);
            return;
        }

        try {
            MinimalLimboSessionHandler handler;
            this.applySkinIfPresent(player, uuid, username);
            this.plugin.getAuthManager().setAuthenticated(uuid, username, true);
            if (this.plugin.getServerConnectHandler() != null) {
                if (firstJoin) {
                    this.plugin.getServerConnectHandler().markFirstJoin(uuid);
                } else {
                    this.plugin.getServerConnectHandler().markAuthSuccess(uuid);
                }
            }
            if ((handler = this.sessionHandlers.get(uuid)) != null && handler.getLimboPlayer() != null) {
                this.transferFromLimbo(handler, player);
            }
            this.cleanup(uuid);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-SUCCESS] Error finalizing auth for {}: {}", username, e.getMessage(), e);
        }
    }

    private boolean requiresHybridDiscordLink(Player player, UUID sessionUuid, String username) {
        if (AuthMethod.fromString(this.config.getString("authentication.method", "password")) != AuthMethod.HYBRID) {
            return false;
        }
        UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, username);
        return !ValidationUtil.isRealDiscordId(this.authService.getDiscordId(accountUuid));
    }

    private void beginHybridDiscordLink(Player player, UUID sessionUuid, String username) {
        this.plugin.debug("[AUTH-SUCCESS] {} password ok; hybrid mode requires Discord link", username);
        if (this.plugin.getDiscordBot() == null || !this.plugin.getDiscordBot().isEnabled()) {
            this.disconnectWithError(player, "discord-bot-not-configured");
            return;
        }
        MinimalLimboSessionHandler handler = this.sessionHandlers.get(sessionUuid);
        if (handler == null || handler.getLimboPlayer() == null) {
            this.plugin.getLogger().warn("[AUTH] Hybrid Discord link requested but {} is not in limbo", (Object)username);
            this.disconnectWithError(player, "error-auth-failed-generic");
            return;
        }
        handler.enableDiscordLinkPhase();
        player.sendMessage(this.plugin.getMessageManager().getMessage("hybrid-discord-required"));
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        for (String line : this.plugin.getMessageManager().getRawMessageList("discord-auth-required")) {
            player.sendMessage(MessageManager.parseColors(line.replace("{server}", serverName)));
        }
    }

    private void applySkinIfPresent(Player player, UUID uuid, String username) {
        try {
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(uuid, username);
            User user = this.authService.getUser(accountUuid);
            if (user != null && this.plugin.getSkinApplier() != null) {
                this.plugin.getSkinApplier().applySkinToPlayer(player, user);
            }
        }
        catch (Exception e) {
            this.plugin.debug("[SKIN] Failed to apply skin for {}: {}", username, e.getMessage());
        }
    }

    public boolean isInLimbo(UUID uuid) {
        return this.sessionHandlers.containsKey(uuid);
    }

    private void transferFromLimbo(MinimalLimboSessionHandler handler, Player player) {
        Optional backend = Optional.empty();
        try {
            String initial;
            if (this.plugin.getInitialServerEventHandler() != null && (initial = this.plugin.getInitialServerEventHandler().consumeInitialServer(player.getUniqueId())) != null && !initial.isBlank() && (backend = this.plugin.getServer().getServer(initial)).isPresent()) {
                this.plugin.debug("[FORCED-HOST] Using initial server for {}: {}", player.getUsername(), initial);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (backend.isEmpty()) {
            backend = BackendServerResolver.resolvePostAuthServer(this.plugin.getServer(), this.plugin.getConfig());
        }
        if (backend.isPresent()) {
            this.plugin.debug("[AUTH-SUCCESS] Transferring {} from limbo to {}", player.getUsername(), ((RegisteredServer)backend.get()).getServerInfo().getName());
            handler.getLimboPlayer().disconnect((RegisteredServer)backend.get());
            return;
        }
        this.plugin.debug("[AUTH-SUCCESS] No backend resolved for {}, using default limbo disconnect", player.getUsername());
        handler.getLimboPlayer().disconnect();
    }

    public void markPendingLimbo(UUID uuid, String username) {
        this.pendingLimboPlayers.add(uuid);
        if (username == null || username.isBlank()) {
            return;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        this.pendingLimboPlayers.add(offlineUuid);
        PremiumVerificationResult premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username);
        if (premiumResult != null && premiumResult.getMojangUuid() != null) {
            this.pendingLimboPlayers.add(premiumResult.getMojangUuid());
        }
    }

    public void clearPendingLimbo(UUID uuid, String username) {
        this.pendingLimboPlayers.remove(uuid);
        if (username == null || username.isBlank()) {
            return;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        this.pendingLimboPlayers.remove(offlineUuid);
        PremiumVerificationResult premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username);
        if (premiumResult != null && premiumResult.getMojangUuid() != null) {
            this.pendingLimboPlayers.remove(premiumResult.getMojangUuid());
        }
    }

    public boolean isPendingLimbo(UUID uuid, String username) {
        if (this.pendingLimboPlayers.contains(uuid)) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        if (this.pendingLimboPlayers.contains(offlineUuid)) {
            return true;
        }
        PremiumVerificationResult premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username);
        return premiumResult != null && premiumResult.getMojangUuid() != null && this.pendingLimboPlayers.contains(premiumResult.getMojangUuid());
    }

    public void cleanup(UUID uuid) {
        if (uuid == null) {
            return;
        }
        this.sessionHandlers.remove(uuid);
        this.pendingLimboPlayers.remove(uuid);
    }

    public void completeDiscordAuth(UUID uuid, String discordId) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(discordId, "Discord ID cannot be null");
        this.plugin.debug("[AUTH] Completing Discord auth for session UUID: {}", uuid);
        MinimalLimboSessionHandler handler = this.sessionHandlers.get(uuid);
        if (handler == null) {
            this.plugin.getLogger().warn("[AUTH] No session handler found for UUID: {} - player may have disconnected", (Object)uuid);
            throw new LimboException(PluginException.ErrorCode.LIMBO_SESSION_ERROR, "Player session not found", uuid);
        }
        if (handler.getLimboPlayer() == null) {
            this.plugin.getLogger().warn("[AUTH] No limbo player in session handler for UUID: {}", (Object)uuid);
            throw new LimboException(PluginException.ErrorCode.LIMBO_SESSION_ERROR, "Player not in limbo", uuid);
        }
        Player player = handler.getLimboPlayer().getProxyPlayer();
        if (player == null) {
            this.plugin.getLogger().warn("[AUTH] No proxy player found in session handler");
            throw new LimboException(PluginException.ErrorCode.LIMBO_SESSION_ERROR, "Proxy player not found", uuid);
        }
        this.plugin.debug("[AUTH] Player {} found in limbo, processing Discord auth", player.getUsername());
        try {
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(uuid, player.getUsername());
            boolean isRegistered = this.authService.isRegistered(accountUuid);
            boolean isPremium = this.isPremiumPlayer(player, uuid);
            this.plugin.debug("[AUTH] Is registered: {}, isPremium: {}", isRegistered, isPremium);
            if (isRegistered) {
                User user;
                boolean linked = this.authService.linkDiscordToUser(accountUuid, discordId);
                if (!linked) {
                    Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-discord-already-linked") : Component.text((String)"This Discord account is already linked to another player.", (TextColor)NamedTextColor.RED);
                    player.disconnect((Component)msg);
                    this.plugin.getLogger().warn("[SECURITY] Rejected Discord linking - Discord ID {} already in use", (Object)discordId);
                    return;
                }
                if (isPremium && (user = this.authService.getUser(accountUuid)) != null && !"PREMIUM".equals(user.getAccountType())) {
                    user.setAccountType("PREMIUM");
                    this.plugin.debug("[AUTH] Updated account type to PREMIUM for {}", player.getUsername());
                }
            } else {
                boolean registered = this.authService.registerDiscordUser(accountUuid, player.getUsername(), discordId, player.getRemoteAddress().getAddress(), isPremium);
                if (!registered) {
                    Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("error-discord-already-linked") : Component.text((String)"This Discord account is already linked to another player.", (TextColor)NamedTextColor.RED);
                    player.disconnect((Component)msg);
                    this.plugin.getLogger().warn("[SECURITY] Rejected Discord registration - Discord ID {} already in use", (Object)discordId);
                    return;
                }
                this.authService.authenticateDiscord(accountUuid, player.getRemoteAddress().getAddress());
                player.sendMessage((Component)(this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage("auth-success") : Component.text((String)"Authenticated successfully!", (TextColor)NamedTextColor.GREEN)));
                this.plugin.debug("[AUTH] Discord registration completed for {}", player.getUsername());
                this.plugin.getAuthManager().setAuthenticated(uuid, player.getUsername(), true);
                if (this.plugin.getServerConnectHandler() != null) {
                    this.plugin.getServerConnectHandler().markFirstJoin(uuid);
                }
                this.onAuthSuccessCleanup(player);
                return;
            }
            this.authService.authenticateDiscord(accountUuid, player.getRemoteAddress().getAddress());
            this.plugin.getAuthManager().setAuthenticated(uuid, player.getUsername(), true);
            if (this.plugin.getServerConnectHandler() != null) {
                this.plugin.getServerConnectHandler().markAuthSuccess(uuid);
            }
            this.onAuthSuccess(player);
            String sessionDuration = this.config.getString("session.duration", "1h");
            if (this.plugin.getDiscordBot() != null) {
                this.plugin.getDiscordBot().sendSessionControl(discordId, player.getUsername(), uuid, player.getRemoteAddress().getAddress().getHostAddress(), sessionDuration);
            }
            this.plugin.debug("[AUTH] Discord auth completed successfully for {}", player.getUsername());
        }
        catch (LimboException e) {
            throw e;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Failed to complete Discord auth: {}", (Object)e.getMessage(), (Object)e);
            this.disconnectWithError(player, "error-auth-failed-generic");
            throw new LimboException(PluginException.ErrorCode.LIMBO_SESSION_ERROR, "Discord auth failed", uuid, (Throwable)e);
        }
    }

    private void disconnectWithError(Player player, String messageKey) {
        try {
            Component msg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessage(messageKey) : Component.text((String)messageKey, (TextColor)NamedTextColor.RED);
            player.disconnect((Component)msg);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("Failed to disconnect player: {}", (Object)e.getMessage());
        }
    }

    public AuthService getAuthService() {
        return this.authService;
    }
}
