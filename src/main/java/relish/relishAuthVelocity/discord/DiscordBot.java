package relish.relishAuthVelocity.discord;

import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.discord.DiscordCommands;
import relish.relishAuthVelocity.discord.DiscordMessageUtil;
import relish.relishAuthVelocity.integrations.DiscordIntegration;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.utils.EmojiConfig;

public class DiscordBot
extends ListenerAdapter
implements DiscordIntegration {
    private final RelishAuthVelocity plugin;
    private final Config config;
    private final Logger logger;
    private JDA jda;
    private boolean enabled = false;
    private final DiscordMessageUtil messageUtil;
    private final Map<UUID, String> pendingVerifications = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> pendingVerificationUsernames = new ConcurrentHashMap<UUID, String>();

    public DiscordBot(RelishAuthVelocity plugin, Config config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
        this.messageUtil = new DiscordMessageUtil(plugin);
    }

    @Override
    public boolean initialize() {
        String token = this.config.getString("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
        if (!this.validateBotToken(token)) {
            return false;
        }
        List<String> guildIds = this.getConfiguredGuildIds();
        if (guildIds.isEmpty()) {
            this.logger.warn("[DISCORD] Discord bot token provided but no discord.server-id configured");
            return false;
        }
        try {
            this.initializeJDA(token);
            this.setupGuildAndLoadMembers(guildIds);
            this.logConnectionInfo();
            this.setupCommands();
            this.setupStatusUpdates();
            this.enabled = true;
            return true;
        }
        catch (InvalidTokenException e) {
            this.handleInvalidToken(e);
            return false;
        }
        catch (Exception e) {
            this.handleStartupError(e);
            return false;
        }
    }

    private boolean validateBotToken(String token) {
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE") || token.trim().isEmpty()) {
            this.logger.warn("[DISCORD] Discord bot disabled - no token configured");
            this.enabled = false;
            return false;
        }
        return true;
    }

    private void initializeJDA(String token) throws InterruptedException {
        this.plugin.debug("[DISCORD] Initializing Discord bot...", new Object[0]);
        DiscordCommands discordCommands = new DiscordCommands(this.plugin);
        this.jda = JDABuilder.createDefault(token).enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS).enableCache(CacheFlag.MEMBER_OVERRIDES, new CacheFlag[0]).disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS).setMemberCachePolicy(MemberCachePolicy.ALL).addEventListeners(this, discordCommands).build();
        this.jda.awaitReady();
    }

    private void setupGuildAndLoadMembers(List<String> guildIds) {
        if (guildIds == null || guildIds.isEmpty()) {
            return;
        }
        for (String guildId : guildIds) {
            Guild guild;
            if (guildId == null || guildId.isBlank() || (guild = this.jda.getGuildById(guildId)) == null) continue;
            guild.loadMembers().onError((Throwable error) -> this.logger.warn("[DISCORD] Failed to load guild members ({}): {}", (Object)guildId, (Object)error.getMessage()));
        }
    }

    private void logConnectionInfo() {
        this.jda.getSelfUser().getName();
        List<String> guildIds = this.getConfiguredGuildIds();
        String guildName = "Discord";
        for (String guildId : guildIds) {
            Guild guild;
            if (guildId == null || guildId.isBlank() || (guild = this.jda.getGuildById(guildId)) == null) continue;
            guildName = guild.getName();
            break;
        }
        this.plugin.debug("[DISCORD] Connected to Discord server: {}", guildName);
    }

    private List<String> getConfiguredGuildIds() {
        String legacy;
        String trimmed;
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        try {
            for (String raw : this.config.getStringList("discord.server-id")) {
                if (raw == null || (trimmed = raw.trim()).isEmpty()) continue;
                ids.add(trimmed);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (ids.isEmpty() && (legacy = this.config.getString("discord.server-id", "")) != null && !legacy.trim().isEmpty()) {
            ids.add(legacy.trim());
        }
        if (ids.isEmpty()) {
            try {
                for (String raw : this.config.getStringList("discord.server-ids")) {
                    if (raw == null || (trimmed = raw.trim()).isEmpty()) continue;
                    ids.add(trimmed);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return new ArrayList<String>(ids);
    }

    private void setupCommands() {
        DiscordCommands discordCommands = new DiscordCommands(this.plugin);
        discordCommands.registerCommands(this.jda);
        this.plugin.debug("[DISCORD] Discord commands registered", new Object[0]);
    }

    private void setupStatusUpdates() {
        this.updateBotStatus();
        if (this.config.getBoolean("discord.status.enabled", true)) {
            int updateInterval = this.config.getInt("discord.status.update-interval", 120);
            if (updateInterval < 60) {
                updateInterval = 60;
            }
            this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                if (this.enabled && this.jda != null) {
                    this.updateBotStatus();
                }
            }).repeat((long)updateInterval, TimeUnit.SECONDS).schedule();
        }
    }

    private void handleInvalidToken(InvalidTokenException e) {
        this.logger.error("[DISCORD] Invalid Discord bot token! Please check your config.yml", e);
        this.enabled = false;
        this.jda = null;
    }

    private void handleStartupError(Exception e) {
        this.logger.error("[DISCORD] Failed to start Discord bot: {}", (Object)e.getMessage(), (Object)e);
        this.enabled = false;
        this.jda = null;
    }

    @Override
    public void sendVerificationRequest(String discordId, String playerName, UUID playerUuid, String serverIp, boolean isNewAccount) {
        if (!this.enabled || this.jda == null) {
            return;
        }
        this.plugin.debug("[DISCORD] Sending verification request - UUID: {}, Username: {}, Discord ID: {}", playerUuid, playerName, discordId);
        this.pendingVerifications.put(playerUuid, discordId);
        this.pendingVerificationUsernames.put(playerUuid, playerName);
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            this.pendingVerifications.remove(playerUuid);
            this.pendingVerificationUsernames.remove(playerUuid);
        }).delay((long)this.config.getInt("discord.verification.pending-cleanup-minutes", 5), TimeUnit.MINUTES).schedule();
        String sessionId = playerUuid.toString();
        String ipOnly = serverIp != null ? serverIp.split(":")[0] : "Unknown";
        int timeout = this.config.getInt("security.authentication-timeout", 300);
        MessageEmbed embed = this.messageUtil.buildVerificationEmbed(playerName, ipOnly, isNewAccount, timeout);
        List<Button> buttons = this.messageUtil.buildVerificationButtons(sessionId);
        this.jda.retrieveUserById(discordId).timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).queue(user -> user.openPrivateChannel().timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).flatMap(channel -> (RestAction)channel.sendMessageEmbeds(embed, new MessageEmbed[0]).setActionRow(buttons)).queue(success -> this.plugin.debug("[DISCORD] Sent verification request to {}", playerName), error -> this.logger.warn("[DISCORD] Could not send DM to {}", (Object)playerName)), error -> this.logger.warn("[DISCORD] Failed to retrieve Discord user: {}", (Object)discordId));
    }

    @Override
    public void sendSessionControl(String discordId, String playerName, UUID playerUuid, String serverIp, String sessionDuration) {
        if (!this.enabled || this.jda == null) {
            return;
        }
        String sessionId = playerUuid.toString();
        String ipOnly = serverIp != null ? serverIp.split(":")[0] : "Unknown";
        MessageEmbed embed = this.messageUtil.buildSessionControlEmbed(playerName, ipOnly, sessionDuration);
        List<Button> buttons = this.messageUtil.buildSessionControlButtons(sessionId, discordId);
        this.jda.retrieveUserById(discordId).timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).queue(user -> user.openPrivateChannel().timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).flatMap(channel -> (RestAction)channel.sendMessageEmbeds(embed, new MessageEmbed[0]).addActionRow(buttons)).queue(success -> {}, error -> this.logger.warn("[DISCORD] Could not send session control DM")), error -> this.logger.warn("[DISCORD] Failed to retrieve Discord user for session control"));
    }

    private UUID parseSessionId(String buttonId, int prefixLength, ButtonInteractionEvent event) {
        try {
            return UUID.fromString(buttonId.substring(prefixLength));
        }
        catch (IllegalArgumentException e) {
            event.reply(this.messageUtil.getResponse("session-not-found")).setEphemeral(true).queue();
            return null;
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        // DiscordCommands also listens for buttons; only acknowledge interactions we own.
        if (!this.isOwnedButton(buttonId)) {
            return;
        }
        if (this.isButtonExpired(event)) {
            if (!event.isAcknowledged()) {
                event.reply(this.messageUtil.getResponse("button-expired")).setEphemeral(true).queue();
            }
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
            return;
        }
        if (buttonId.startsWith("verify:")) {
            this.handleVerifyButton(event, buttonId);
        } else if (buttonId.startsWith("deny:")) {
            this.handleDenyButton(event, buttonId);
        } else if (buttonId.startsWith("kickself:")) {
            this.handleKickSelfButton(event, buttonId);
        } else if (buttonId.startsWith("duration:")) {
            this.handleDurationButton(event, buttonId);
        } else if (buttonId.startsWith("unlink:")) {
            this.handleUnlinkButton(event, buttonId);
        } else if (buttonId.startsWith("confirm_unlink:")) {
            this.handleConfirmUnlinkButton(event, buttonId);
        } else if (buttonId.startsWith("deny_unlink:")) {
            this.handleDenyUnlinkButton(event, buttonId);
        } else if (buttonId.startsWith("deny_join:")) {
            this.handleDenyJoinButton(event, buttonId);
        } else if (buttonId.startsWith("set_password:")) {
            this.handleSetPasswordButton(event, buttonId);
        } else if (buttonId.startsWith("toggle_notifications:")) {
            this.handleToggleNotificationsButton(event, buttonId);
        }
    }

    private boolean isOwnedButton(String buttonId) {
        return buttonId.startsWith("verify:")
                || buttonId.startsWith("deny:")
                || buttonId.startsWith("kickself:")
                || buttonId.startsWith("duration:")
                || buttonId.startsWith("unlink:")
                || buttonId.startsWith("confirm_unlink:")
                || buttonId.startsWith("deny_unlink:")
                || buttonId.startsWith("deny_join:")
                || buttonId.startsWith("set_password:")
                || buttonId.startsWith("toggle_notifications:");
    }

    private boolean isButtonExpired(@NotNull ButtonInteractionEvent event) {
        int expirationMinutes;
        OffsetDateTime now;
        OffsetDateTime messageTime = event.getMessage().getTimeCreated();
        long minutesOld = Duration.between(messageTime, now = OffsetDateTime.now()).toMinutes();
        return minutesOld >= (long)(expirationMinutes = this.config.getInt("discord.button-expiration-minutes", 5));
    }

    private void handleVerifyButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 7, event);
        if (playerUuid == null) {
            return;
        }
        String discordId = event.getUser().getId();
        this.plugin.debug("[DISCORD] Verify button clicked - Session UUID: {}, Discord ID: {}", playerUuid, discordId);
        String expectedDiscordId = this.pendingVerifications.get(playerUuid);
        if (expectedDiscordId == null) {
            this.logger.warn("[DISCORD] No pending verification found for UUID: {}", (Object)playerUuid);
            event.reply(this.messageUtil.getResponse("verify-expired")).setEphemeral(true).queue();
            return;
        }
        if (!expectedDiscordId.equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        if (!this.plugin.getLimboHandler().isInLimbo(playerUuid)) {
            this.logger.error("[DISCORD] Player not in limbo - UUID: {}, Username: {}", (Object)playerUuid, (Object)this.pendingVerificationUsernames.get(playerUuid));
            event.reply(this.messageUtil.getResponse("player-offline")).setEphemeral(true).queue();
            this.pendingVerifications.remove(playerUuid);
            this.pendingVerificationUsernames.remove(playerUuid);
            return;
        }
        if (this.config.getBoolean("authentication.enforce-discord-account-match", true)) {
            String originalDiscordId;
            String username = this.pendingVerificationUsernames.get(playerUuid);
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(playerUuid, username);
            User user = this.plugin.getAuthService().getUser(accountUuid);
            if (user != null && user.getDiscordId() != null && user.getDiscordId().startsWith("unlinked_") && !(originalDiscordId = user.getDiscordId().replaceFirst("^unlinked_", "")).equals(discordId)) {
                event.reply(this.messageUtil.getResponse("account-mismatch").replace("{expected_id}", originalDiscordId).replace("{your_id}", discordId)).setEphemeral(true).queue();
                event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
                this.pendingVerifications.remove(playerUuid);
                this.pendingVerificationUsernames.remove(playerUuid);
                return;
            }
        }
        if (!this.deferReply(event, false)) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                this.plugin.debug("[DISCORD] Completing Discord verification for UUID: {}", playerUuid);
                this.plugin.getLimboHandler().completeDiscordAuth(playerUuid, discordId);
                this.assignLinkedRole(discordId);
                event.getHook().sendMessage(this.messageUtil.getResponse("verify-success")).queue();
                event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
                this.pendingVerifications.remove(playerUuid);
                this.pendingVerificationUsernames.remove(playerUuid);
                this.plugin.debug("[DISCORD] Discord verification completed for UUID: {}", playerUuid);
            }
            catch (Exception e) {
                this.logger.error("[DISCORD] Failed to complete verification: {}", (Object)e.getMessage(), (Object)e);
                this.pendingVerifications.remove(playerUuid);
                event.getHook().sendMessage("\u274c Verification failed: " + e.getMessage()).setEphemeral(true).queue();
            }
        }).schedule();
    }

    private void handleDenyButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 5, event);
        if (playerUuid == null) {
            return;
        }
        String discordId = event.getUser().getId();
        String expectedDiscordId = this.pendingVerifications.get(playerUuid);
        if (expectedDiscordId == null) {
            event.reply(this.messageUtil.getResponse("deny-expired")).setEphemeral(true).queue();
            return;
        }
        if (!expectedDiscordId.equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            String playerName;
            Player player = this.plugin.getServer().getPlayer(playerUuid).orElse(null);
            if (player == null && (playerName = this.pendingVerificationUsernames.get(playerUuid)) != null) {
                player = this.plugin.getServer().getPlayer(playerName).orElse(null);
            }
            if (player != null) {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.verification-denied", new String[0]);
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Verification denied", (TextColor)NamedTextColor.RED) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                this.plugin.getAuthManager().blockIpForName(player.getRemoteAddress().getAddress().getHostAddress(), player.getUsername(), -1);
            }
            this.pendingVerifications.remove(playerUuid);
            this.pendingVerificationUsernames.remove(playerUuid);
        }).schedule();
        event.reply(this.messageUtil.getResponse("deny-success")).setEphemeral(true).queue();
        event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
    }

    private void handleKickSelfButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 9, event);
        if (playerUuid == null) {
            return;
        }
        String discordId = event.getUser().getId();
        if (!this.deferEphemeral(event)) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            String username = this.plugin.getServer().getPlayer(playerUuid).map(p -> p.getUsername()).orElse(null);
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(playerUuid, username);
            User user = this.plugin.getAuthService().getUser(accountUuid);
            if (user == null || !discordId.equals(user.getDiscordId())) {
                event.getHook().sendMessage(this.messageUtil.getResponse("not-your-button")).queue();
                return;
            }
            this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
            Player player = this.plugin.getServer().getPlayer(playerUuid).orElse(null);
            if (player != null) {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Logged out", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
            }
            MessageEmbed embed = this.messageUtil.buildLogoutEmbed();
            event.getHook().sendMessageEmbeds(embed, new MessageEmbed[0]).queue();
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        }).schedule();
    }

    private void handleDurationButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 9, event);
        if (playerUuid == null) {
            return;
        }
        event.getUser().getId();
        String currentDuration = this.config.getString("session.duration", "1h");
        MessageEmbed embed = this.messageUtil.buildSessionDurationPickerEmbed(currentDuration);
        String sessionId = playerUuid.toString();
        Button noSave = Button.secondary("setduration:" + sessionId + ":0", "No Save").withEmoji(Emoji.fromUnicode(EmojiConfig.getLock(this.config)));
        Button min5 = Button.secondary("setduration:" + sessionId + ":5m", "5m").withEmoji(Emoji.fromUnicode(EmojiConfig.getClock(this.config)));
        Button min15 = Button.secondary("setduration:" + sessionId + ":15m", "15m");
        Button min30 = Button.secondary("setduration:" + sessionId + ":30m", "30m");
        Button hour1 = Button.primary("setduration:" + sessionId + ":1h", "1h").withEmoji(Emoji.fromUnicode(EmojiConfig.getHourGlass(this.config)));
        ((ReplyCallbackAction)event.replyEmbeds(embed, new MessageEmbed[0]).addActionRow(noSave, min5, min15, min30, hour1)).setEphemeral(true).queue();
    }

    private void handleUnlinkButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 7, event);
        if (playerUuid == null) {
            return;
        }
        event.getUser().getId();
        event.reply(this.messageUtil.getResponse("unlink-initiated")).setEphemeral(true).queue();
    }

    private void handleConfirmUnlinkButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 14, event);
        if (playerUuid == null) {
            return;
        }
        event.getUser().getId();
        event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        event.reply(this.messageUtil.getResponse("unlink-confirmed")).setEphemeral(true).queue();
    }

    private void handleDenyUnlinkButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 12, event);
        if (playerUuid == null) {
            return;
        }
        event.getUser().getId();
        event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        event.reply(this.messageUtil.getResponse("unlink-denied")).setEphemeral(true).queue();
    }

    private void handleSetPasswordButton(ButtonInteractionEvent event, String buttonId) {
        String discordId = buttonId.substring(13);
        if (!event.getUser().getId().equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        TextInput passwordInput = TextInput.create("password", "New Password", TextInputStyle.SHORT).setPlaceholder("Enter your password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        TextInput confirmInput = TextInput.create("confirm_password", "Confirm Password", TextInputStyle.SHORT).setPlaceholder("Confirm your password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        Modal modal = Modal.create("password_modal:" + discordId, "Set Password").addActionRow(passwordInput).addActionRow(confirmInput).build();
        event.replyModal(modal).queue();
    }

    private void handleToggleNotificationsButton(ButtonInteractionEvent event, String buttonId) {
        String discordId = buttonId.substring(21);
        if (!event.getUser().getId().equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        if (!this.deferEphemeral(event)) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            user.setJoinNotifications(!user.isJoinNotifications());
            this.plugin.getAuthService().getDatabase().updateUser(user);
            event.getHook().sendMessage(user.isJoinNotifications() ? this.messageUtil.getResponse("notifications-enabled") : this.messageUtil.getResponse("notifications-disabled")).queue();
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        }).schedule();
    }

    @Override
    public void sendJoinNotification(String discordId, String playerName, UUID playerUuid) {
        if (!this.enabled || this.jda == null) {
            this.logger.warn("[DISCORD-NOTIFY] Cannot send join notification - bot not enabled");
            return;
        }
        String sessionId = playerUuid.toString();
        boolean hasPassword = false;
        try {
            String username = this.plugin.getServer().getPlayer(playerUuid).map(p -> p.getUsername()).orElse(null);
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(playerUuid, username);
            User user2 = this.plugin.getAuthService().getUser(accountUuid);
            if (user2 != null && user2.getPassword() != null && !user2.getPassword().isEmpty()) {
                hasPassword = true;
            }
        }
        catch (Exception e) {
            this.logger.warn("[DISCORD-NOTIFY] Error checking password status: {}", (Object)e.getMessage());
        }
        MessageEmbed embed = this.messageUtil.buildServerJoinAlertEmbed(playerName, hasPassword);
        Button kickButton = Button.danger("deny_join:" + sessionId, "Logout").withEmoji(Emoji.fromUnicode(EmojiConfig.getProhibited(this.config)));
        Button passwordButton = Button.primary("set_password:" + discordId, hasPassword ? "Change Password" : "Set Password").withEmoji(Emoji.fromUnicode(EmojiConfig.getKey(this.config)));
        Button notificationsButton = Button.secondary("toggle_notifications:" + discordId, "Disable Notifications").withEmoji(Emoji.fromUnicode(EmojiConfig.getBellSlash(this.config)));
        this.plugin.debug("[DISCORD-NOTIFY] Sending join notification to Discord ID: {} for player: {}", discordId, playerName);
        this.jda.retrieveUserById(discordId).timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).queue(user -> {
            this.logger.debug("[DISCORD-NOTIFY] Retrieved Discord user, opening DM channel");
            user.openPrivateChannel().flatMap(channel -> (RestAction)((MessageCreateAction)channel.sendMessageEmbeds(embed, new MessageEmbed[0]).addActionRow(kickButton, passwordButton)).addActionRow(notificationsButton)).queue(success -> this.plugin.debug("[DISCORD-NOTIFY] Successfully sent join notification to {}", playerName), error -> this.logger.warn("[DISCORD-NOTIFY] Failed to send join notification DM to {} ({}): {}", playerName, discordId, error));
        }, (Throwable error) -> this.logger.warn("[DISCORD-NOTIFY] Failed to retrieve Discord user {}: {}", (Object)discordId, (Object)error.getMessage()));
    }

    private void handleDenyJoinButton(ButtonInteractionEvent event, String buttonId) {
        UUID playerUuid = this.parseSessionId(buttonId, 10, event);
        if (playerUuid == null) {
            return;
        }
        String discordId = event.getUser().getId();
        if (!this.deferEphemeral(event)) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            String username = this.plugin.getServer().getPlayer(playerUuid).map(p -> p.getUsername()).orElse(null);
            UUID accountUuid = this.plugin.getAuthManager().resolveAccountUuid(playerUuid, username);
            User user = this.plugin.getAuthService().getUser(accountUuid);
            if (user == null || !discordId.equals(user.getDiscordId())) {
                event.getHook().sendMessage(this.messageUtil.getResponse("not-your-button")).queue();
                return;
            }
            this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
            Player player = this.plugin.getServer().getPlayer(playerUuid).orElse(null);
            boolean wasOnline = false;
            if (player != null) {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.session-cleared", new String[0]);
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Sessions cleared", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                wasOnline = true;
            }
            MessageEmbed embed = this.messageUtil.buildSessionsClearedEmbed(wasOnline);
            event.getHook().sendMessageEmbeds(embed, new MessageEmbed[0]).queue();
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        }).schedule();
    }

    @Override
    public void assignLinkedRole(String discordId) {
        if (!this.enabled || this.jda == null) {
            return;
        }
        String linkedRoleId = this.config.getString("discord.linked-role-id", "");
        if (linkedRoleId.isEmpty()) {
            return;
        }
        for (String guildId : this.getConfiguredGuildIds()) {
            Role role;
            Guild guild;
            if (guildId == null || guildId.isBlank() || (guild = this.jda.getGuildById(guildId)) == null || (role = guild.getRoleById(linkedRoleId)) == null) continue;
            guild.retrieveMemberById(discordId).timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS).queue(member -> {
                if (!member.getRoles().contains(role)) {
                    guild.addRoleToMember((UserSnowflake)member, role).queue(success -> this.plugin.debug("[DISCORD] Assigned linked role to {} in guild {}", member.getUser().getAsTag(), guildId), (Throwable error) -> this.logger.warn("[DISCORD] Failed to assign linked role (guild {}): {}", (Object)guildId, (Object)error.getMessage()));
                }
            }, error -> {});
            return;
        }
    }

    @Override
    public CompletableFuture<Set<String>> getRoleIds(String discordId) {
        if (!this.enabled || this.jda == null || discordId == null || discordId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord bot is not available for role lookup"));
        }
        ArrayList<CompletableFuture<RoleLookupResult>> lookups = new ArrayList<>();
        for (String guildId : this.getConfiguredGuildIds()) {
            Guild guild;
            if (guildId == null || guildId.isBlank() || (guild = this.jda.getGuildById(guildId)) == null) {
                continue;
            }
            CompletableFuture<RoleLookupResult> lookup = guild.retrieveMemberById(discordId)
                    .timeout(this.config.getInt("discord.api.request-timeout", 10), TimeUnit.SECONDS)
                    .submit()
                    .thenApply(member -> {
                        Set<String> roleIds = new HashSet<>();
                        member.getRoles().forEach(role -> roleIds.add(role.getId()));
                        return RoleLookupResult.success(roleIds);
                    })
                    .exceptionally(error -> RoleLookupResult.failure(error));
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No configured Discord guilds are available for role lookup"));
        }
        return CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            HashSet<String> roleIds = new HashSet<>();
            boolean anySuccess = false;
            Throwable lastError = null;
            for (CompletableFuture<RoleLookupResult> lookup : lookups) {
                RoleLookupResult result = lookup.join();
                if (result.success) {
                    anySuccess = true;
                    roleIds.addAll(result.roleIds);
                } else if (result.error != null) {
                    lastError = result.error;
                }
            }
            if (!anySuccess) {
                throw new CompletionException(lastError != null
                        ? lastError
                        : new IllegalStateException("Discord role lookup failed for all configured guilds"));
            }
            return roleIds;
        });
    }

    private static final class RoleLookupResult {
        private final boolean success;
        private final Set<String> roleIds;
        private final Throwable error;

        private RoleLookupResult(boolean success, Set<String> roleIds, Throwable error) {
            this.success = success;
            this.roleIds = roleIds;
            this.error = error;
        }

        static RoleLookupResult success(Set<String> roleIds) {
            return new RoleLookupResult(true, roleIds != null ? roleIds : Collections.emptySet(), null);
        }

        static RoleLookupResult failure(Throwable error) {
            return new RoleLookupResult(false, Collections.emptySet(), error);
        }
    }

    private void updateBotStatus() {
        if (!this.enabled || this.jda == null) {
            return;
        }
        if (!this.config.getBoolean("discord.status.enabled", true)) {
            return;
        }
        try {
            int playerCount = this.plugin.getServer().getPlayerCount();
            String message = this.config.getString("discord.status.message", "{players} players online").replace("{players}", String.valueOf(playerCount));
            String activityType = this.config.getString("discord.status.activity-type", "WATCHING");
            Activity activity = switch (activityType.toUpperCase()) {
                case "PLAYING" -> Activity.playing(message);
                case "LISTENING" -> Activity.listening(message);
                case "COMPETING" -> Activity.competing(message);
                default -> Activity.watching(message);
            };
            this.jda.getPresence().setActivity(activity);
        }
        catch (Exception e) {
            this.logger.error("[DISCORD] Failed to update bot status", e);
        }
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        List<String> guildIds = this.getConfiguredGuildIds();
        if (!guildIds.contains(event.getGuild().getId())) {
            return;
        }
        if (this.plugin.getGroupSyncService() != null) {
            this.plugin.getGroupSyncService().syncDiscordUser(event.getUser().getId(), "discord role update");
        }
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        List<String> guildIds = this.getConfiguredGuildIds();
        if (!guildIds.contains(event.getGuild().getId())) {
            return;
        }
        if (this.plugin.getGroupSyncService() != null) {
            this.plugin.getGroupSyncService().syncDiscordUser(event.getUser().getId(), "discord role update");
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        String discordId = event.getUser().getId();
        List<String> guildIds = this.getConfiguredGuildIds();
        if (!guildIds.contains(event.getGuild().getId())) {
            return;
        }
        this.plugin.debug("[DISCORD] Member left Discord server: {} - checking for linked accounts", event.getUser().getAsTag());
        try {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                this.plugin.debug("[DISCORD] No linked account found for {}", event.getUser().getAsTag());
                return;
            }
            if (this.plugin.getGroupSyncService() != null) {
                UUID clearUuid = user.getUuid();
                Player online = this.plugin.getServer().getPlayer(user.getUuid()).orElse(null);
                if (online != null) {
                    clearUuid = online.getUniqueId();
                }
                this.plugin.getGroupSyncService().clearSyncedGroups(clearUuid, user.getUsername(), "discord member leave");
            }
            String authMethod = this.config.getString("authentication.method", "password");
            if (!authMethod.equalsIgnoreCase("discord") && !authMethod.equalsIgnoreCase("hybrid")) {
                this.plugin.debug("[DISCORD] Auth method does not require Discord unlink on leave; groups cleared if configured");
                return;
            }
            user.setDiscordId("unlinked_" + discordId);
            user.setAccountType("UNLINKED");
            this.plugin.getAuthService().getDatabase().updateUser(user);
            this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
            this.plugin.getServer().getPlayer(user.getUuid()).ifPresent(player -> this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.discord-left-server", new String[0]);
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"You left the Discord server", (TextColor)NamedTextColor.RED) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                this.plugin.getLogger().info("[DISCORD] Kicked {} - Discord account left server", (Object)player.getUsername());
            }).schedule());
            this.plugin.getLogger().info("[DISCORD] Cleared sessions for {} - Discord account {} left server (marked as unlinked, can re-auth after rejoining)", (Object)user.getUsername(), (Object)event.getUser().getAsTag());
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[DISCORD] Error handling member leave: {}", (Object)e.getMessage(), (Object)e);
        }
    }

    @Override
    public void shutdown() {
        if (this.jda != null) {
            this.plugin.debug("[DISCORD] Shutting down Discord bot...", new Object[0]);
            try {
                this.jda.getHttpClient().connectionPool().evictAll();
                ExecutorService executor = this.jda.getHttpClient().dispatcher().executorService();
                executor.shutdown();
                int shutdownTimeout = this.config.getInt("discord.shutdown-timeout", 5);
                if (!executor.awaitTermination(shutdownTimeout, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
                this.jda.shutdownNow();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.logger.warn("[DISCORD] JDA shutdown interrupted: {}", (Object)e.getMessage());
            }
            catch (Exception e) {
                this.logger.warn("[DISCORD] Error during JDA shutdown", e);
            }
            finally {
                this.jda = null;
                this.enabled = false;
            }
        }
    }

    private boolean deferEphemeral(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event) {
        return this.deferReply(event, true);
    }

    private boolean deferReply(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event, boolean ephemeral) {
        if (event.isAcknowledged()) {
            return true;
        }
        try {
            event.deferReply(ephemeral).complete();
            return true;
        }
        catch (Exception e) {
            this.logger.warn("[DISCORD] Failed to acknowledge interaction (expired or duplicate bot process?): {}", (Object)e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        if (!this.enabled || this.jda == null) {
            return false;
        }
        try {
            return this.jda.getStatus() == JDA.Status.CONNECTED;
        }
        catch (Exception e) {
            return false;
        }
    }

    @Override
    public DiscordUserSearchResult findUserByUsername(String username) {
        if (!this.enabled || this.jda == null) {
            return DiscordUserSearchResult.notFound();
        }
        try {
            String searchUsername = this.normalizeUsername(username);
            List<String> guildIds = this.getConfiguredGuildIds();
            boolean hasGuild = !guildIds.isEmpty();
            this.plugin.debug("[DISCORD] Searching for Discord username: {}", searchUsername);
            if (hasGuild) {
                for (String guildId : guildIds) {
                    DiscordUserSearchResult guildResult = this.searchInGuild(searchUsername, guildId);
                    if (!guildResult.found()) continue;
                    return guildResult;
                }
            }
            return this.searchInCachedUsers(searchUsername, hasGuild);
        }
        catch (Exception e) {
            this.logger.warn("[DISCORD] Error finding Discord user", e);
            return DiscordUserSearchResult.notFound();
        }
    }

    private String normalizeUsername(String username) {
        String normalized = username;
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase();
    }

    private DiscordUserSearchResult searchInGuild(String searchUsername, String serverId) {
        Guild guild = this.jda.getGuildById(serverId);
        if (guild == null) {
            return DiscordUserSearchResult.notFound();
        }
        List<Member> members = this.loadGuildMembers(guild);
        this.plugin.debug("[DISCORD] Searching through {} guild members", members.size());
        for (Member member : members) {
            if (!this.matchesUsername(member.getUser(), searchUsername)) continue;
            this.plugin.debug("[DISCORD] Found user in guild: {}", member.getUser().getId());
            return DiscordUserSearchResult.foundInGuild(member.getUser().getId());
        }
        return DiscordUserSearchResult.notFound();
    }

    private List<Member> loadGuildMembers(Guild guild) {
        List<Member> cachedMembers = guild.getMembers();
        if (cachedMembers.size() >= guild.getMemberCount() || cachedMembers.size() > this.config.getInt("discord.cache.member-threshold", 100)) {
            return cachedMembers;
        }
        try {
            CompletableFuture<List> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return guild.loadMembers().get();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return future.get(this.config.getInt("discord.api.member-load-timeout", 5), TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            this.plugin.debug("[DISCORD] Guild member loading timed out, using cached members ({})", cachedMembers.size());
            return cachedMembers;
        }
        catch (Exception e) {
            this.logger.warn("[DISCORD] Failed to load all guild members: {}", (Object)e.getMessage());
            return cachedMembers;
        }
    }

    private DiscordUserSearchResult searchInCachedUsers(String searchUsername, boolean hasGuild) {
        List<net.dv8tion.jda.api.entities.User> users = this.jda.getUsers();
        this.plugin.debug("[DISCORD] Searching through {} cached users", users.size());
        for (net.dv8tion.jda.api.entities.User user : users) {
            if (!this.matchesUsername(user, searchUsername)) continue;
            this.logger.info("[DISCORD] Found user in cache (not in guild): {}", (Object)user.getId());
            return hasGuild ? DiscordUserSearchResult.foundNotInGuild(user.getId()) : DiscordUserSearchResult.foundInGuild(user.getId());
        }
        this.logger.info("[DISCORD] Discord user not found anywhere: {}", (Object)searchUsername);
        return DiscordUserSearchResult.notFound();
    }

    private boolean matchesUsername(net.dv8tion.jda.api.entities.User user, String searchUsername) {
        String userUsername = user.getName().toLowerCase();
        String userGlobalName = user.getGlobalName();
        if (userUsername.equals(searchUsername)) {
            return true;
        }
        return userGlobalName != null && userGlobalName.toLowerCase().equals(searchUsername);
    }
}
