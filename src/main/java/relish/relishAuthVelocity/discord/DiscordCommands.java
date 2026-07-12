package relish.relishAuthVelocity.discord;

import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.discord.DiscordMessageUtil;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.utils.ColorConfig;
import relish.relishAuthVelocity.utils.DurationUtil;
import relish.relishAuthVelocity.utils.EmojiConfig;
import relish.relishAuthVelocity.utils.PasswordHasher;
import relish.relishAuthVelocity.utils.RandomPasswordGenerator;
import relish.relishAuthVelocity.validators.PasswordValidator;

public class DiscordCommands
extends ListenerAdapter {
    private final RelishAuthVelocity plugin;
    private final DiscordMessageUtil messageUtil;

    public DiscordCommands(RelishAuthVelocity plugin) {
        this.plugin = plugin;
        this.messageUtil = new DiscordMessageUtil(plugin);
    }

    public void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(Commands.slash("password", "Manage your account password").setGuildOnly(false), Commands.slash("notify", "Toggle join notifications").addOption(OptionType.STRING, "option", "on or off (leave empty to view current)", false).setGuildOnly(false), Commands.slash("logout", "Logout and clear all sessions").setGuildOnly(false), Commands.slash("session", "Manage your session duration").addOption(OptionType.STRING, "duration", "Session duration (use one of the configured presets)", false).setGuildOnly(false), Commands.slash("info", "Get player information").addOption(OptionType.STRING, "player", "Minecraft username or Discord @mention (leave empty for yourself)", false).setGuildOnly(false), Commands.slash("unlink", "Unlink a player's Discord account").addOption(OptionType.STRING, "player", "Minecraft username or Discord @mention (admin only)", false).setGuildOnly(false), Commands.slash("reload", "Reload plugin configuration").setGuildOnly(false), Commands.slash("block", "Block an IP from joining with a username").addOption(OptionType.STRING, "username", "Username to block", true).addOption(OptionType.STRING, "from", "Player name or IP address", true).setGuildOnly(false), Commands.slash("unblock", "Unblock an IP for a username").addOption(OptionType.STRING, "username", "Username to unblock", true).addOption(OptionType.STRING, "from", "Player name or IP address", true).setGuildOnly(false), Commands.slash("clearblocks", "Clear all IP blocks for a username").addOption(OptionType.STRING, "username", "Username to clear blocks for", true).setGuildOnly(false), Commands.slash("resetpassword", "Reset a player's password (generates a temporary one)").addOption(OptionType.STRING, "username", "Minecraft username", true).setGuildOnly(false), Commands.slash("setpassword", "Set a player's password (opens a modal)").addOption(OptionType.STRING, "username", "Minecraft username", true).setGuildOnly(false)).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        try {
            if (List.of("reload", "block", "unblock", "clearblocks", "resetpassword", "setpassword").contains(commandName)) {
                if (!this.isAdmin(event)) {
                    event.reply(this.messageUtil.getResponse("no-permission")).setEphemeral(true).queue();
                    return;
                }
                this.handleAdminCommand(event, commandName);
            } else {
                this.handlePlayerCommand(event, commandName);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[DISCORD-CMD] Error handling command {}: {}", commandName, e.getMessage(), e);
            event.reply("\u274c An error occurred while processing your command.").setEphemeral(true).queue();
        }
    }

    private void handlePlayerCommand(@NotNull SlashCommandInteractionEvent event, String commandName) {
        String discordId = event.getUser().getId();
        switch (commandName) {
            case "password": {
                this.handlePassword(event, discordId);
                break;
            }
            case "notify": {
                this.handleNotifications(event, discordId);
                break;
            }
            case "logout": {
                this.handleLogout(event, discordId);
                break;
            }
            case "session": {
                this.handleSession(event, discordId);
                break;
            }
            case "info": {
                this.handleInfo(event, discordId);
                break;
            }
            case "unlink": {
                this.handleUnlink(event, discordId);
            }
        }
    }

    private void handlePassword(SlashCommandInteractionEvent event, String discordId) {
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
            EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83d\udd12 Password Management").setDescription("Manage your account password for additional security").setColor(ColorConfig.getBlue(this.plugin.getConfig())).addField("Current Status", hasPassword ? "\ud83d\udd12 Password Set" : "\u274c No Password", true).addField("Account", "\ud83d\udc64 " + user.getUsername(), true).addField("\u2139\ufe0f Information", "Setting a password allows you to:\n\u2022 Login without Discord verification\n\u2022 Have a backup authentication method\n\u2022 Increase account security", false).setFooter("RelishAuth Security", null).setTimestamp(Instant.now());
            Button setPasswordButton = Button.primary("password_modal:" + discordId, hasPassword ? "Change Password" : "Set Password").withEmoji(Emoji.fromUnicode(EmojiConfig.getKey(this.plugin.getConfig())));
            ((WebhookMessageCreateAction)event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).addActionRow(setPasswordButton)).queue();
        }).schedule();
    }

    private void handleNotifications(SlashCommandInteractionEvent event, String discordId) {
        OptionMapping optionValue = event.getOption("option");
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            if (optionValue == null) {
                this.showNotificationStatus(event, user);
            } else {
                this.toggleNotifications(event, user, optionValue.getAsString());
            }
        }).schedule();
    }

    private void showNotificationStatus(SlashCommandInteractionEvent event, User user) {
        boolean enabled = user.isJoinNotifications();
        EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83d\udd14 Join Notifications").setDescription("Manage your server join notification settings").setColor(ColorConfig.getBlue(this.plugin.getConfig())).addField("Current Status", enabled ? "\u2705 **Enabled**" : "\u274c **Disabled**", false).addField("\u2139\ufe0f What are notifications?", "When enabled, you'll receive a Discord DM every time someone joins the server with your account.", false).addField("\u26a0\ufe0f Security", "This helps you detect unauthorized access to your account.", false).setFooter("Use the buttons below to toggle", null).setTimestamp(Instant.now());
        Button enableButton = Button.success("notify_enable:" + user.getDiscordId(), "Enable").withEmoji(Emoji.fromUnicode("\ud83d\udd14"));
        Button disableButton = Button.danger("notify_disable:" + user.getDiscordId(), "Disable").withEmoji(Emoji.fromUnicode(EmojiConfig.getBellSlash(this.plugin.getConfig())));
        ((WebhookMessageCreateAction)event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).addActionRow(enableButton, disableButton)).queue();
    }

    private void toggleNotifications(SlashCommandInteractionEvent event, User user, String option) {
        boolean newState;
        if (option.equalsIgnoreCase("on") || option.equalsIgnoreCase("enable") || option.equalsIgnoreCase("true")) {
            newState = true;
        } else if (option.equalsIgnoreCase("off") || option.equalsIgnoreCase("disable") || option.equalsIgnoreCase("false")) {
            newState = false;
        } else {
            event.getHook().sendMessage(this.messageUtil.getResponse("invalid-option")).queue();
            return;
        }
        user.setJoinNotifications(newState);
        this.plugin.getAuthService().getDatabase().updateUser(user);
        event.getHook().sendMessage(newState ? this.messageUtil.getResponse("notifications-enabled") : this.messageUtil.getResponse("notifications-disabled")).queue();
    }

    private void handleLogout(SlashCommandInteractionEvent event, String discordId) {
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
            Optional playerOpt = this.plugin.getServer().getPlayer(user.getUuid());
            if (playerOpt.isPresent()) {
                this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                    ((Player)playerOpt.get()).disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Logged out", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                }).delay(100L, TimeUnit.MILLISECONDS).schedule();
            }
            MessageEmbed embed = this.messageUtil.buildLogoutEmbed();
            Button sessionButton = Button.secondary("setduration:" + user.getUuid().toString() + ":0", "Turn Off Session Saving").withEmoji(Emoji.fromUnicode(EmojiConfig.getLock(this.plugin.getConfig())));
            ((WebhookMessageCreateAction)event.getHook().sendMessageEmbeds(embed, new MessageEmbed[0]).addActionRow(sessionButton)).queue();
        }).schedule();
    }

    private void handleSession(SlashCommandInteractionEvent event, String discordId) {
        OptionMapping durationOption = event.getOption("duration");
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            if (durationOption == null) {
                this.showSessionPicker(event, user);
            } else {
                this.setSessionDuration(event, user, durationOption.getAsString());
            }
        }).schedule();
    }

    private void showSessionPicker(SlashCommandInteractionEvent event, User user) {
        String currentDuration = this.plugin.getAuthService().getDatabase().getPlayerSessionDuration(user.getDiscordId());
        MessageEmbed embed = this.messageUtil.buildSessionDurationPickerEmbed(currentDuration);
        String sessionId = user.getUuid().toString();
        List<String> allowedDurations = this.plugin.getConfig().getStringList("session.available-durations");
        if (allowedDurations.isEmpty()) {
            allowedDurations = List.of("0", "5m", "15m", "30m", "1h");
        }
        List<Button> buttons = new ArrayList();
        for (String duration : allowedDurations) {
            if (!DurationUtil.isValidDuration(duration)) continue;
            String label = duration.equalsIgnoreCase("0") ? "No Save" : duration.toLowerCase(Locale.ROOT);
            Button button = Button.secondary("setduration:" + sessionId + ":" + duration, label);
            button = duration.equalsIgnoreCase("0") ? button.withEmoji(Emoji.fromUnicode(EmojiConfig.getLock(this.plugin.getConfig()))) : (duration.toLowerCase(Locale.ROOT).endsWith("h") || duration.toLowerCase(Locale.ROOT).endsWith("d") ? button.withEmoji(Emoji.fromUnicode(EmojiConfig.getHourGlass(this.plugin.getConfig()))) : button.withEmoji(Emoji.fromUnicode(EmojiConfig.getClock(this.plugin.getConfig()))));
            buttons.add(button);
        }
        if (buttons.size() > 25) {
            buttons = buttons.subList(0, 25);
        }
        ArrayList<ActionRow> rows = new ArrayList<ActionRow>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        event.getHook().sendMessage(((MessageCreateBuilder)((MessageCreateBuilder)new MessageCreateBuilder().setEmbeds(embed)).setComponents(rows)).build()).queue();
    }

    private void setSessionDuration(SlashCommandInteractionEvent event, User user, String duration) {
        List<String> allowedDurations = this.plugin.getConfig().getStringList("session.available-durations");
        if (allowedDurations.isEmpty()) {
            allowedDurations = List.of("0", "5m", "15m", "30m", "1h");
        }
        if (!DurationUtil.isValidDuration(duration) || !allowedDurations.contains(duration)) {
            event.getHook().sendMessage(this.messageUtil.getResponse("invalid-duration")).queue();
            return;
        }
        this.plugin.getAuthService().getDatabase().setPlayerSessionDuration(user.getDiscordId(), duration);
        MessageEmbed embed = this.messageUtil.buildDurationUpdatedEmbed(duration);
        event.getHook().sendMessageEmbeds(embed, new MessageEmbed[0]).queue();
    }

    private void handleInfo(SlashCommandInteractionEvent event, String discordId) {
        OptionMapping playerOption = event.getOption("player");
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = playerOption == null ? this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId) : (this.isAdmin(event) ? this.resolveUser(playerOption.getAsString()) : this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId));
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("player-not-found")).queue();
                return;
            }
            this.showPlayerInfo(event, user, this.isAdmin(event));
        }).schedule();
    }

    private void showPlayerInfo(SlashCommandInteractionEvent event, User user, boolean isAdmin) {
        String sessionDuration = this.plugin.getAuthService().getDatabase().getPlayerSessionDuration(user.getDiscordId());
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
        String passwordStatus = hasPassword ? "\ud83d\udd12 Password Set" : "\u274c No Password";
        String premiumStatus = "PREMIUM".equals(user.getAccountType()) ? "\u2728 Premium Account" : "\ud83d\udce6 Cracked Account";
        boolean isOnline = this.plugin.getServer().getPlayer(user.getUuid()).isPresent();
        String sessionStatus = isOnline ? "\ud83d\udfe2 Online" : "\u26ab Offline";
        String discordInfo = user.getDiscordId() != null ? "<@" + user.getDiscordId() + ">\n\ud83c\udd94 `" + user.getDiscordId() + "`" : "\u274c Not Linked";
        EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83d\udcca Account Information").setDescription((CharSequence)(isAdmin ? "Account details for **" + user.getUsername() + "**" : "Your RelishAuth account details")).setColor(ColorConfig.getBlue(this.plugin.getConfig())).addField("Minecraft Account", "\ud83d\udc64 **" + user.getUsername() + "**\n\ud83c\udd94 `" + String.valueOf(user.getUuid()) + "`", false).addField("Discord Account", discordInfo, false).addField("Security", passwordStatus + "\n" + premiumStatus, false).addField("Session", "\u23f1\ufe0f Duration: **" + this.formatDuration(sessionDuration) + "**\n" + sessionStatus, false).addField("Statistics", "\ud83d\udcc5 First Join: <t:" + user.getFirstLogin() / 1000L + ":R>\n\ud83d\udd04 Last Login: <t:" + user.getLastLogin() / 1000L + ":R>", false);
        if (isAdmin && user.getIpAddress() != null) {
            embed.addField("Admin Info", "\ud83c\udf10 Last IP: ||`" + user.getIpAddress() + "`||", false);
        }
        embed.setFooter("RelishAuth", null).setTimestamp(Instant.now());
        event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
    }

    private void handleUnlink(SlashCommandInteractionEvent event, String discordId) {
        OptionMapping playerOption = event.getOption("player");
        if (playerOption != null && this.isAdmin(event)) {
            this.handleUnlinkAdmin(event, playerOption.getAsString());
        } else {
            this.handleUnlinkSelf(event, discordId);
        }
    }

    private void handleUnlinkSelf(SlashCommandInteractionEvent event, String discordId) {
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            user.setDiscordId("unlinked_" + discordId);
            user.setAccountType("UNLINKED");
            this.plugin.getAuthService().getDatabase().updateUser(user);
            this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
            Optional playerOpt = this.plugin.getServer().getPlayer(user.getUuid());
            if (playerOpt.isPresent()) {
                this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.account-unlinked", new String[0]);
                    ((Player)playerOpt.get()).disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Account unlinked", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                }).delay(100L, TimeUnit.MILLISECONDS).schedule();
            }
            EmbedBuilder embed = new EmbedBuilder().setTitle("\u2705 Account Unlinked").setDescription("Your Discord account has been unlinked successfully.").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Status", "You can now link a different Discord account", false).setFooter("RelishAuth", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    private void handleUnlinkAdmin(SlashCommandInteractionEvent event, String targetPlayer) {
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.resolveUser(targetPlayer);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("player-not-found")).queue();
                return;
            }
            String oldDiscordId = user.getDiscordId();
            user.setDiscordId(null);
            user.setAccountType("UNLINKED");
            this.plugin.getAuthService().getDatabase().updateUser(user);
            this.plugin.getAuthService().getDatabase().clearAllSessions(oldDiscordId);
            Optional playerOpt = this.plugin.getServer().getPlayer(user.getUuid());
            if (playerOpt.isPresent()) {
                this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.admin-unlink", new String[0]);
                    ((Player)playerOpt.get()).disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Account unlinked by admin", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                }).delay(100L, TimeUnit.MILLISECONDS).schedule();
            }
            EmbedBuilder embed = new EmbedBuilder().setTitle("\u2705 Account Unlinked").setDescription("Successfully unlinked **" + user.getUsername() + "**'s Discord account").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Details", "Player can now link a different Discord account", false).addField("Admin", event.getUser().getAsMention(), true).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    private void handleAdminCommand(@NotNull SlashCommandInteractionEvent event, String commandName) {
        switch (commandName) {
            case "reload": {
                this.handleReload(event);
                break;
            }
            case "block": {
                this.handleBlockUsername(event);
                break;
            }
            case "unblock": {
                this.handleUnblockUsername(event);
                break;
            }
            case "clearblocks": {
                this.handleClearBlocksUsername(event);
                break;
            }
            case "resetpassword": {
                this.handleResetPassword(event);
                break;
            }
            case "setpassword": {
                this.handleSetPassword(event);
            }
        }
    }

    private void handleResetPassword(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply(this.messageUtil.getResponse("parameter-required").replace("{parameter}", "Username")).setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();
        event.deferReply(true).queue();
        String tempPassword = RandomPasswordGenerator.generateDefault();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                User user = this.plugin.getAuthService().getDatabase().getUserByUsername(minecraftUsername);
                if (user == null) {
                    event.getHook().sendMessage(this.messageUtil.getResponse("player-not-found")).queue();
                    return;
                }
                String hashedPassword = PasswordHasher.hash(tempPassword, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
                user.setPassword(hashedPassword);
                this.plugin.getAuthService().getDatabase().updateUser(user);
                this.plugin.getAuthService().removeSession(user.getUuid());
                this.plugin.getAuthManager().setAuthenticated(user.getUuid(), false);
                this.plugin.getServer().getPlayer(user.getUuid()).ifPresent(p -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                    p.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Password reset by admin. Please re-login.", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                });
                EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83d\udd11 Password Reset").setDescription("Temporary password generated for **" + user.getUsername() + "**").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Minecraft Username", "`" + user.getUsername() + "`", true).addField("Temporary Password", "||`" + tempPassword + "`||", false).addField("Note", "Share this password securely with the player.", false).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
                event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[DISCORD-ADMIN] Error resetting password for {}: {}", minecraftUsername, e.getMessage(), e);
                event.getHook().sendMessage("\u274c Error: " + e.getMessage()).queue();
            }
        }).schedule();
    }

    private void handleSetPassword(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply(this.messageUtil.getResponse("parameter-required").replace("{parameter}", "Username")).setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();
        String adminId = event.getUser().getId();
        TextInput passwordInput = TextInput.create("password", "New Password", TextInputStyle.SHORT).setPlaceholder("Enter the new password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        TextInput confirmInput = TextInput.create("confirm_password", "Confirm Password", TextInputStyle.SHORT).setPlaceholder("Confirm the new password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        Modal modal = Modal.create("admin_setpassword:" + adminId + ":" + minecraftUsername, "Set Password: " + minecraftUsername).addActionRow(passwordInput).addActionRow(confirmInput).build();
        event.replyModal(modal).queue();
    }

    private void handleClearBlocksUsername(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply(this.messageUtil.getResponse("parameter-required").replace("{parameter}", "Username")).setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            int cleared = this.plugin.getAuthManager().clearAllBlocksForUsername(minecraftUsername);
            EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83e\uddf9 Cleared Username Blocks").setDescription("Cleared **" + cleared + "** block(s) for this username").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Minecraft Username", "`" + minecraftUsername + "`", true).addField("Cleared By", event.getUser().getAsMention(), true).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    private void handleReload(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                boolean success = this.plugin.reloadConfig();
                if (success) {
                    if (this.plugin.getUpdateManager() != null && this.plugin.isUpdateCheckEnabled()) {
                        this.plugin.getUpdateManager().checkForPluginUpdates();
                    }
                    EmbedBuilder embed = new EmbedBuilder().setTitle("\u2705 Configuration Reloaded").setDescription("Plugin configuration has been reloaded successfully").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Status", "All settings updated", false).addField("Reloaded By", event.getUser().getAsMention(), true).setFooter("RelishAuth", null).setTimestamp(Instant.now());
                    event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
                } else {
                    event.getHook().sendMessage("\u274c Configuration reload failed. Check console for details.").queue();
                }
            }
            catch (Exception e) {
                event.getHook().sendMessage("\u274c Error: " + e.getMessage()).queue();
            }
        }).schedule();
    }

    private void handleBlockUsername(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply(this.messageUtil.getResponse("parameter-required").replace("{parameter}", "Username")).setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();
        OptionMapping fromOption = event.getOption("from");
        if (fromOption == null) {
            event.reply(this.messageUtil.getResponse("from-required")).setEphemeral(true).queue();
            return;
        }
        String fromInput = fromOption.getAsString();
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            String targetIp;
            String ipAddress = this.resolveIpAddress(fromInput);
            if (ipAddress == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("could-not-determine-ip").replace("{from}", fromInput)).queue();
                return;
            }
            this.plugin.getAuthManager().blockIpForName(ipAddress, minecraftUsername, -1);
            Optional targetPlayer = this.plugin.getServer().getPlayer(minecraftUsername);
            if (targetPlayer.isPresent() && ((Player)targetPlayer.get()).isActive() && ipAddress.equals(targetIp = ((Player)targetPlayer.get()).getRemoteAddress().getAddress().getHostAddress())) {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.ip-blocked-permanent", new String[0]);
                ((Player)targetPlayer.get()).disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"IP blocked", (TextColor)NamedTextColor.RED) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
            }
            EmbedBuilder embed = new EmbedBuilder().setTitle("\ud83d\udeab IP Blocked for Username").setDescription("Successfully blocked IP from joining with this username").setColor(ColorConfig.getRed(this.plugin.getConfig())).addField("Minecraft Username", "`" + minecraftUsername + "`", true).addField("From", "`" + fromInput + "`", true).addField("IP Address", "||`" + ipAddress + "`||", false).addField("Blocked By", event.getUser().getAsMention(), true).addField("Status", "This IP cannot join with username **" + minecraftUsername + "** anymore", false).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    private void handleUnblockUsername(SlashCommandInteractionEvent event) {
        OptionMapping usernameOption = event.getOption("username");
        if (usernameOption == null) {
            event.reply(this.messageUtil.getResponse("parameter-required").replace("{parameter}", "Username")).setEphemeral(true).queue();
            return;
        }
        String minecraftUsername = usernameOption.getAsString();
        OptionMapping fromOption = event.getOption("from");
        if (fromOption == null) {
            event.reply(this.messageUtil.getResponse("from-required")).setEphemeral(true).queue();
            return;
        }
        String fromInput = fromOption.getAsString();
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            String ipAddress = this.resolveIpAddress(fromInput);
            if (ipAddress == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("could-not-determine-ip").replace("{from}", fromInput)).queue();
                return;
            }
            boolean wasBlocked = this.plugin.getAuthManager().isIpBlockedForName(ipAddress, minecraftUsername);
            if (!wasBlocked) {
                EmbedBuilder embed = new EmbedBuilder().setTitle("\u2139\ufe0f Not Blocked").setDescription("This IP is not blocked for this username").setColor(ColorConfig.getBlue(this.plugin.getConfig())).addField("Minecraft Username", "`" + minecraftUsername + "`", true).addField("From", "`" + fromInput + "`", true).addField("IP Address", "||`" + ipAddress + "`||", false).addField("Status", "No action needed", false).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
                event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
                return;
            }
            this.plugin.getAuthManager().unblockIpForName(ipAddress, minecraftUsername);
            EmbedBuilder embed = new EmbedBuilder().setTitle("\u2705 IP Unblocked for Username").setDescription("Successfully unblocked IP for this username").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Minecraft Username", "`" + minecraftUsername + "`", true).addField("From", "`" + fromInput + "`", true).addField("IP Address", "||`" + ipAddress + "`||", false).addField("Unblocked By", event.getUser().getAsMention(), true).addField("Status", "This IP can now join with username **" + minecraftUsername + "**", false).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (modalId.startsWith("password_modal:")) {
            this.handleSelfPasswordModal(event);
            return;
        }
        if (modalId.startsWith("admin_setpassword:")) {
            this.handleAdminSetPasswordModal(event);
        }
    }

    private void handleSelfPasswordModal(@NotNull ModalInteractionEvent event) {
        String confirmPassword;
        String modalId = event.getModalId();
        String discordId = modalId.substring(15);
        if (!event.getUser().getId().equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        ModalMapping passwordValue = event.getValue("password");
        ModalMapping confirmPasswordValue = event.getValue("confirm_password");
        String password = passwordValue != null && passwordValue.getAsString() != null ? passwordValue.getAsString() : null;
        String string = confirmPassword = confirmPasswordValue != null && confirmPasswordValue.getAsString() != null ? confirmPasswordValue.getAsString() : null;
        if (password == null || confirmPassword == null || password.isEmpty() || confirmPassword.isEmpty()) {
            event.reply(this.messageUtil.getResponse("password-fields-required")).setEphemeral(true).queue();
            return;
        }
        if (!password.equals(confirmPassword)) {
            event.reply(this.messageUtil.getResponse("passwords-mismatch")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            PasswordValidator validator = new PasswordValidator(this.plugin.getConfig());
            PasswordValidator.ValidationResult validation = validator.validate(password, confirmPassword);
            if (!validation.isValid()) {
                event.getHook().sendMessage("\u274c " + validation.getMessage()).queue();
                return;
            }
            boolean hadPassword = user.getPassword() != null && !user.getPassword().isEmpty();
            String hashedPassword = PasswordHasher.hash(password, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
            user.setPassword(hashedPassword);
            this.plugin.getAuthService().getDatabase().updateUser(user);
            EmbedBuilder embed = new EmbedBuilder().setTitle(hadPassword ? "\u2705 Password Changed" : "\u2705 Password Set Successfully").setDescription(hadPassword ? "Your password has been updated successfully" : "Your password has been set and saved securely").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("\u2728 What's Next?", "You can now use password authentication in addition to Discord verification", false).setFooter("RelishAuth Security", null).setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
        }).schedule();
    }

    private void handleAdminSetPasswordModal(@NotNull ModalInteractionEvent event) {
        String confirmPassword;
        String[] parts = event.getModalId().split(":", 3);
        if (parts.length != 3) {
            event.reply(this.messageUtil.getResponse("invalid-request")).setEphemeral(true).queue();
            return;
        }
        String adminId = parts[1];
        String minecraftUsername = parts[2];
        if (!event.getUser().getId().equals(adminId)) {
            event.reply(this.messageUtil.getResponse("no-permission")).setEphemeral(true).queue();
            return;
        }
        if (event.getMember() == null || !this.isAdminMember(event.getMember())) {
            event.reply(this.messageUtil.getResponse("no-permission")).setEphemeral(true).queue();
            return;
        }
        ModalMapping passwordValue = event.getValue("password");
        ModalMapping confirmPasswordValue = event.getValue("confirm_password");
        String password = passwordValue != null && passwordValue.getAsString() != null ? passwordValue.getAsString() : null;
        String string = confirmPassword = confirmPasswordValue != null && confirmPasswordValue.getAsString() != null ? confirmPasswordValue.getAsString() : null;
        if (password == null || confirmPassword == null || password.isEmpty() || confirmPassword.isEmpty()) {
            event.reply(this.messageUtil.getResponse("password-fields-required")).setEphemeral(true).queue();
            return;
        }
        if (!password.equals(confirmPassword)) {
            event.reply(this.messageUtil.getResponse("passwords-mismatch")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                User user = this.plugin.getAuthService().getDatabase().getUserByUsername(minecraftUsername);
                if (user == null) {
                    event.getHook().sendMessage(this.messageUtil.getResponse("player-not-found")).queue();
                    return;
                }
                PasswordValidator validator = new PasswordValidator(this.plugin.getConfig());
                PasswordValidator.ValidationResult validation = validator.validate(password, confirmPassword);
                if (!validation.isValid()) {
                    event.getHook().sendMessage("\u274c " + validation.getMessage()).queue();
                    return;
                }
                String hashedPassword = PasswordHasher.hash(password, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
                user.setPassword(hashedPassword);
                this.plugin.getAuthService().getDatabase().updateUser(user);
                this.plugin.getAuthService().removeSession(user.getUuid());
                this.plugin.getAuthManager().setAuthenticated(user.getUuid(), false);
                this.plugin.getServer().getPlayer(user.getUuid()).ifPresent(p -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                    p.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Password changed by admin. Please re-login.", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                });
                EmbedBuilder embed = new EmbedBuilder().setTitle("\u2705 Password Updated").setDescription("Updated password for **" + user.getUsername() + "**").setColor(ColorConfig.getGreen(this.plugin.getConfig())).addField("Minecraft Username", "`" + user.getUsername() + "`", true).addField("Admin", event.getUser().getAsMention(), true).setFooter("RelishAuth Admin", null).setTimestamp(Instant.now());
                event.getHook().sendMessageEmbeds(embed.build(), new MessageEmbed[0]).queue();
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[DISCORD-ADMIN] Error setting password for {}: {}", minecraftUsername, e.getMessage(), e);
                event.getHook().sendMessage("\u274c Error: " + e.getMessage()).queue();
            }
        }).schedule();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (this.isButtonExpired(event)) {
            event.reply(this.messageUtil.getResponse("button-expired")).setEphemeral(true).queue(success -> event.getMessage().editMessageComponents(new LayoutComponent[0]).queue());
            return;
        }
        try {
            if (buttonId.startsWith("password_modal:")) {
                this.handlePasswordModal(event, buttonId);
            } else if (buttonId.startsWith("notify_enable:") || buttonId.startsWith("notify_disable:")) {
                this.handleNotifyButton(event, buttonId);
            } else if (buttonId.startsWith("setduration:")) {
                this.handleSetDurationButton(event, buttonId);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[DISCORD-BTN] Error handling button {}: {}", buttonId, e.getMessage(), e);
            event.reply("\u274c An error occurred.").setEphemeral(true).queue();
        }
    }

    private void handlePasswordModal(ButtonInteractionEvent event, String buttonId) {
        String discordId = buttonId.substring(15);
        if (!event.getUser().getId().equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        TextInput passwordInput = TextInput.create("password", "New Password", TextInputStyle.SHORT).setPlaceholder("Enter your password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        TextInput confirmInput = TextInput.create("confirm_password", "Confirm Password", TextInputStyle.SHORT).setPlaceholder("Confirm your password").setMinLength(6).setMaxLength(50).setRequired(true).build();
        Modal modal = Modal.create("password_modal:" + discordId, "Set Password").addActionRow(passwordInput).addActionRow(confirmInput).build();
        event.replyModal(modal).queue();
    }

    private void handleNotifyButton(ButtonInteractionEvent event, String buttonId) {
        boolean enable = buttonId.startsWith("notify_enable:");
        String discordId = buttonId.substring(enable ? 14 : 15);
        if (!event.getUser().getId().equals(discordId)) {
            event.reply(this.messageUtil.getResponse("not-your-button")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("no-account")).queue();
                return;
            }
            user.setJoinNotifications(enable);
            this.plugin.getAuthService().getDatabase().updateUser(user);
            event.getHook().sendMessage(enable ? this.messageUtil.getResponse("notifications-enabled") : this.messageUtil.getResponse("notifications-disabled")).queue();
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        }).schedule();
    }

    private void handleSetDurationButton(ButtonInteractionEvent event, String buttonId) {
        UUID uuid;
        String[] parts = buttonId.split(":");
        if (parts.length != 3) {
            event.reply(this.messageUtil.getResponse("invalid-request")).setEphemeral(true).queue();
            return;
        }
        String sessionId = parts[1];
        String durationKey = parts[2];
        List<String> allowedDurations = this.plugin.getConfig().getStringList("session.available-durations");
        if (allowedDurations.isEmpty()) {
            allowedDurations = List.of("0", "5m", "15m", "30m", "1h");
        }
        if (!DurationUtil.isValidDuration(durationKey) || !allowedDurations.contains(durationKey)) {
            event.reply(this.messageUtil.getResponse("invalid-duration")).setEphemeral(true).queue();
            return;
        }
        try {
            uuid = UUID.fromString(sessionId);
        }
        catch (IllegalArgumentException e) {
            event.reply(this.messageUtil.getResponse("invalid-session")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            User user = this.plugin.getAuthService().getUser(uuid);
            if (user == null) {
                event.getHook().sendMessage(this.messageUtil.getResponse("player-data-not-found")).queue();
                return;
            }
            if (user.getDiscordId() != null && !user.getDiscordId().equals(event.getUser().getId())) {
                event.getHook().sendMessage(this.messageUtil.getResponse("not-your-button")).queue();
                return;
            }
            this.plugin.getAuthService().getDatabase().setPlayerSessionDuration(user.getDiscordId(), durationKey);
            MessageEmbed embed = this.messageUtil.buildDurationUpdatedEmbed(durationKey);
            event.getHook().sendMessageEmbeds(embed, new MessageEmbed[0]).queue();
            event.getMessage().editMessageComponents(new LayoutComponent[0]).queue();
        }).schedule();
    }

    private boolean isButtonExpired(@NotNull ButtonInteractionEvent event) {
        int expirationMinutes;
        OffsetDateTime now;
        OffsetDateTime messageTime = event.getMessage().getTimeCreated();
        long minutesOld = Duration.between(messageTime, now = OffsetDateTime.now()).toMinutes();
        return minutesOld >= (long)(expirationMinutes = this.plugin.getConfig().getInt("discord.button-expiration-minutes", 5));
    }

    private boolean isAdmin(SlashCommandInteractionEvent event) {
        return event.getMember() != null && this.isAdminMember(event.getMember());
    }

    private boolean isAdminMember(Member member) {
        String adminRoleId = this.plugin.getConfig().getString("discord.admin-role-id", "");
        if (adminRoleId.isEmpty() || adminRoleId.equals("YOUR_ADMIN_ROLE_ID")) {
            return member.hasPermission(Permission.ADMINISTRATOR);
        }
        return member.getRoles().stream().anyMatch(role -> role.getId().equals(adminRoleId));
    }

    private String resolveIpAddress(String input) {
        if (input.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            return input;
        }
        Optional player = this.plugin.getServer().getPlayer(input);
        if (player.isPresent()) {
            return ((Player)player.get()).getRemoteAddress().getAddress().getHostAddress();
        }
        User user = this.plugin.getAuthService().getUserByUsername(input);
        if (user != null && user.getIpAddress() != null) {
            return user.getIpAddress();
        }
        return null;
    }

    private User resolveUser(String input) {
        if ((input = input.replaceAll("[<@!>]", "")).matches("\\d{17,19}")) {
            return this.plugin.getAuthService().getDatabase().getUserByDiscordId(input);
        }
        Optional player = this.plugin.getServer().getPlayer(input);
        if (player.isPresent()) {
            return this.plugin.getAuthService().getUser(((Player)player.get()).getUniqueId());
        }
        return this.plugin.getAuthService().getUserByUsername(input);
    }

    private String formatDuration(String duration) {
        return DurationUtil.formatDuration(duration);
    }
}
