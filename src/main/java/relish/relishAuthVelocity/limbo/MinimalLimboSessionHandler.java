package relish.relishAuthVelocity.limbo;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;
import relish.relishAuthVelocity.utils.MessageManager;

public class MinimalLimboSessionHandler
implements LimboSessionHandler {
    private static final double MAX_DISTANCE_FROM_SPAWN = 8.0;
    private static final int TITLE_FADE_IN_MS = 500;
    private static final int TITLE_STAY_SECONDS = 999999;
    private static final int TITLE_FADE_OUT_MS = 500;
    private final RelishAuthVelocity plugin;
    private final UUID uuid;
    private final AuthenticationCallback callback;
    private final Config config;
    private LimboPlayer limboPlayer;
    private final boolean isRegistered;
    private boolean promptSent = false;
    AuthState authState = AuthState.WAITING;
    String firstInput = null;
    private long authStartTime;
    private int authTimeout;
    private ScheduledTask timeoutTask;
    private BossBar timeoutBossBar;
    private final Object stateLock = new Object();
    private final AtomicBoolean sessionInitialized = new AtomicBoolean(false);
    private static final double AUTH_WORLD_SPAWN_X = 0.0;
    private static final double AUTH_WORLD_SPAWN_Y = 64.0;
    private static final double AUTH_WORLD_SPAWN_Z = 0.0;

    public MinimalLimboSessionHandler(RelishAuthVelocity plugin, UUID uuid, AuthenticationCallback callback, Config config, boolean isRegistered) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.callback = callback;
        this.config = config;
        this.isRegistered = isRegistered;
        this.authTimeout = config.getInt("security.authentication-timeout", 300);
        this.authStartTime = System.currentTimeMillis();
    }

    public void onSpawn(Limbo server, LimboPlayer player) {
        this.initializeSession(player);
    }

    public void onConfig(Limbo server, LimboPlayer player) {
        this.initializeSession(player);
    }

    private void initializeSession(LimboPlayer player) {
        this.limboPlayer = player;
        if (!this.sessionInitialized.compareAndSet(false, true)) {
            return;
        }
        this.authStartTime = System.currentTimeMillis();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            if (!this.promptSent && this.limboPlayer != null) {
                this.showLimboTitle();
                this.sendAuthPrompt();
                this.promptSent = true;
                this.startTimeoutMonitor();
            }
        }).delay((long)this.config.getInt("customization.limbo.timing.auth-prompt-delay", 100), TimeUnit.MILLISECONDS).schedule();
    }

    private void showLimboTitle() {
        if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        if (!this.config.getBoolean("customization.limbo.title.enabled", true)) {
            return;
        }
        Component title = this.plugin.getMessageManager().getMessage("limbo.title");
        Component subtitle = this.plugin.getMessageManager().getMessage("limbo.subtitle");
        Title titleObj = Title.title((Component)title, (Component)subtitle, (Title.Times)Title.Times.times((Duration)Duration.ofMillis(500L), (Duration)Duration.ofSeconds(999999L), (Duration)Duration.ofMillis(500L)));
        this.limboPlayer.getProxyPlayer().showTitle(titleObj);
    }

    private void startTimeoutMonitor() {
        this.initializeBossBar();
        this.timeoutTask = this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
                this.cancelTimeoutTask();
                return;
            }
            if (this.authState == AuthState.PROCESSING) {
                return;
            }
            long elapsed = (System.currentTimeMillis() - this.authStartTime) / 1000L;
            long remaining = (long)this.authTimeout - elapsed;
            if (remaining <= 0L) {
                List<Component> kickMessage = this.plugin.getMessageManager().getMessageList("kick.timeout", new String[0]);
                this.hideBossBar();
                this.limboPlayer.getProxyPlayer().disconnect((Component)(kickMessage.isEmpty() ? Component.text((String)"Authentication timeout. Please reconnect.", (TextColor)NamedTextColor.RED) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMessage)));
                this.cancelTimeoutTask();
                return;
            }
            this.sendActionBar(remaining);
            this.updateBossBar(remaining);
            int warningThreshold = this.config.getInt("security.timeout-warnings.warning-threshold", 30);
            int warningInterval = this.config.getInt("security.timeout-warnings.warning-interval", 10);
            if (remaining <= (long)warningThreshold && remaining % (long)warningInterval == 0L) {
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("auth-timeout-warning", "{time}", remaining + "s"));
            }
        }).repeat((long)this.config.getInt("customization.limbo.monitor.check-interval", 1), TimeUnit.SECONDS).schedule();
    }

    private void cancelTimeoutTask() {
        if (this.timeoutTask != null && this.timeoutTask.status() == TaskStatus.SCHEDULED) {
            this.timeoutTask.cancel();
            this.timeoutTask = null;
            this.plugin.debug("[LIMBO] Cancelled timeout task for {}", this.uuid);
        }
        this.hideBossBar();
    }

    public void onDisconnect() {
        this.cancelTimeoutTask();
        if (this.callback != null) {
            this.callback.onDisconnect(this.uuid);
        }
    }

    public void onChat(String message) {
        Object object = this.stateLock;
        synchronized (object) {
            String authMethod = this.config.getString("authentication.method", "password");
            if ("discord".equalsIgnoreCase(authMethod)) {
                this.handleDiscordUsername(message);
                return;
            }
            if (this.authState == AuthState.PROCESSING) {
                return;
            }
            try {
                if (this.callback.isRegistered(this.uuid)) {
                    this.handleLogin(message);
                } else {
                    this.handleRegister(message);
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[LIMBO-AUTH] Error processing chat authentication for {}", (Object)this.uuid, (Object)e);
                this.sendMessage(this.plugin.getMessageManager().getMessage("error-auth-processing"));
            }
        }
    }

    private void handleDiscordUsername(String username) {
        if (this.authState == AuthState.PROCESSING) {
            return;
        }
        this.authState = AuthState.PROCESSING;
        String tempUsername = username.trim();
        if (tempUsername.startsWith("@")) {
            tempUsername = tempUsername.substring(1);
        }
        String cleanUsername = tempUsername;
        this.plugin.debug("[LIMBO-AUTH] Looking up Discord username {}", cleanUsername);
        this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("looking-up-discord"));
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            UUID existingUuid;
            DiscordUserSearchResult searchResult = this.plugin.getDiscordBot().findUserByUsername(cleanUsername);
            if (searchResult == null || !searchResult.found()) {
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-username-not-found-general"));
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-use-username-not-display"));
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-username-under-display"));
                String inviteLink = this.config.getString("discord.invite-link", "");
                if (!inviteLink.isEmpty()) {
                    Component linkComponent = ((TextComponent)Component.text((String)inviteLink, (TextColor)NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl((String)inviteLink))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"Click to open invite", (TextColor)NamedTextColor.GRAY)));
                    this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-join-here").append(linkComponent));
                } else {
                    this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-contact-admin-invite"));
                }
                this.authState = AuthState.WAITING;
                return;
            }
            if (!searchResult.inGuild()) {
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-not-in-server"));
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-must-join-authenticate"));
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-join-server-first"));
                String inviteLink = this.config.getString("discord.invite-link", "");
                if (!inviteLink.isEmpty()) {
                    Component linkComponent = ((TextComponent)Component.text((String)inviteLink, (TextColor)NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl((String)inviteLink))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"Click to open invite", (TextColor)NamedTextColor.GRAY)));
                    this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-join-here").append(linkComponent));
                } else {
                    this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-contact-admin-invite"));
                }
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("discord-rejoin-reconnect"));
                this.authState = AuthState.WAITING;
                return;
            }
            String discordId = searchResult.userId();
            this.plugin.debug("[LIMBO-AUTH] Discord user resolved to {}", discordId);
            boolean isDiscordLinked = this.plugin.getAuthService().getDatabase().isDiscordLinked(discordId);
            if (isDiscordLinked && (existingUuid = this.plugin.getAuthService().getUuidByDiscordId(discordId)) != null && !existingUuid.equals(this.uuid)) {
                this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("already-linked"));
                this.authState = AuthState.WAITING;
                return;
            }
            this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("request-sent"));
            this.plugin.getDiscordBot().sendVerificationRequest(discordId, this.limboPlayer.getProxyPlayer().getUsername(), this.uuid, this.limboPlayer.getProxyPlayer().getRemoteAddress().getAddress().getHostAddress(), true);
            this.limboPlayer.getProxyPlayer().sendMessage(this.plugin.getMessageManager().getMessage("waiting-discord-verification"));
            this.authState = AuthState.WAITING;
        }).schedule();
    }

    private void handleLogin(String password) {
        this.authState = AuthState.PROCESSING;
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            if (this.limboPlayer != null && this.limboPlayer.getProxyPlayer() != null) {
                this.plugin.getLimboHandler().processLogin(this.limboPlayer.getProxyPlayer(), password);
            }
            this.authState = AuthState.WAITING;
        }).schedule();
    }

    private void handleRegister(String message) {
        if (this.authState == AuthState.WAITING) {
            this.firstInput = message;
            this.authState = AuthState.WAITING_CONFIRM;
            for (String line : this.plugin.getMessageManager().getRawMessageList("auth.register.confirm")) {
                Player player = this.limboPlayer.getProxyPlayer();
                this.plugin.getMessageManager();
                player.sendMessage(MessageManager.parseColors(line));
            }
        } else if (this.authState == AuthState.WAITING_CONFIRM) {
            this.authState = AuthState.PROCESSING;
            String password = this.firstInput;
            String confirm = message;
            this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                if (this.limboPlayer != null && this.limboPlayer.getProxyPlayer() != null) {
                    this.plugin.getLimboHandler().processRegister(this.limboPlayer.getProxyPlayer(), password, confirm);
                }
                this.authState = AuthState.WAITING;
                this.firstInput = null;
            }).schedule();
        }
    }

    public void onMove(double x, double y, double z) {
        double distance;
        if (this.limboPlayer != null && (distance = Math.sqrt(Math.pow(x - 0.0, 2.0) + Math.pow(y - 64.0, 2.0) + Math.pow(z - 0.0, 2.0))) > 8.0) {
            this.limboPlayer.teleport(0.0, 64.0, 0.0, 0.0f, 0.0f);
        }
    }

    private void sendAuthPrompt() {
        if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        String authMethod = this.config.getString("authentication.method", "password");
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        if ("discord".equalsIgnoreCase(authMethod)) {
            if (this.isRegistered) {
                String username = this.limboPlayer.getProxyPlayer().getUsername();
                UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(this.uuid, username);
                String discordId = this.plugin.getAuthService().getDiscordId(accountUuid);
                if (discordId != null && !discordId.isEmpty()) {
                    Player player = this.limboPlayer.getProxyPlayer();
                    this.plugin.getMessageManager();
                    player.sendMessage(MessageManager.parseColors("<#87CEEB>\u2726 <#A0A0A0>Verifying your Discord account..."));
                    Player player2 = this.limboPlayer.getProxyPlayer();
                    this.plugin.getMessageManager();
                    player2.sendMessage(MessageManager.parseColors("<#A0A0A0>Please check your Discord DMs"));
                } else {
                    for (String line : this.plugin.getMessageManager().getRawMessageList("discord-auth-required")) {
                        line = line.replace("{server}", serverName);
                        Player player = this.limboPlayer.getProxyPlayer();
                        this.plugin.getMessageManager();
                        player.sendMessage(MessageManager.parseColors(line));
                    }
                }
            } else {
                for (String line : this.plugin.getMessageManager().getRawMessageList("discord-auth-required")) {
                    line = line.replace("{server}", serverName);
                    Player player = this.limboPlayer.getProxyPlayer();
                    this.plugin.getMessageManager();
                    player.sendMessage(MessageManager.parseColors(line));
                }
            }
            return;
        }
        if (this.isRegistered) {
            for (String line : this.plugin.getMessageManager().getRawMessageList("auth.login.prompt")) {
                line = line.replace("{server}", serverName);
                Player player = this.limboPlayer.getProxyPlayer();
                this.plugin.getMessageManager();
                player.sendMessage(MessageManager.parseColors(line));
            }
        } else {
            int minLen = this.config.getInt("authentication.password.min-length", 6);
            int maxLen = this.config.getInt("authentication.password.max-length", 32);
            for (String line : this.plugin.getMessageManager().getRawMessageList("auth.register.prompt")) {
                line = line.replace("{server}", serverName);
                line = line.replace("{min}", String.valueOf(minLen));
                line = line.replace("{max}", String.valueOf(maxLen));
                Player player = this.limboPlayer.getProxyPlayer();
                this.plugin.getMessageManager();
                player.sendMessage(MessageManager.parseColors(line));
            }
        }
    }

    public void onGround(boolean onGround) {
    }

    private void sendMessage(Component component) {
        if (this.limboPlayer != null && this.limboPlayer.getProxyPlayer() != null) {
            this.limboPlayer.getProxyPlayer().sendMessage(component);
        }
    }

    private void initializeBossBar() {
        if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        if (!this.config.getBoolean("customization.limbo.bossbar.enabled", true)) {
            return;
        }
        if (this.timeoutBossBar == null) {
            this.timeoutBossBar = BossBar.bossBar((Component)this.buildBossBarTitle(this.authTimeout), (float)1.0f, (BossBar.Color)this.parseBossBarColor(this.config.getString("customization.limbo.bossbar.color", "BLUE")), (BossBar.Overlay)this.parseBossBarOverlay(this.config.getString("customization.limbo.bossbar.overlay", "PROGRESS")));
        }
        this.limboPlayer.getProxyPlayer().showBossBar(this.timeoutBossBar);
        this.updateBossBar(this.authTimeout);
    }

    private void sendActionBar(long remaining) {
        if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        if (!this.config.getBoolean("customization.limbo.actionbar.enabled", true)) {
            return;
        }
        this.limboPlayer.getProxyPlayer().sendActionBar(this.plugin.getMessageManager().getMessage("limbo.actionbar", "{time}", String.valueOf(remaining)));
    }

    private void updateBossBar(long remaining) {
        if (this.timeoutBossBar == null || this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        float progress = this.authTimeout <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, (float)remaining / (float)this.authTimeout));
        this.timeoutBossBar.progress(progress);
        this.timeoutBossBar.name(this.buildBossBarTitle(remaining));
    }

    private void hideBossBar() {
        if (this.timeoutBossBar != null && this.limboPlayer != null && this.limboPlayer.getProxyPlayer() != null) {
            this.limboPlayer.getProxyPlayer().hideBossBar(this.timeoutBossBar);
        }
        this.timeoutBossBar = null;
    }

    private Component buildBossBarTitle(long remaining) {
        return this.plugin.getMessageManager().getMessage("limbo.bossbar", "{time}", String.valueOf(remaining));
    }

    private BossBar.Color parseBossBarColor(String value) {
        try {
            return BossBar.Color.valueOf((String)value.trim().toUpperCase(Locale.ROOT));
        }
        catch (Exception ignored) {
            return BossBar.Color.BLUE;
        }
    }

    private BossBar.Overlay parseBossBarOverlay(String value) {
        try {
            return BossBar.Overlay.valueOf((String)value.trim().toUpperCase(Locale.ROOT));
        }
        catch (Exception ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public LimboPlayer getLimboPlayer() {
        return this.limboPlayer;
    }

    public static enum AuthState {
        WAITING,
        WAITING_CONFIRM,
        PROCESSING;

    }

    public static interface AuthenticationCallback {
        public void onAuthenticated(UUID var1);

        public void onDisconnect(UUID var1);

        public boolean isRegistered(UUID var1);
    }
}
