package relish.relishAuthVelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.models.User;

public class LogoutCommand
implements SimpleCommand {
    private final RelishAuthVelocity plugin;

    public LogoutCommand(RelishAuthVelocity plugin) {
        this.plugin = plugin;
    }

    public void execute(SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        if (!(commandSource instanceof Player)) {
            invocation.source().sendMessage((Component)Component.text((String)"Only players can use this command", (TextColor)NamedTextColor.RED));
            return;
        }
        Player player = (Player)commandSource;
        if (!this.plugin.isInitialized()) {
            player.sendMessage((Component)Component.text((String)"Plugin not initialized", (TextColor)NamedTextColor.RED));
            return;
        }
        if (this.plugin.getAuthManager() == null || !this.plugin.getAuthManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage((Component)Component.text((String)"You are not authenticated", (TextColor)NamedTextColor.RED));
            return;
        }
        UUID uuid = player.getUniqueId();
        User user = this.plugin.getAuthService().getUser(uuid);
        if (user != null && user.getDiscordId() != null) {
            this.plugin.getAuthService().getDatabase().clearAllSessions(user.getDiscordId());
        }
        this.plugin.getAuthService().removeSession(uuid);
        this.plugin.getAuthManager().setAuthenticated(uuid, false);
        if (this.plugin.getLimboHandler() != null) {
            this.plugin.getLimboHandler().cleanup(uuid);
        }
        this.plugin.debug("[LOGOUT] Cleared all sessions and auth data for {}", player.getUsername());
        int delay = this.plugin.getConfig().getInt("commands.logout.disconnect-delay", 100);
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                List kickMsg = this.plugin.getMessageManager() != null ? this.plugin.getMessageManager().getMessageList("kick.logged-out", new String[0]) : List.of();
                player.disconnect((Component)(kickMsg.isEmpty() ? Component.text((String)"Logged out", (TextColor)NamedTextColor.YELLOW) : Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), kickMsg)));
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("Error disconnecting player during logout: {}", (Object)e.getMessage());
            }
        }).delay((long)delay, TimeUnit.MILLISECONDS).schedule();
    }

    public boolean hasPermission(SimpleCommand.Invocation invocation) {
        return true;
    }
}
