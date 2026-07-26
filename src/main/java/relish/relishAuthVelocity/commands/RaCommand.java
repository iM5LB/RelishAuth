package relish.relishAuthVelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.BuildableComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import relish.relishAuthVelocity.BuildConstants;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.auth.AuthenticationManager;
import relish.relishAuthVelocity.exceptions.DatabaseException;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.utils.DurationUtil;
import relish.relishAuthVelocity.utils.MessageManager;
import relish.relishAuthVelocity.utils.PasswordHasher;
import relish.relishAuthVelocity.utils.RandomPasswordGenerator;
import relish.relishAuthVelocity.utils.ValidationUtil;
import relish.relishAuthVelocity.validators.PasswordValidator;

public class RaCommand
implements SimpleCommand {
    private final RelishAuthVelocity plugin;
    private static final String PERM_ADMIN = "relishauth.admin";
    private static final String PERM_WILDCARD = "relishauth.*";
    private static final String LEGACY_PERM_ADMIN = "relishAuth.admin";
    private static final String LEGACY_PERM_WILDCARD = "relishAuth.*";

    public RaCommand(RelishAuthVelocity plugin) {
        this.plugin = plugin;
    }

    public void execute(SimpleCommand.Invocation invocation) {
        String[] args = (String[])invocation.arguments();
        if (args.length == 0) {
            CommandSource commandSource = invocation.source();
            if (commandSource instanceof Player) {
                Player player = (Player)commandSource;
                this.sendHelp(player);
            } else {
                invocation.source().sendMessage((Component)Component.text((String)"Available commands: reload, info", (TextColor)NamedTextColor.GRAY));
            }
            return;
        }
        String subCommand = args[0].toLowerCase();
        Object object = invocation.source();
        if (!(object instanceof Player)) {
            if (subCommand.equals("reload")) {
                this.handleReloadConsole(invocation.source());
                return;
            }
            if (subCommand.equals("info")) {
                this.handleInfoConsole(invocation.source());
                return;
            }
            invocation.source().sendMessage((Component)Component.text((String)"Only players can use this command", (TextColor)NamedTextColor.RED));
            return;
        }
        Player player = (Player)object;
        if (!this.plugin.isInitialized()) {
            player.sendMessage(this.msg("error-plugin-not-initialized"));
            return;
        }
        try {
            switch (subCommand) {
                case "help": {
                    this.sendHelp(player);
                    break;
                }
                case "password": {
                    this.handlePassword(player, args);
                    break;
                }
                case "logout": {
                    this.handleLogout(player);
                    break;
                }
                case "discord": {
                    this.handleDiscordLink(player, args);
                    break;
                }
                case "notifications": 
                case "notify": {
                    this.handleNotifications(player, args);
                    break;
                }
                case "session": {
                    this.handleSession(player, args);
                    break;
                }
                case "syncgroups": {
                    this.handleSyncGroups(player, args);
                    break;
                }
                case "unlink": {
                    this.handleUnlink(player, args);
                    break;
                }
                case "info": {
                    this.handleInfo(player);
                    break;
                }
                case "reload": {
                    this.handleReload(player);
                    break;
                }
                case "block": {
                    this.handleBlockUsername(player, args);
                    break;
                }
                case "unblock": {
                    this.handleUnblockUsername(player, args);
                    break;
                }
                case "clearblocks": {
                    this.handleClearBlocks(player, args);
                    break;
                }
                case "setpassword": {
                    this.handleAdminSetPassword(player, args);
                    break;
                }
                case "resetpassword": {
                    this.handleAdminResetPassword(player, args);
                    break;
                }
                default: {
                    this.sendHelp(player);
                    break;
                }
            }
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[COMMAND] Database error in /{} for {}: {}", subCommand, player.getUsername(), e.getMessage());
            player.sendMessage(this.msg("error-database"));
        }
        catch (PluginException e) {
            this.plugin.getLogger().error("[COMMAND] Plugin error in /{} for {}: {} ({})", subCommand, player.getUsername(), e.getMessage(), e.getErrorCode().getCode());
            player.sendMessage(this.getMessageManager().getMessage("error-occurred", "{message}", e.getMessage()));
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[COMMAND] Unexpected error in /{} for {}: {}", subCommand, player.getUsername(), e.getMessage(), e);
            player.sendMessage(this.msg("error-unexpected"));
        }
    }

    private void handlePassword(Player player, String[] args) {
        if (!this.checkAuthenticated(player)) {
            return;
        }
        if (args.length != 3) {
            player.sendMessage(this.msg("cmd-password-usage"));
            return;
        }
        String newPassword = args[1];
        String confirmPassword = args[2];
        if (!newPassword.equals(confirmPassword)) {
            player.sendMessage(this.msg("cmd-password-mismatch"));
            return;
        }
        PasswordValidator validator = new PasswordValidator(this.plugin.getConfig());
        PasswordValidator.ValidationResult validation = validator.validate(newPassword, confirmPassword);
        if (!validation.isValid()) {
            player.sendMessage(this.getMessageManager().getMessage("password-validation-failed", "{message}", validation.getMessage()));
            return;
        }
        User user = this.plugin.getAuthService().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(this.msg("cmd-account-data-not-found"));
            return;
        }
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
        String hashedPassword = PasswordHasher.hash(newPassword, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
        user.setPassword(hashedPassword);
        this.plugin.getAuthService().getDatabase().updateUser(user);
        if (hasPassword) {
            player.sendMessage(this.msg("cmd-password-changed"));
        } else {
            player.sendMessage(this.msg("cmd-password-set"));
        }
        this.plugin.debug("Password {} for {}", hasPassword ? "changed" : "set", player.getUsername());
    }

    private void handleLogout(Player player) {
        if (!this.checkAuthenticated(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        User user = this.plugin.getAuthService().getUser(uuid);
        if (user != null && user.getDiscordId() != null) {
            this.plugin.getAuthService().getDatabase().clearAllSessions(user.getDiscordId());
        } else {
            this.plugin.getAuthService().getDatabase().deleteSession(uuid);
        }
        int delay = this.plugin.getConfig().getInt("commands.logout.disconnect-delay", 100);
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                List<Component> kickMsg = this.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Logged out", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("Error disconnecting player during logout: {}", (Object)e.getMessage());
            }
        }).delay((long)delay, TimeUnit.MILLISECONDS).schedule();
        this.plugin.debug("Logout initiated for {}", player.getUsername());
    }

    private void handleDiscordLink(Player player, String[] args) {
        if (!this.checkAuthenticated(player)) {
            return;
        }
        if (this.plugin.getDiscordBot() == null || !this.plugin.getDiscordBot().isEnabled()) {
            player.sendMessage(this.msg("discord-bot-not-configured"));
            return;
        }
        User user = this.plugin.getAuthService().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(this.msg("cmd-account-data-not-found"));
            return;
        }
        if (this.isRealDiscordId(user.getDiscordId())) {
            player.sendMessage(this.msg("cmd-discord-already-linked"));
            return;
        }
        if (args.length < 2) {
            for (Component line : this.getMessageManager().getMessageList("cmd-discord-link-usage", new String[0])) {
                player.sendMessage(line);
            }
            return;
        }
        String discordUsername = args[1];
        player.sendMessage(this.msg("looking-up-discord"));
        this.plugin.debug("Discord link requested for {} with Discord username {}", player.getUsername(), discordUsername);
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            DiscordUserSearchResult searchResult = this.plugin.getDiscordBot().findUserByUsername(discordUsername);
            if (searchResult == null || !searchResult.found()) {
                player.sendMessage(this.msg("discord-username-not-found-general"));
                player.sendMessage(this.msg("discord-use-username-not-display"));
                return;
            }
            if (!searchResult.inGuild()) {
                player.sendMessage(this.msg("discord-not-in-server"));
                player.sendMessage(this.msg("discord-must-join-authenticate"));
                return;
            }
            String discordId = searchResult.userId();
            boolean linked = this.plugin.getAuthService().linkDiscordToUser(player.getUniqueId(), discordId);
            if (!linked) {
                player.sendMessage(this.msg("already-linked"));
                return;
            }
            if (this.plugin.getDiscordBot() != null) {
                this.plugin.getDiscordBot().assignLinkedRole(discordId);
            }
            if (this.plugin.getGroupSyncService() != null) {
                this.plugin.getGroupSyncService().syncPlayer(player, "discord link");
            }
            player.sendMessage(this.msg("discord-linked-success"));
            this.plugin.debug("[DISCORD-LINK] {} linked Discord account {}", player.getUsername(), discordId);
        }).schedule();
    }

    private void handleNotifications(Player player, String[] args) {
        boolean newState;
        if (!this.checkAuthenticated(player)) {
            return;
        }
        User user = this.plugin.getAuthService().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(this.msg("cmd-not-registered"));
            return;
        }
        if (!this.isRealDiscordId(user.getDiscordId())) {
            player.sendMessage(this.msg("cmd-discord-not-linked-notify"));
            player.sendMessage(this.msg("cmd-discord-link-prompt"));
            return;
        }
        if (args.length < 2) {
            boolean enabled = user.isJoinNotifications();
            for (Component line : this.getMessageManager().getMessageList("cmd-notifications-header", new String[0])) {
                player.sendMessage(line);
            }
            String status = enabled ? this.getMessageManager().getRawMessage("cmd-notifications-status-on") : this.getMessageManager().getRawMessage("cmd-notifications-status-off");
            player.sendMessage(this.getMessageManager().getMessage("cmd-notifications-current", "{status}", status));
            player.sendMessage((Component)Component.empty());
            player.sendMessage(this.msg("cmd-notifications-usage"));
            player.sendMessage((Component)Component.empty());
            return;
        }
        String option = args[1].toLowerCase();
        if (option.equals("on") || option.equals("true") || option.equals("enable")) {
            newState = true;
        } else if (option.equals("off") || option.equals("false") || option.equals("disable")) {
            newState = false;
        } else {
            player.sendMessage(this.msg("cmd-notifications-invalid"));
            return;
        }
        user.setJoinNotifications(newState);
        this.plugin.getAuthService().getDatabase().updateUser(user);
        player.sendMessage(this.msg(newState ? "cmd-notifications-enabled" : "cmd-notifications-disabled"));
        this.plugin.debug("Notifications {} for {}", newState ? "enabled" : "disabled", player.getUsername());
    }

    private void handleSession(Player player, String[] args) {
        if (!this.checkAuthenticated(player)) {
            return;
        }
        User user = this.plugin.getAuthService().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(this.msg("cmd-not-registered"));
            return;
        }
        String discordId = user.getDiscordId();
        String currentDuration = this.plugin.getAuthService().getDatabase().getPlayerSessionDuration(discordId);
        if (args.length < 2) {
            for (Component line : this.getMessageManager().getMessageList("cmd-session-header", new String[0])) {
                player.sendMessage(line);
            }
            player.sendMessage(this.getMessageManager().getMessage("cmd-session-current", "{duration}", this.formatDuration(currentDuration)));
            player.sendMessage((Component)Component.empty());
            player.sendMessage(this.msg("cmd-session-select"));
            player.sendMessage((Component)Component.empty());
            for (String value : this.getAvailableSessionDurations()) {
                String label = this.formatDuration(value);
                String desc = value.equals(currentDuration) ? "Current" : "Click to select";
                Component button = Component.text()
                    .append(MessageManager.parseColors("<#87CEEB>\u25b6 <#FFFFFF>" + label + " <#808080>(" + desc + ")"))
                    .clickEvent(ClickEvent.runCommand("/ra session " + value))
                    .hoverEvent(HoverEvent.showText(MessageManager.parseColors("<#A0A0A0>Click to set to " + label)))
                    .build();
                player.sendMessage(button);
            }
            player.sendMessage((Component)Component.empty());
            return;
        }
        String duration = args[1].toLowerCase();
        if (!this.isValidDuration(duration) || !this.getAvailableSessionDurations().contains(duration)) {
            player.sendMessage(this.msg("cmd-session-invalid"));
            player.sendMessage(this.msg("cmd-session-valid-options"));
            return;
        }
        this.plugin.getAuthService().getDatabase().setPlayerSessionDuration(discordId, duration);
        player.sendMessage(this.getMessageManager().getMessage("cmd-session-updated", "{duration}", this.formatDuration(duration)));
        this.plugin.debug("Session duration set to {} for {}", duration, player.getUsername());
    }

    private void handleUnlink(Player player, String[] args) {
        if (args.length > 1) {
            if (!this.isAdmin(player)) {
                player.sendMessage(this.msg("cmd-no-permission"));
                return;
            }
            this.unlinkPlayerAdmin(player, args[1]);
            return;
        }
        if (!this.checkAuthenticated(player)) {
            return;
        }
        User user = this.plugin.getAuthService().resolveUserForPlayer(player);
        if (user == null || !this.isRealDiscordId(user.getDiscordId())) {
            player.sendMessage(this.msg("unlink-not-linked"));
            return;
        }
        this.unlinkPlayerSelf(player, user);
    }

    private void unlinkPlayerSelf(Player player, User user) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                String discordId = user.getDiscordId();
                this.plugin.getAuthService().unlinkDiscord(user.getUuid(), discordId);
                try {
                    if (this.plugin.getGroupSyncService() != null) {
                        this.plugin.getGroupSyncService().clearSyncedGroups(player.getUniqueId(), player.getUsername(), "self unlink");
                    }
                } catch (Exception groupErr) {
                    this.plugin.getLogger().warn("[AUTH] Group clear after unlink failed for {}: {}", player.getUsername(), groupErr.getMessage());
                }
                player.sendMessage(this.msg("unlink-success"));
                this.plugin.getLogger().info("[AUTH] {} unlinked Discord account {}", (Object)player.getUsername(), (Object)discordId);
                this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
                    List<Component> kickMsg = this.getMessageManager().getMessageList("kick.account-unlinked", new String[0]);
                    player.disconnect((Component)(kickMsg.isEmpty()
                            ? Component.text((String)"Account unlinked", (TextColor)NamedTextColor.YELLOW)
                            : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                }).delay(100L, TimeUnit.MILLISECONDS).schedule();
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH] Error unlinking Discord for {}: {}", player.getUsername(), e.getMessage(), e);
                player.sendMessage(this.msg("error-unlink-player"));
            }
        }).schedule();
    }

    private void unlinkPlayerAdmin(Player sender, String targetName) {
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                User user = null;
                Player online = null;
                String lookup = targetName;
                for (Player p : this.plugin.getServer().getAllPlayers()) {
                    if (!p.getUsername().equalsIgnoreCase(targetName)
                            && !AuthenticationManager.stripFloodgatePrefix(p.getUsername()).equalsIgnoreCase(targetName)
                            && !(p.getUsername().startsWith(".") && p.getUsername().substring(1).equalsIgnoreCase(targetName))) {
                        continue;
                    }
                    online = p;
                    user = this.plugin.getAuthService().resolveUserForPlayer(p);
                    break;
                }
                if (user == null) {
                    user = this.plugin.getAuthService().getUserByUsername(lookup);
                }
                if (user == null) {
                    String stripped = AuthenticationManager.stripFloodgatePrefix(lookup);
                    if (!stripped.equals(lookup)) {
                        user = this.plugin.getAuthService().getUserByUsername(stripped);
                    } else {
                        user = this.plugin.getAuthService().getUserByUsername("." + lookup);
                    }
                }
                if (user == null) {
                    sender.sendMessage(this.getMessageManager().getMessage("cmd-player-not-found", "{player}", targetName));
                    return;
                }
                if (!this.isRealDiscordId(user.getDiscordId())) {
                    sender.sendMessage(this.msg("cmd-player-not-linked"));
                    return;
                }
                String discordId = user.getDiscordId();
                this.plugin.getAuthService().getDatabase().unlinkDiscord(user.getUuid(), null);
                this.plugin.getAuthService().getDatabase().clearAllSessions(discordId);
                if (this.plugin.getConfig().getBoolean("authentication.clear-password-on-discord-unlink", true)) {
                    this.plugin.getAuthService().getDatabase().clearPassword(user.getUuid());
                }
                try {
                    if (this.plugin.getGroupSyncService() != null) {
                        UUID clearUuid = online != null ? online.getUniqueId() : user.getUuid();
                        this.plugin.getGroupSyncService().clearSyncedGroups(clearUuid, user.getUsername(), "admin unlink");
                    }
                } catch (Exception groupErr) {
                    this.plugin.getLogger().warn("[ADMIN] Group clear after unlink failed for {}: {}", targetName, groupErr.getMessage());
                }
                Player target = online != null ? online : this.plugin.getServer().getPlayer(user.getUuid()).orElse(null);
                if (target != null) {
                    List<Component> kickMsg = this.getMessageManager().getMessageList("kick.admin-unlink", new String[0]);
                    target.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Account unlinked by admin", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                }
                sender.sendMessage(this.getMessageManager().getMessage("cmd-player-unlinked", "{player}", targetName));
                this.plugin.getLogger().info("[ADMIN] {} unlinked Discord from {}", (Object)sender.getUsername(), (Object)targetName);
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[ADMIN] Error unlinking player {}: {}", targetName, e.getMessage(), e);
                sender.sendMessage(this.msg("error-unlink-player"));
            }
        }).schedule();
    }

    private void handleInfo(Player player) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        player.sendMessage(this.getMessageManager().getMessage("info-header", "{version}", BuildConstants.VERSION));
        player.sendMessage((Component)Component.empty());
        if (this.plugin.getUpdateManager() != null) {
            player.sendMessage(this.msg("update-checking"));
            this.plugin.getUpdateManager().checkForPluginUpdates().thenAccept(updateInfo -> {
                if (updateInfo.isUpdateAvailable()) {
                    player.sendMessage(this.getMessageManager().getMessage("info-update-available", "{version}", updateInfo.getLatestVersion()));
                    if (updateInfo.getDownloadUrl() != null) {
                        Component downloadLink = ((TextComponent)Component.text((String)updateInfo.getDownloadUrl(), (TextColor)NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl((String)updateInfo.getDownloadUrl()))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)Component.text((String)"Click to download", (TextColor)NamedTextColor.GRAY)));
                        player.sendMessage(this.getMessageManager().getMessage("info-update-download", "{url}", "").append(downloadLink));
                    }
                } else {
                    player.sendMessage(this.msg("info-latest-version"));
                }
            });
        } else {
            player.sendMessage(this.msg("info-latest-version"));
        }
        player.sendMessage((Component)Component.empty());
        if (this.plugin.isDebugEnabled()) {
            player.sendMessage((Component)Component.text((String)"Debug Mode: Enabled", (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Admin Status: " + (this.isAdmin(player) ? "Yes" : "No")), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Admin Players Config: " + String.valueOf(this.plugin.getConfig().getStringList("admin-players"))), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Has relishauth.admin: " + player.hasPermission(PERM_ADMIN)), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Has relishauth.*: " + player.hasPermission(PERM_WILDCARD)), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Has legacy relishAuth.admin: " + player.hasPermission(LEGACY_PERM_ADMIN)), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Has legacy relishAuth.*: " + player.hasPermission(LEGACY_PERM_WILDCARD)), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Database: " + (this.plugin.getDatabase() != null && this.plugin.getDatabase().isHealthy() ? "Connected" : "Disconnected")), (TextColor)NamedTextColor.GRAY));
            player.sendMessage((Component)Component.text((String)("Discord Bot: " + (this.plugin.getDiscordBot() != null && this.plugin.getDiscordBot().isEnabled() ? "Connected" : "Disconnected")), (TextColor)NamedTextColor.GRAY));
        }
    }

    private void handleSyncGroups(Player player, String[] args) {
        if (this.plugin.getGroupSyncService() == null || !this.plugin.getGroupSyncService().isEnabled()) {
            player.sendMessage(this.msg("cmd-syncgroups-disabled"));
            return;
        }
        Player target = player;
        if (args.length >= 2) {
            if (!this.isAdmin(player)) {
                player.sendMessage(this.msg("cmd-no-permission"));
                return;
            }
            target = this.plugin.getServer().getPlayer(args[1]).orElse(null);
            if (target == null) {
                player.sendMessage(this.msg("cmd-syncgroups-player-not-found"));
                return;
            }
        }
        if (target.equals((Object)player) && !this.checkAuthenticated(player)) {
            return;
        }
        this.plugin.getGroupSyncService().syncPlayer(target, "manual command");
        player.sendMessage(this.getMessageManager().getMessage("cmd-syncgroups-queued", "{player}", target.getUsername()));
    }

    private void handleReload(Player player) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        try {
            boolean success;
            if (this.plugin.getUpdateManager() != null) {
                player.sendMessage((Component)Component.text((String)"Checking for config updates...", (TextColor)NamedTextColor.GRAY));
                this.plugin.getUpdateManager().updateConfigurationFiles();
            }
            if (success = this.plugin.reloadConfig()) {
                player.sendMessage(this.msg("cmd-config-reloaded"));
                this.plugin.getLogger().info("[ADMIN] Configuration reloaded by {}", (Object)player.getUsername());
                if (this.plugin.getUpdateManager() != null && this.plugin.isUpdateCheckEnabled()) {
                    this.plugin.getUpdateManager().checkForPluginUpdates();
                }
            } else {
                player.sendMessage(this.msg("error-config-reload"));
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[ADMIN] Config reload failed: {}", (Object)e.getMessage(), (Object)e);
            player.sendMessage(this.getMessageManager().getMessage("cmd-config-reload-failed", "{error}", e.getMessage()));
        }
    }

    private void handleReloadConsole(CommandSource source) {
        if (!this.isAdmin(source)) {
            source.sendMessage((Component)Component.text((String)"No permission", (TextColor)NamedTextColor.RED));
            return;
        }
        try {
            boolean success;
            if (this.plugin.getUpdateManager() != null) {
                source.sendMessage((Component)Component.text((String)"Checking for config updates...", (TextColor)NamedTextColor.GRAY));
                this.plugin.getUpdateManager().updateConfigurationFiles();
            }
            if (success = this.plugin.reloadConfig()) {
                source.sendMessage((Component)Component.text((String)"Configuration reloaded successfully", (TextColor)NamedTextColor.GREEN));
                this.plugin.getLogger().info("[ADMIN] Configuration reloaded from console");
                if (this.plugin.getUpdateManager() != null && this.plugin.isUpdateCheckEnabled()) {
                    this.plugin.getUpdateManager().checkForPluginUpdates();
                }
            } else {
                source.sendMessage((Component)Component.text((String)"Failed to reload configuration", (TextColor)NamedTextColor.RED));
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[ADMIN] Config reload failed: {}", (Object)e.getMessage(), (Object)e);
            source.sendMessage((Component)Component.text((String)("Reload failed: " + e.getMessage()), (TextColor)NamedTextColor.RED));
        }
    }

    private void handleBlockUsername(Player player, String[] args) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(this.msg("cmd-block-usage"));
            return;
        }
        String username = args[1];
        String from = args[2];
        String ip = this.resolveIpAddress(from);
        if (ip == null) {
            player.sendMessage(this.getMessageManager().getMessage("cmd-block-ip-not-found", "{from}", from));
            return;
        }
        this.plugin.getAuthManager().blockIpForName(ip, username, -1);
        player.sendMessage(this.getMessageManager().getMessage("cmd-block-success", "{ip}", ip, "{username}", username));
        this.plugin.getServer().getPlayer(username).ifPresent(target -> {
            String targetIp = target.getRemoteAddress().getAddress().getHostAddress();
            if (ip.equals(targetIp)) {
                List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.ip-blocked-permanent", new String[0]);
                target.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"IP blocked", (TextColor)NamedTextColor.RED) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
            }
        });
    }

    private void handleUnblockUsername(Player player, String[] args) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(this.msg("cmd-unblock-usage"));
            return;
        }
        String username = args[1];
        String from = args[2];
        String ip = this.resolveIpAddress(from);
        if (ip == null) {
            player.sendMessage(this.getMessageManager().getMessage("cmd-unblock-ip-not-found", "{from}", from));
            return;
        }
        if (!this.plugin.getAuthManager().isIpBlockedForName(ip, username)) {
            player.sendMessage(this.msg("cmd-unblock-not-blocked"));
            return;
        }
        boolean removed = this.plugin.getAuthManager().unblockIpForName(ip, username);
        player.sendMessage(removed ? this.getMessageManager().getMessage("cmd-unblock-success", "{ip}", ip, "{username}", username) : this.msg("cmd-unblock-nothing"));
    }

    private void handleClearBlocks(Player player, String[] args) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(this.msg("cmd-clearblocks-usage"));
            return;
        }
        String username = args[1];
        int cleared = this.plugin.getAuthManager().clearAllBlocksForUsername(username);
        player.sendMessage(this.getMessageManager().getMessage("cmd-clearblocks-success", "{count}", String.valueOf(cleared), "{username}", username));
    }

    private void handleAdminSetPassword(Player player, String[] args) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(this.msg("cmd-admin-setpassword-usage"));
            return;
        }
        String targetName = args[1];
        String newPassword = args[2];
        String confirm = args[3];
        PasswordValidator validator = new PasswordValidator(this.plugin.getConfig());
        PasswordValidator.ValidationResult validation = validator.validate(newPassword, confirm);
        if (!validation.isValid()) {
            player.sendMessage((Component)Component.text((String)validation.getMessage(), (TextColor)NamedTextColor.RED));
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                User target = this.plugin.getAuthService().getDatabase().getUserByUsername(targetName);
                if (target == null) {
                    player.sendMessage(this.getMessageManager().getMessage("cmd-player-not-found", "{player}", targetName));
                    return;
                }
                String hashed = PasswordHasher.hash(newPassword, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
                target.setPassword(hashed);
                this.plugin.getAuthService().getDatabase().updateUser(target);
                this.plugin.getAuthService().removeSession(target.getUuid());
                this.plugin.getAuthManager().setAuthenticated(target.getUuid(), false);
                this.plugin.getServer().getPlayer(target.getUuid()).ifPresent(p -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                    p.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Password changed by admin. Please re-login.", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                });
                player.sendMessage(this.getMessageManager().getMessage("cmd-admin-setpassword-success", "{player}", target.getUsername()));
                this.plugin.getLogger().info("[ADMIN] {} changed password for {}", (Object)player.getUsername(), (Object)target.getUsername());
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[ADMIN] Failed setting password for {}: {}", targetName, e.getMessage(), e);
                player.sendMessage(this.getMessageManager().getMessage("cmd-admin-setpassword-failed", "{error}", e.getMessage()));
            }
        }).schedule();
    }

    private void handleAdminResetPassword(Player player, String[] args) {
        if (!this.isAdmin(player)) {
            player.sendMessage(this.msg("cmd-no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(this.msg("cmd-admin-resetpassword-usage"));
            return;
        }
        String targetName = args[1];
        int length = 12;
        if (args.length >= 3) {
            try {
                length = Integer.parseInt(args[2]);
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        String tempPassword = RandomPasswordGenerator.generate(length);
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                User target = this.plugin.getAuthService().getDatabase().getUserByUsername(targetName);
                if (target == null) {
                    player.sendMessage(this.getMessageManager().getMessage("cmd-player-not-found", "{player}", targetName));
                    return;
                }
                String hashed = PasswordHasher.hash(tempPassword, this.plugin.getConfig().getString("authentication.password.hashing", "argon2"), this.plugin.getConfig());
                target.setPassword(hashed);
                this.plugin.getAuthService().getDatabase().updateUser(target);
                this.plugin.getAuthService().removeSession(target.getUuid());
                this.plugin.getAuthManager().setAuthenticated(target.getUuid(), false);
                this.plugin.getServer().getPlayer(target.getUuid()).ifPresent(p -> {
                    List<Component> kickMsg = this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]);
                    p.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Password reset by admin. Please re-login.", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
                });
                player.sendMessage(this.getMessageManager().getMessage("cmd-admin-resetpassword-success", "{player}", target.getUsername(), "{password}", tempPassword));
                this.plugin.getLogger().info("[ADMIN] {} reset password for {}", (Object)player.getUsername(), (Object)target.getUsername());
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[ADMIN] Failed resetting password for {}: {}", targetName, e.getMessage(), e);
                player.sendMessage(this.getMessageManager().getMessage("cmd-admin-resetpassword-failed", "{error}", e.getMessage()));
            }
        }).schedule();
    }

    private void handleInfoConsole(CommandSource source) {
        if (!this.isAdmin(source)) {
            source.sendMessage((Component)Component.text((String)"No permission", (TextColor)NamedTextColor.RED));
            return;
        }
        source.sendMessage((Component)Component.text((String)("RelishAuth v" + BuildConstants.VERSION), (TextColor)NamedTextColor.AQUA));
        source.sendMessage((Component)Component.text((String)("Database: " + (this.plugin.getDatabase() != null && this.plugin.getDatabase().isHealthy() ? "Connected" : "Disconnected")), (TextColor)NamedTextColor.GRAY));
        source.sendMessage((Component)Component.text((String)("Discord Bot: " + (this.plugin.getDiscordBot() != null && this.plugin.getDiscordBot().isEnabled() ? "Connected" : "Disconnected")), (TextColor)NamedTextColor.GRAY));
    }

    private boolean checkAuthenticated(Player player) {
        if (this.plugin.getAuthManager() == null) {
            player.sendMessage(this.msg("error-auth-unavailable"));
            return false;
        }
        if (!this.plugin.getAuthManager().isAuthenticated(player.getUniqueId(), player.getUsername())) {
            player.sendMessage(this.msg("cmd-authenticated-required"));
            return false;
        }
        if (this.plugin.getAuthService() == null) {
            player.sendMessage(this.msg("error-auth-service-unavailable"));
            return false;
        }
        User user = this.plugin.getAuthService().resolveUserForPlayer(player);
        if (user == null) {
            player.sendMessage(this.msg("cmd-account-data-not-found"));
            return false;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(this.msg("help-header"));
        player.sendMessage((Component)Component.empty());
        player.sendMessage(this.msg("help-password"));
        player.sendMessage(this.msg("help-logout"));
        player.sendMessage(this.msg("help-discord"));
        player.sendMessage(this.msg("help-unlink"));
        player.sendMessage(this.msg("help-notify"));
        player.sendMessage(this.msg("help-session"));
        player.sendMessage(this.msg("help-syncgroups"));
        if (this.isAdmin(player)) {
            player.sendMessage((Component)Component.empty());
            player.sendMessage(this.msg("help-admin"));
            player.sendMessage(this.msg("help-reload"));
            player.sendMessage(this.msg("help-info"));
            player.sendMessage(this.msg("help-unlink-player"));
            player.sendMessage(this.msg("help-setpassword"));
            player.sendMessage(this.msg("help-resetpassword"));
            player.sendMessage(this.msg("help-ip-blocks"));
        }
        player.sendMessage((Component)Component.empty());
    }

    private boolean isValidDuration(String duration) {
        return DurationUtil.isValidDuration(duration);
    }

    private List<String> getAvailableSessionDurations() {
        List<String> configured = this.plugin.getConfig().getStringList("session.available-durations").stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(DurationUtil::isValidDuration)
            .distinct()
            .toList();
        return configured.isEmpty() ? List.of("0", "5m", "15m", "30m", "1h") : configured;
    }

    private String formatDuration(String duration) {
        return DurationUtil.formatDuration(duration);
    }

    private boolean isRealDiscordId(String discordId) {
        return ValidationUtil.isRealDiscordId(discordId);
    }

    private boolean isAdmin(CommandSource source) {
        if (!(source instanceof Player)) {
            return true;
        }
        Player player = (Player)source;
        return this.isAdmin(player);
    }

    private boolean isAdmin(Player player) {
        List<String> adminPlayers = this.plugin.getConfig().getStringList("admin-players");
        for (String adminName : adminPlayers) {
            if (!adminName.equalsIgnoreCase(player.getUsername())) continue;
            this.plugin.debug("Admin access granted to {} via config admin-players", player.getUsername());
            return true;
        }
        if (player.hasPermission(PERM_ADMIN) || player.hasPermission(LEGACY_PERM_ADMIN)) {
            this.plugin.debug("Admin access granted to {} via {} permission", player.getUsername(), PERM_ADMIN);
            return true;
        }
        if (player.hasPermission(PERM_WILDCARD) || player.hasPermission(LEGACY_PERM_WILDCARD)) {
            this.plugin.debug("Admin access granted to {} via {} permission", player.getUsername(), PERM_WILDCARD);
            return true;
        }
        this.plugin.debug("Admin access denied to {} - not in config and no permissions", player.getUsername());
        return false;
    }

    private String resolveIpAddress(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            return trimmed;
        }
        Optional online = this.plugin.getServer().getPlayer(trimmed);
        if (online.isPresent()) {
            return ((Player)online.get()).getRemoteAddress().getAddress().getHostAddress();
        }
        try {
            User user = this.plugin.getAuthService().getDatabase().getUserByUsername(trimmed);
            if (user != null && user.getIpAddress() != null && !user.getIpAddress().isEmpty()) {
                return user.getIpAddress();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private MessageManager getMessageManager() {
        MessageManager mm = this.plugin.getMessageManager();
        if (mm == null) {
            throw new IllegalStateException("MessageManager not initialized");
        }
        return mm;
    }

    private Component msg(String key) {
        try {
            return this.getMessageManager().getMessage(key);
        }
        catch (Exception e) {
            return Component.text((String)key, (TextColor)NamedTextColor.GRAY);
        }
    }

    public boolean hasPermission(SimpleCommand.Invocation invocation) {
        return true;
    }

    public List<String> suggest(SimpleCommand.Invocation invocation) {
        String[] args = (String[])invocation.arguments();
        if (args.length <= 1) {
            ArrayList<String> completions = new ArrayList<String>(Arrays.asList("help", "password", "logout", "discord", "notify", "session", "syncgroups", "unlink"));
            if (this.isAdmin(invocation.source())) {
                completions.addAll(Arrays.asList("reload", "info", "block", "unblock", "clearblocks", "setpassword", "resetpassword"));
            }
            if (args.length == 0) {
                return completions;
            }
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).sorted().toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("session")) {
                List<String> durations = this.plugin.getConfig().getStringList("session.available-durations");
                if (durations.isEmpty()) {
                    durations = List.of("0", "5m", "15m", "30m", "1h");
                }
                return durations.stream().filter(d -> d.startsWith(args[1].toLowerCase())).toList();
            }
            if (args[0].equalsIgnoreCase("notify") || args[0].equalsIgnoreCase("notifications")) {
                return Arrays.asList("on", "off").stream().filter(o -> o.startsWith(args[1].toLowerCase())).toList();
            }
        }
        return List.of();
    }
}
