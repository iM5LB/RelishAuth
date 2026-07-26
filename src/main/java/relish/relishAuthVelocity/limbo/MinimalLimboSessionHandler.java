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
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.constants.AuthMethod;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.utils.FloodgateHelper;
import relish.relishAuthVelocity.utils.MessageManager;
import relish.relishAuthVelocity.utils.ValidationUtil;

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
    private boolean awaitingDiscordLink = false;
    private SelectedLoginMethod selectedLoginMethod = SelectedLoginMethod.UNSET;
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
        this.showLimboTitle("limbo.title", "limbo.subtitle");
    }

    private void showLimboTitle(String titleKey, String subtitleKey) {
        if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
            return;
        }
        if (!this.config.getBoolean("customization.limbo.title.enabled", true)) {
            return;
        }
        Component title = this.plugin.getMessageManager().getMessage(titleKey);
        Component subtitle = this.plugin.getMessageManager().getMessage(subtitleKey);
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
            if (this.awaitingDiscordLink) {
                this.handleDiscordUsername(message);
                return;
            }
            if (this.authState == AuthState.CHOOSING_METHOD) {
                this.handleChooserInput(message);
                return;
            }
            if (this.authState == AuthState.PROCESSING) {
                return;
            }
            if (this.selectedLoginMethod == SelectedLoginMethod.DISCORD
                    || (this.selectedLoginMethod == SelectedLoginMethod.UNSET
                        && AuthMethod.fromString(this.config.getString("authentication.method", "password")) == AuthMethod.DISCORD)) {
                this.handleDiscordUsername(message);
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

    public void enableDiscordLinkPhase() {
        Object object = this.stateLock;
        synchronized (object) {
            this.awaitingDiscordLink = true;
            this.selectedLoginMethod = SelectedLoginMethod.DISCORD;
            this.authState = AuthState.WAITING;
            this.firstInput = null;
        }
    }

    private void handleChooserInput(String raw) {
        String input = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        // ClickEvent.runCommand arrives here as "/password" or "/discord" (LimboAPI onChat).
        if (input.startsWith("/")) {
            input = input.substring(1).trim();
        }
        if (input.equals("password") || input.equals("pass") || input.equals("1") || input.equals("p")) {
            this.selectPasswordMethod();
            return;
        }
        if (input.equals("discord") || input.equals("disc") || input.equals("2") || input.equals("d")) {
            this.selectDiscordMethod();
            return;
        }
        this.sendMessage(this.plugin.getMessageManager().getMessage("auth.chooser.invalid"));
        this.sendChooserPrompt();
    }

    private void selectPasswordMethod() {
        Object object = this.stateLock;
        synchronized (object) {
            if (this.authState != AuthState.CHOOSING_METHOD && this.selectedLoginMethod != SelectedLoginMethod.UNSET) {
                return;
            }
            this.selectedLoginMethod = SelectedLoginMethod.PASSWORD;
            this.awaitingDiscordLink = false;
            this.authState = AuthState.WAITING;
            this.firstInput = null;
        }
        this.sendMessage(this.plugin.getMessageManager().getMessage("auth.chooser.selected-password"));
        this.sendPasswordPrompt();
    }

    private void selectDiscordMethod() {
        Object object = this.stateLock;
        synchronized (object) {
            if (this.authState != AuthState.CHOOSING_METHOD && this.selectedLoginMethod != SelectedLoginMethod.UNSET) {
                return;
            }
            this.selectedLoginMethod = SelectedLoginMethod.DISCORD;
            this.authState = AuthState.WAITING;
            this.firstInput = null;
        }
        this.sendMessage(this.plugin.getMessageManager().getMessage("auth.chooser.selected-discord"));
        this.beginDiscordLoginFlow();
    }

    private void beginDiscordLoginFlow() {
        Player player = this.limboPlayer != null ? this.limboPlayer.getProxyPlayer() : null;
        if (player == null) {
            return;
        }
        UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(this.uuid, player.getUsername());
        String discordId = this.plugin.getAuthService().getDiscordId(accountUuid);
        if (ValidationUtil.isRealDiscordId(discordId)) {
            if (this.plugin.getDiscordBot() == null || !this.plugin.getDiscordBot().isEnabled()) {
                this.sendMessage(this.plugin.getMessageManager().getMessage("discord-bot-not-configured"));
                this.resetToChooser();
                return;
            }
            this.sendDiscordVerification(player, discordId, false);
            return;
        }
        this.awaitingDiscordLink = true;
        this.selectedLoginMethod = SelectedLoginMethod.DISCORD;
        this.showDiscordUsernamePrompt(player);
    }

    private void sendDiscordVerification(Player player, String discordId, boolean isNewLink) {
        try {
            this.plugin.getDiscordBot().sendVerificationRequest(
                    discordId,
                    player.getUsername(),
                    this.uuid,
                    player.getRemoteAddress().getAddress().getHostAddress(),
                    isNewLink
            );
        } catch (Exception e) {
            this.plugin.getLogger().warn("[LIMBO-AUTH] Failed to send Discord verification for {}: {}", player.getUsername(), e.getMessage());
            this.sendMessage(this.plugin.getMessageManager().getMessage("error-auth-processing"));
            this.resetToChooser();
            return;
        }
        this.showLimboTitle("limbo.discord-verify-title", "limbo.discord-verify-subtitle");
        this.showDiscordVerifyPrompt(player);
    }

    private void showDiscordVerifyPrompt(Player player) {
        player.sendMessage(MessageManager.parseColors("<#87CEEB>\u2726 <#A0A0A0>Verifying your Discord account..."));
        player.sendMessage(this.plugin.getMessageManager().getMessage("error-discord-dm-sent"));
    }

    private void showDiscordUsernamePrompt(Player player) {
        this.showLimboTitle("limbo.discord-link-title", "limbo.discord-link-subtitle");
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        for (String line : this.plugin.getMessageManager().getRawMessageList("discord-auth-required")) {
            player.sendMessage(MessageManager.parseColors(line.replace("{server}", serverName == null ? "" : serverName)));
        }
    }

    private void resetToChooser() {
        Object object = this.stateLock;
        synchronized (object) {
            this.selectedLoginMethod = SelectedLoginMethod.UNSET;
            this.awaitingDiscordLink = false;
            this.authState = AuthState.CHOOSING_METHOD;
            this.firstInput = null;
        }
        this.sendChooserPrompt();
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
            if (this.limboPlayer == null || this.limboPlayer.getProxyPlayer() == null) {
                this.authState = AuthState.WAITING;
                return;
            }
            Player proxyPlayer = this.limboPlayer.getProxyPlayer();
            DiscordUserSearchResult searchResult = this.plugin.getDiscordBot().findUserByUsername(cleanUsername);
            if (searchResult == null || !searchResult.found()) {
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-username-not-found-general"));
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-use-username-not-display"));
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-username-under-display"));
                String inviteLink = this.config.getString("discord.invite-link", "");
                if (!inviteLink.isEmpty()) {
                    Component linkComponent = ((TextComponent)Component.text((String)inviteLink, (TextColor)NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl((String)inviteLink))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"Click to open invite", (TextColor)NamedTextColor.GRAY)));
                    proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-join-here").append(linkComponent));
                } else {
                    proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-contact-admin-invite"));
                }
                this.authState = AuthState.WAITING;
                return;
            }
            if (!searchResult.inGuild()) {
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-not-in-server"));
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-must-join-authenticate"));
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-join-server-first"));
                String inviteLink = this.config.getString("discord.invite-link", "");
                if (!inviteLink.isEmpty()) {
                    Component linkComponent = ((TextComponent)Component.text((String)inviteLink, (TextColor)NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl((String)inviteLink))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"Click to open invite", (TextColor)NamedTextColor.GRAY)));
                    proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-join-here").append(linkComponent));
                } else {
                    proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-contact-admin-invite"));
                }
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("discord-rejoin-reconnect"));
                this.authState = AuthState.WAITING;
                return;
            }
            String discordId = searchResult.userId();
            this.plugin.debug("[LIMBO-AUTH] Discord user resolved to {}", discordId);
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(this.uuid, proxyPlayer.getUsername());
            boolean isDiscordLinked = this.plugin.getAuthService().getDatabase().isDiscordLinked(discordId);
            if (isDiscordLinked && (existingUuid = this.plugin.getAuthService().getUuidByDiscordId(discordId)) != null && !existingUuid.equals(accountUuid)) {
                proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("already-linked"));
                this.authState = AuthState.WAITING;
                return;
            }
            proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("request-sent"));
            this.plugin.getDiscordBot().sendVerificationRequest(discordId, proxyPlayer.getUsername(), this.uuid, proxyPlayer.getRemoteAddress().getAddress().getHostAddress(), true);
            proxyPlayer.sendMessage(this.plugin.getMessageManager().getMessage("waiting-discord-verification"));
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
        AuthMethod method = AuthMethod.fromString(this.config.getString("authentication.method", "password"));
        if (this.shouldShowLoginChooser(method)) {
            this.authState = AuthState.CHOOSING_METHOD;
            this.selectedLoginMethod = SelectedLoginMethod.UNSET;
            this.sendChooserPrompt();
            return;
        }
        if (method == AuthMethod.DISCORD) {
            this.selectedLoginMethod = SelectedLoginMethod.DISCORD;
            this.beginDiscordLoginFlow();
            return;
        }
        // password or hybrid: start with password prompts
        this.selectedLoginMethod = SelectedLoginMethod.PASSWORD;
        if (this.isRegistered) {
            this.sendPasswordPrompt();
        } else {
            this.sendRegisterPrompt();
        }
    }

    private boolean shouldShowLoginChooser(AuthMethod method) {
        // Hybrid requires both factors in sequence — no chooser.
        if (method == AuthMethod.HYBRID) {
            this.plugin.debug("[CHOOSER] skipped for {}: hybrid requires both factors", this.uuid);
            return false;
        }
        if (!this.config.getBoolean("authentication.login-chooser.enabled", true)) {
            this.plugin.debug("[CHOOSER] skipped for {}: login-chooser.enabled=false", this.uuid);
            return false;
        }
        if (!this.isRegistered || this.plugin.getAuthService() == null) {
            this.plugin.debug("[CHOOSER] skipped for {}: registered={}, authService={}", this.uuid, this.isRegistered, this.plugin.getAuthService() != null);
            return false;
        }
        Player player = this.limboPlayer.getProxyPlayer();
        User account = this.plugin.getAuthService().resolveUserForPlayer(player);
        boolean hasPassword = account != null && account.getPassword() != null && !account.getPassword().isEmpty();
        boolean hasDiscord = account != null && ValidationUtil.isRealDiscordId(account.getDiscordId());
        boolean botOk = this.plugin.getDiscordBot() != null && this.plugin.getDiscordBot().isEnabled();
        boolean show = hasPassword && hasDiscord && botOk;
        if (!show) {
            this.plugin.getLogger().info("[CHOOSER] {} no chooser (password={}, discord={}, bot={})",
                    player.getUsername(), hasPassword, hasDiscord, botOk);
        } else {
            this.plugin.getLogger().info("[CHOOSER] {} showing login method chooser (method={})",
                    player.getUsername(), method);
        }
        return show;
    }

    private void sendChooserPrompt() {
        Player player = this.limboPlayer != null ? this.limboPlayer.getProxyPlayer() : null;
        if (player == null) {
            return;
        }
        this.showLimboTitle("limbo.chooser-title", "limbo.chooser-subtitle");
        // Slight delay so limbo chat is ready (early packets are sometimes dropped).
        this.plugin.getServer().getScheduler().buildTask((Object) this.plugin, () -> {
            Player current = this.limboPlayer != null ? this.limboPlayer.getProxyPlayer() : null;
            if (current != null && this.authState == AuthState.CHOOSING_METHOD) {
                this.sendChooserChatPrompt(current);
            }
        }).delay(250L, TimeUnit.MILLISECONDS).schedule();
    }

    private void sendChooserChatPrompt(Player player) {
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        List<String> header = this.plugin.getMessageManager().getRawMessageList("auth.chooser.header");
        if (header == null || header.isEmpty()) {
            header = List.of(
                    "",
                    "<#1E90FF>💙 <#87CEEB>Choose how to login to {server}",
                    ""
            );
        }
        for (String line : header) {
            player.sendMessage(MessageManager.parseColors(line.replace("{server}", serverName == null ? "" : serverName)));
        }

        FloodgateHelper floodgate = this.plugin.getFloodgateHelper();
        boolean bedrock = floodgate != null && floodgate.isFloodgatePlayer(player);

        // Plain text always — LimboAPI often drops Adventure click components.
        List<String> typeHint = this.plugin.getMessageManager().getRawMessageList("auth.chooser.bedrock-hint");
        if (typeHint == null || typeHint.isEmpty()) {
            typeHint = List.of(
                    "",
                    "<#87CEEB>▶ <#A0A0A0>Type <#87CEEB>password <#A0A0A0>or <#87CEEB>discord <#A0A0A0>in chat",
                    "<#808080>You can also type 1 or 2",
                    ""
            );
        }
        for (String line : typeHint) {
            player.sendMessage(MessageManager.parseColors(line));
        }

        if (bedrock) {
            return;
        }

        String passwordLabel = this.rawOrDefault("auth.chooser.password-button", "Password");
        String discordLabel = this.rawOrDefault("auth.chooser.discord-button", "Discord");

        // Best-effort clickable buttons for Java (may not render in all LimboAPI versions).
        Component passwordButton = Component.text("[" + passwordLabel + "]", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(this.rawOrDefault("auth.chooser.password-hover", "Login with your password"), NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/password"));
        Component discordButton = Component.text("[" + discordLabel + "]", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(this.rawOrDefault("auth.chooser.discord-hover", "Login with Discord verification"), NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand("/discord"));

        player.sendMessage(Component.empty()
                .append(passwordButton)
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(discordButton));
    }

    public void repromptPasswordAuth() {
        this.authState = AuthState.WAITING;
        this.firstInput = null;
        if (this.isRegistered) {
            this.sendPasswordPrompt();
        } else {
            this.sendRegisterPrompt();
        }
    }

    private void sendPasswordPrompt() {
        Player player = this.limboPlayer != null ? this.limboPlayer.getProxyPlayer() : null;
        if (player == null) {
            return;
        }
        this.authState = AuthState.WAITING;
        this.showLimboTitle("limbo.password-login-title", "limbo.password-login-subtitle");
        this.showPasswordLoginPrompt(player);
    }

    private void showPasswordLoginPrompt(Player player) {
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        for (String line : this.plugin.getMessageManager().getRawMessageList("auth.login.prompt")) {
            player.sendMessage(MessageManager.parseColors(line.replace("{server}", serverName == null ? "" : serverName)));
        }
    }

    private void sendRegisterPrompt() {
        Player player = this.limboPlayer != null ? this.limboPlayer.getProxyPlayer() : null;
        if (player == null) {
            return;
        }
        this.authState = AuthState.WAITING;
        this.firstInput = null;
        this.showLimboTitle("limbo.password-register-title", "limbo.password-register-subtitle");
        this.showPasswordRegisterPrompt(player);
    }

    private void showPasswordRegisterPrompt(Player player) {
        String serverName = this.plugin.getMessageManager().getRawMessage("server-name");
        int minLen = this.config.getInt("authentication.password.min-length", 6);
        int maxLen = this.config.getInt("authentication.password.max-length", 32);
        for (String line : this.plugin.getMessageManager().getRawMessageList("auth.register.prompt")) {
            line = line.replace("{server}", serverName == null ? "" : serverName);
            line = line.replace("{min}", String.valueOf(minLen));
            line = line.replace("{max}", String.valueOf(maxLen));
            player.sendMessage(MessageManager.parseColors(line));
        }
    }

    private String rawOrDefault(String key, String fallback) {
        String value = this.plugin.getMessageManager().getRawMessage(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
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
        CHOOSING_METHOD,
        PROCESSING;

    }

    private static enum SelectedLoginMethod {
        UNSET,
        PASSWORD,
        DISCORD;
    }

    public static interface AuthenticationCallback {
        public void onAuthenticated(UUID var1);

        public void onDisconnect(UUID var1);

        public boolean isRegistered(UUID var1);
    }
}
