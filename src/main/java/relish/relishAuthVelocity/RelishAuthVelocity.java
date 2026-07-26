package relish.relishAuthVelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import relish.relishAuthVelocity.auth.AuthService;
import relish.relishAuthVelocity.auth.AuthenticationManager;
import relish.relishAuthVelocity.commands.LogoutCommand;
import relish.relishAuthVelocity.commands.RaCommand;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.database.AuthDatabase;
import relish.relishAuthVelocity.exceptions.ConfigurationException;
import relish.relishAuthVelocity.exceptions.DatabaseException;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.handlers.ConnectionEventHandler;
import relish.relishAuthVelocity.handlers.InitialServerEventHandler;
import relish.relishAuthVelocity.handlers.PostLoginHandler;
import relish.relishAuthVelocity.handlers.ServerConnectEventHandler;
import relish.relishAuthVelocity.handlers.SkinProfileEventHandler;
import relish.relishAuthVelocity.integrations.DiscordIntegration;
import relish.relishAuthVelocity.integrations.DiscordIntegrationLoader;
import relish.relishAuthVelocity.integrations.NoopDiscordIntegration;
import relish.relishAuthVelocity.limbo.LimboAuthHandler;
import relish.relishAuthVelocity.premium.PremiumVerificationManager;
import relish.relishAuthVelocity.services.GroupSyncService;
import relish.relishAuthVelocity.services.PremiumVerificationService;
import relish.relishAuthVelocity.services.SkinApplier;
import relish.relishAuthVelocity.updater.UpdateManager;
import relish.relishAuthVelocity.utils.ConnectionUtil;
import relish.relishAuthVelocity.utils.FloodgateHelper;
import relish.relishAuthVelocity.utils.MessageManager;
import relish.relishAuthVelocity.BuildConstants;

@Plugin(id="relishauth", name="RelishAuth", version=BuildConstants.VERSION, dependencies={@Dependency(id="limboapi", optional=false), @Dependency(id="luckperms", optional=true)})
public class RelishAuthVelocity {
    @Inject
    private ProxyServer server;
    @Inject
    private Logger logger;
    @Inject
    @DataDirectory
    private Path dataDirectory;
    private Config config;
    private AuthDatabase database;
    private AuthService authService;
    private LimboAuthHandler limboHandler;
    private PremiumVerificationManager premiumManager;
    private AuthenticationManager authManager;
    private PremiumVerificationService premiumVerificationService;
    private MessageManager messageManager;
    private FloodgateHelper floodgateHelper;
    private DiscordIntegration discordBot = new NoopDiscordIntegration();
    private ServerConnectEventHandler serverConnectHandler;
    private InitialServerEventHandler initialServerEventHandler;
    private UpdateManager updateManager;
    private SkinApplier skinApplier;
    private GroupSyncService groupSyncService;
    private boolean debugEnabled = false;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private InitializationState initState = InitializationState.NOT_STARTED;
    private long startTime = 0L;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.startTime = System.currentTimeMillis();
        this.printStartupHeader();
        try {
            this.initializeConfig();
            this.initializeUpdater();
            this.initializeDatabase();
            this.initializeServices();
            this.initializeDiscord();
            this.registerEventHandlers();
            this.registerCommands();
            this.initState = InitializationState.FULLY_INITIALIZED;
            this.initialized.set(true);
            this.printStartupSuccess();
            this.logConfigurationSummary();
        }
        catch (ConfigurationException e) {
            this.handleInitializationFailure("Configuration", e);
        }
        catch (DatabaseException e) {
            this.handleInitializationFailure("Database", e);
        }
        catch (PluginException e) {
            this.handleInitializationFailure("Plugin", e);
        }
        catch (Exception e) {
            this.handleInitializationFailure("Unexpected", e);
        }
    }

    private void initializeUpdater() {
        this.debug("[UPDATE] Running config/language updater", new Object[0]);
        try {
            this.updateManager = new UpdateManager(this.logger, this.dataDirectory);
            boolean updated = this.updateManager.updateConfigurationFiles();
            if (updated && this.config != null) {
                this.config.reload();
            }
            if (this.config != null) {
                String lang = this.config.getString("language", "en");
                this.updateManager.updateLanguageFiles(lang);
            }
            if (this.config != null && this.isUpdateCheckEnabled()) {
                this.updateManager.checkForPluginUpdates();
            }
            this.debug("[UPDATE] Updater finished", new Object[0]);
        }
        catch (Exception e) {
            this.logger.warn("[UPDATE] Failed to run updater: {}", (Object)e.getMessage());
        }
    }

    private void initializeConfig() throws ConfigurationException {
        this.debug("Loading configuration...", new Object[0]);
        this.config = new Config(this.dataDirectory);
        this.debugEnabled = this.config.getBoolean("debug", false);
        if (this.config.hasValidationErrors()) {
            this.logger.warn("[CONFIG] Validation warnings detected:");
            for (String error : this.config.getValidationErrors()) {
                this.logger.warn("[CONFIG] - {}", (Object)error);
            }
        }
        this.initState = InitializationState.CONFIG_LOADED;
        this.debug("Configuration loaded successfully", new Object[0]);
    }

    private void initializeDatabase() throws DatabaseException {
        this.debug("Initializing database connection...", new Object[0]);
        try {
            this.database = new AuthDatabase(this.config, this.dataDirectory);
            this.initState = InitializationState.DATABASE_CONNECTED;
            this.debug("Database connection established", new Object[0]);
        }
        catch (Exception e) {
            throw new DatabaseException(PluginException.ErrorCode.DB_CONNECTION_FAILED, "Failed to initialize database: " + e.getMessage(), e);
        }
    }

    private void initializeServices() {
        this.debug("Initializing services...", new Object[0]);
        try {
            this.messageManager = new MessageManager(this.dataDirectory, this.config);
        }
        catch (Exception e) {
            this.logger.warn("[INIT] Failed to initialize MessageManager, using defaults: {}", (Object)e.getMessage());
        }
        try {
            this.floodgateHelper = new FloodgateHelper(this.server, this.logger, this.config);
        }
        catch (Exception e) {
            this.logger.warn("[INIT] Failed to initialize FloodgateHelper: {}", (Object)e.getMessage());
        }
        this.authService = new AuthService(this, this.database, this.config);
        this.premiumManager = new PremiumVerificationManager(this);
        this.authManager = new AuthenticationManager(this);
        try {
            this.limboHandler = new LimboAuthHandler(this, this.authService, this.config);
        }
        catch (Exception e) {
            this.logger.error("[INIT] Failed to initialize LimboAuthHandler: {}", (Object)e.getMessage());
            throw new PluginException(PluginException.ErrorCode.LIMBO_INIT_FAILED, "LimboAPI initialization failed", e);
        }
        this.premiumVerificationService = new PremiumVerificationService(this, this.config, this.premiumManager);
        this.skinApplier = new SkinApplier(this, this.logger);
        this.initState = InitializationState.SERVICES_INITIALIZED;
        this.debug("Services initialized successfully", new Object[0]);
    }

    private void initializeDiscord() {
        this.debug("Initializing Discord bot...", new Object[0]);
        try {
            this.discordBot = DiscordIntegrationLoader.load(this, this.config, this.logger);
            boolean discordEnabled = this.discordBot != null && this.discordBot.initialize();
            this.initializeGroupSyncService();
            if (discordEnabled) {
                this.debug("Discord bot initialized and connected", new Object[0]);
            } else {
                this.debug("Discord bot disabled or failed to connect", new Object[0]);
            }
        }
        catch (Exception e) {
            boolean required;
            String method = this.config != null ? this.config.getString("authentication.method", "password") : "password";
            boolean bl = required = "discord".equalsIgnoreCase(method) || "hybrid".equalsIgnoreCase(method);
            if (required) {
                throw new PluginException(PluginException.ErrorCode.INITIALIZATION_FAILED, "Discord authentication is enabled but Discord failed to initialize: " + e.getMessage(), e);
            }
            this.logger.warn("[INIT] Failed to initialize Discord bot (disabled): {}", (Object)e.getMessage());
            this.discordBot = new NoopDiscordIntegration();
            this.initializeGroupSyncService();
        }
    }

    private void initializeGroupSyncService() {
        if (this.config == null || !this.config.getBoolean("group-sync.enabled", false)) {
            this.groupSyncService = null;
            return;
        }
        if (this.discordBot == null || !this.discordBot.isEnabled()) {
            this.groupSyncService = null;
            this.logger.warn("[GROUP-SYNC] Enabled in config but Discord bot is not connected; group sync disabled until Discord is available");
            return;
        }
        try {
            this.groupSyncService = new GroupSyncService(this, this.discordBot);
            this.logger.info("[GROUP-SYNC] Discord role to LuckPerms group sync enabled");
        }
        catch (NoClassDefFoundError e) {
            this.groupSyncService = null;
            this.logger.warn("[GROUP-SYNC] Disabled because LuckPerms API is not available. Install LuckPerms on the proxy to use group sync.");
        }
        catch (Exception e) {
            this.groupSyncService = null;
            this.logger.warn("[GROUP-SYNC] Failed to initialize group sync: {}", (Object)e.getMessage());
        }
    }

    private void registerEventHandlers() {
        this.debug("Registering event handlers...", new Object[0]);
        try {
            ConnectionEventHandler connectionHandler = new ConnectionEventHandler(this, this.premiumVerificationService, this.premiumManager);
            this.serverConnectHandler = new ServerConnectEventHandler(this, this.limboHandler);
            this.initialServerEventHandler = new InitialServerEventHandler(this);
            PostLoginHandler postLoginHandler = new PostLoginHandler(this);
            SkinProfileEventHandler skinProfileHandler = new SkinProfileEventHandler(this);
            this.server.getEventManager().register((Object)this, (Object)connectionHandler);
            this.server.getEventManager().register((Object)this, (Object)this.serverConnectHandler);
            this.server.getEventManager().register((Object)this, (Object)this.initialServerEventHandler);
            this.server.getEventManager().register((Object)this, (Object)postLoginHandler);
            this.server.getEventManager().register((Object)this, (Object)skinProfileHandler);
            this.initState = InitializationState.HANDLERS_REGISTERED;
            this.debug("Event handlers registered successfully", new Object[0]);
        }
        catch (Exception e) {
            throw new PluginException(PluginException.ErrorCode.INITIALIZATION_FAILED, "Failed to register event handlers: " + e.getMessage(), e);
        }
    }

    private void registerCommands() {
        this.debug("Registering commands...", new Object[0]);
        try {
            RaCommand command = new RaCommand(this);
            CommandMeta meta = this.server.getCommandManager().metaBuilder("ra").aliases(new String[]{"relishauth"}).plugin((Object)this).build();
            this.server.getCommandManager().register(meta, (Command)command);
            LogoutCommand logoutCommand = new LogoutCommand(this);
            CommandMeta logoutMeta = this.server.getCommandManager().metaBuilder("logout").plugin((Object)this).build();
            this.server.getCommandManager().register(logoutMeta, (Command)logoutCommand);
            this.debug("Commands registered successfully", new Object[0]);
        }
        catch (Exception e) {
            this.logger.error("[INIT] Failed to register commands: {}", (Object)e.getMessage(), (Object)e);
        }
    }

    private void handleInitializationFailure(String phase, Exception e) {
        this.initState = InitializationState.FAILED;
        this.initialized.set(false);
        this.logger.error("");
        this.logger.error("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.error("\u001b[1;91m  INITIALIZATION FAILED  \u001b[0m");
        this.logger.error("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.error("");
        this.logger.error("\u001b[91m  \u25b6 \u001b[0m\u001b[97mPhase: \u001b[0m\u001b[91m{}\u001b[0m", (Object)phase);
        this.logger.error("\u001b[91m  \u25b6 \u001b[0m\u001b[97mError: \u001b[0m\u001b[90m{}\u001b[0m", (Object)e.getMessage());
        if (e instanceof PluginException) {
            PluginException pe = (PluginException)e;
            this.logger.error("\u001b[91m  \u25b6 \u001b[0m\u001b[97mError Code: \u001b[0m\u001b[90m{}\u001b[0m", (Object)pe.getErrorCode().getCode());
            if (pe.getContext() != null) {
                this.logger.error("\u001b[91m  \u25b6 \u001b[0m\u001b[97mContext: \u001b[0m\u001b[90m{}\u001b[0m", (Object)pe.getContext());
            }
        }
        if (this.debugEnabled && e.getCause() != null) {
            this.logger.error("\u001b[91m  \u25b6 \u001b[0m\u001b[97mCause: \u001b[0m\u001b[90m{}\u001b[0m", (Object)e.getCause().getMessage());
        }
        this.logger.error("");
        this.logger.error("\u001b[91m  \u26a0 The plugin will not function correctly.\u001b[0m");
        this.logger.error("\u001b[91m  \u26a0 Please check your configuration and try again.\u001b[0m");
        this.logger.error("");
        this.logger.error("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.error("");
        this.cleanupResources();
    }

    private void logConfigurationSummary() {
        this.debug("=== Configuration Summary ===", new Object[0]);
        this.debug("Authentication method: {}", this.config.getString("authentication.method", "password"));
        this.debug("Premium auto-login: {}", this.config.getBoolean("authentication.premium-auto-login", true));
        this.debug("Database type: {}", this.config.getString("database.type", "sqlite"));
        this.debug("Password hashing: {}", this.config.getString("authentication.password.hashing", "argon2"));
        this.debug("Discord bot enabled: {}", this.discordBot != null && this.discordBot.isEnabled());
        this.debug("Session duration: {}", this.config.getString("session.duration", "1h"));
        this.debug("=============================", new Object[0]);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.shuttingDown.getAndSet(true)) {
            return;
        }
        this.printShutdownHeader();
        this.cleanupResources();
        this.printShutdownComplete();
    }

    private void cleanupResources() {
        String dbType;
        String string = dbType = this.config != null ? this.config.getString("database.type", "sqlite").toUpperCase() : "UNKNOWN";
        if (this.database != null) {
            this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mShutting down {}...\u001b[0m", (Object)dbType);
            try {
                this.database.close();
                this.debug("Database Auth closed", new Object[0]);
            }
            catch (Exception e) {
                this.logger.warn("Error closing database: {}", (Object)e.getMessage());
            }
        }
        if (this.discordBot != null && this.discordBot.isEnabled()) {
            this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mShutting down Discord...\u001b[0m");
            try {
                this.discordBot.shutdown();
                Thread.sleep(2000L);
                this.debug("Discord bot shutdown complete", new Object[0]);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.logger.warn("Discord shutdown interrupted: {}", (Object)e.getMessage());
            }
            catch (Exception e) {
                this.logger.warn("Error shutting down Discord bot: {}", (Object)e.getMessage());
            }
        }
        if (this.premiumManager != null) {
            this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mShutting down Premium Verification...\u001b[0m");
            try {
                this.debug("Premium verification shutdown complete", new Object[0]);
            }
            catch (Exception e) {
                this.logger.warn("Error shutting down premium verification: {}", (Object)e.getMessage());
            }
        }
        if (this.authManager != null) {
            this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mShutting down Authentication...\u001b[0m");
            try {
                this.authManager.shutdown();
                this.debug("Auth manager shutdown complete", new Object[0]);
            }
            catch (Exception e) {
                this.logger.warn("Error shutting down auth manager: {}", (Object)e.getMessage());
            }
        }
        if (this.limboHandler != null) {
            this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mShutting down Limbo...\u001b[0m");
            try {
                this.debug("Limbo handler cleanup complete", new Object[0]);
            }
            catch (Exception e) {
                this.logger.warn("Error shutting down limbo handler: {}", (Object)e.getMessage());
            }
        }
        this.debug("Resource cleanup complete", new Object[0]);
    }

    public void connectPlayerToBackend(Player player) {
        if (!this.isInitialized()) {
            this.logger.warn("Cannot connect player {} - plugin not initialized", (Object)player.getUsername());
            return;
        }
        this.debug("Connecting player to backend: {}", player.getUsername());
        try {
            ConnectionUtil.connectPlayerToBackend(player, this.server, this.config, this.logger, this.debugEnabled);
        }
        catch (Exception e) {
            this.logger.error("Failed to connect player {} to backend: {}", (Object)player.getUsername(), (Object)e.getMessage());
        }
    }

    public void debug(String message, Object ... args) {
        if (this.debugEnabled) {
            this.logger.info("[DEBUG] " + message, args);
        }
    }

    public boolean isInitialized() {
        return this.initialized.get() && this.initState == InitializationState.FULLY_INITIALIZED;
    }

    public InitializationState getInitializationState() {
        return this.initState;
    }

    public boolean reloadConfig() {
        try {
            if (this.updateManager != null) {
                try {
                    boolean updated = this.updateManager.updateConfigurationFiles();
                    if (updated) {
                        this.debug("[UPDATE] Config updater applied changes during reload", new Object[0]);
                    }
                }
                catch (Exception e) {
                    this.logger.warn("[UPDATE] Failed to run config updater during reload: {}", (Object)e.getMessage());
                }
            }
            this.config.reload();
            this.debugEnabled = this.config.getBoolean("debug", false);
            if (this.messageManager != null) {
                if (this.updateManager != null) {
                    try {
                        this.updateManager.updateLanguageFiles(this.config.getString("language", "en"));
                    }
                    catch (Exception e) {
                        this.logger.warn("[UPDATE] Failed to run language updater during reload: {}", (Object)e.getMessage());
                    }
                }
                this.messageManager.reload();
            }
            this.initializeGroupSyncService();
            this.debug("Configuration reloaded successfully", new Object[0]);
            return true;
        }
        catch (Exception e) {
            this.logger.error("[CONFIG] Failed to reload configuration: {}", (Object)e.getMessage());
            return false;
        }
    }

    public ProxyServer getServer() {
        return this.server;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public boolean isDebugEnabled() {
        return this.debugEnabled;
    }

    public boolean isUpdateCheckEnabled() {
        if (this.config == null) {
            return true;
        }
        if (this.config.contains("check-for-updates")) {
            return this.config.getBoolean("check-for-updates", true);
        }
        if (this.config.contains("update-check.enabled")) {
            return this.config.getBoolean("update-check.enabled", true);
        }
        return true;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public AuthenticationManager getAuthManager() {
        return this.authManager;
    }

    public Config getConfig() {
        return this.config;
    }

    public Path getDataDirectory() {
        return this.dataDirectory;
    }

    public LimboAuthHandler getLimboHandler() {
        return this.limboHandler;
    }

    public MessageManager getMessageManager() {
        return this.messageManager;
    }

    public FloodgateHelper getFloodgateHelper() {
        return this.floodgateHelper;
    }

    public DiscordIntegration getDiscordBot() {
        return this.discordBot;
    }

    public AuthService getAuthService() {
        return this.authService;
    }

    public ServerConnectEventHandler getServerConnectHandler() {
        return this.serverConnectHandler;
    }

    public InitialServerEventHandler getInitialServerEventHandler() {
        return this.initialServerEventHandler;
    }

    public AuthDatabase getDatabase() {
        return this.database;
    }

    public UpdateManager getUpdateManager() {
        return this.updateManager;
    }

    public SkinApplier getSkinApplier() {
        return this.skinApplier;
    }

    public GroupSyncService getGroupSyncService() {
        return this.groupSyncService;
    }

    private void printStartupHeader() {
        this.logger.info("");
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("\u001b[1;96m  RELISH Auth  \u001b[0m\u001b[90mv{}\u001b[0m", (Object)BuildConstants.VERSION);
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("");
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[90mInitializing plugin...\u001b[0m");
    }

    private void printStartupSuccess() {
        boolean hasLimboAPI;
        long loadTime = System.currentTimeMillis() - this.startTime;
        String timeString = loadTime < 1000L ? loadTime + "ms" : String.format("%.2fs", (double)loadTime / 1000.0);
        String dbType = this.config.getString("database.type", "sqlite").toUpperCase();
        String authMethod = this.config.getString("authentication.method", "password").toUpperCase();
        StringBuilder hooks = new StringBuilder();
        boolean hasFloodgate = this.server.getPluginManager().getPlugin("floodgate").isPresent();
        if (hasFloodgate) {
            hooks.append("Floodgate");
        }
        if (this.discordBot != null && this.discordBot.isEnabled()) {
            if (hooks.length() > 0) {
                hooks.append(", ");
            }
            hooks.append("Discord");
        }
        if (hasLimboAPI = this.server.getPluginManager().getPlugin("limboapi").isPresent()) {
            if (hooks.length() > 0) {
                hooks.append(", ");
            }
            hooks.append("LimboAPI");
        }
        if (hooks.length() == 0) {
            hooks.append("None");
        }
        this.logger.info("");
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[97mStatus: \u001b[0m\u001b[92mEnabled\u001b[0m");
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[97mLoad Time: \u001b[0m\u001b[93m{}\u001b[0m", (Object)timeString);
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[97mDatabase: \u001b[0m\u001b[96m{}\u001b[0m", (Object)dbType);
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[97mAuth: \u001b[0m\u001b[96m{}\u001b[0m", (Object)authMethod);
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[97mHooks: \u001b[0m\u001b[90m{}\u001b[0m", (Object)hooks.toString());
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("");
    }

    private void printShutdownHeader() {
        this.logger.info("");
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("\u001b[1;96m  RELISH Auth  \u001b[0m\u001b[90mv{}\u001b[0m", (Object)BuildConstants.VERSION);
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("");
    }

    private void printShutdownComplete() {
        this.logger.info("");
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("\u001b[96m  \u25b6 \u001b[0m\u001b[91mDisabled\u001b[0m");
        this.logger.info("\u001b[36m\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u001b[0m");
        this.logger.info("");
    }

    public static enum InitializationState {
        NOT_STARTED,
        CONFIG_LOADED,
        DATABASE_CONNECTED,
        SERVICES_INITIALIZED,
        HANDLERS_REGISTERED,
        FULLY_INITIALIZED,
        FAILED;

    }
}
