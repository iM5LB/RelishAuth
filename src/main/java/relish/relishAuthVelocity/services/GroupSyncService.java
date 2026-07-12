package relish.relishAuthVelocity.services;

import com.velocitypowered.api.proxy.Player;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.integrations.DiscordIntegration;
import relish.relishAuthVelocity.utils.ValidationUtil;

public final class GroupSyncService {
    private final RelishAuthVelocity plugin;
    private final DiscordIntegration discord;

    public GroupSyncService(RelishAuthVelocity plugin, DiscordIntegration discord) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.discord = Objects.requireNonNull(discord, "discord");
    }

    public boolean isEnabled() {
        return this.plugin.getConfig() != null && this.plugin.getConfig().getBoolean("group-sync.enabled", false);
    }

    public void syncPlayer(Player player, String reason) {
        if (player == null || this.plugin.getAuthService() == null) {
            return;
        }
        try {
            relish.relishAuthVelocity.models.User user = this.plugin.getAuthService().getUser(player.getUniqueId());
            if (user == null) {
                return;
            }
            this.sync(player.getUniqueId(), player.getUsername(), user.getDiscordId(), reason);
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[GROUP-SYNC] Failed to prepare sync for {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
        }
    }

    public void syncDiscordUser(String discordId, String reason) {
        if (!this.isEnabled() || !ValidationUtil.isRealDiscordId(discordId) || this.plugin.getAuthService() == null) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            try {
                relish.relishAuthVelocity.models.User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
                if (user == null) {
                    this.plugin.debug("[GROUP-SYNC] No linked Minecraft account for Discord user {}", discordId);
                    return;
                }
                this.sync(user.getUuid(), user.getUsername(), discordId, reason);
            }
            catch (Exception e) {
                this.plugin.getLogger().warn("[GROUP-SYNC] Failed to prepare Discord role sync for {}: {}", (Object)discordId, (Object)e.getMessage());
            }
        }).schedule();
    }

    public CompletableFuture<Boolean> sync(UUID uuid, String username, String discordId, String reason) {
        if (!this.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, String> roleToGroup = this.getRoleToGroupMapping();
        if (roleToGroup.isEmpty()) {
            this.plugin.debug("[GROUP-SYNC] Enabled but no group-sync.role-to-group mappings are configured", new Object[0]);
            return CompletableFuture.completedFuture(false);
        }
        if (!ValidationUtil.isRealDiscordId(discordId)) {
            this.plugin.debug("[GROUP-SYNC] {} has no linked Discord account, skipping", username);
            return CompletableFuture.completedFuture(false);
        }
        LuckPerms luckPerms = this.getLuckPerms();
        if (luckPerms == null) {
            this.plugin.getLogger().warn("[GROUP-SYNC] LuckPerms is not available on the proxy; cannot sync groups");
            return CompletableFuture.completedFuture(false);
        }
        return this.discord.getRoleIds(discordId).thenCompose(roleIds -> this.applyGroups(luckPerms, uuid, username, roleIds, roleToGroup, reason)).exceptionally((Throwable error) -> {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            this.plugin.getLogger().warn("[GROUP-SYNC] Failed to sync groups for {}: {}", (Object)username, (Object)cause.getMessage());
            return false;
        });
    }

    private CompletableFuture<Boolean> applyGroups(LuckPerms luckPerms, UUID uuid, String username, Set<String> roleIds, Map<String, String> roleToGroup, String reason) {
        Set<String> effectiveRoles = roleIds == null ? Collections.emptySet() : roleIds;
        boolean removeMissing = this.plugin.getConfig().getBoolean("group-sync.remove-groups-when-role-missing", true);
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
            boolean changed = false;
            for (Map.Entry<String, String> entry : roleToGroup.entrySet()) {
                String roleId = entry.getKey();
                String groupName = entry.getValue();
                boolean hasRole = effectiveRoles.contains(roleId);
                if (hasRole) {
                    changed |= this.addGroup((User)user, groupName);
                    continue;
                }
                if (!removeMissing) continue;
                changed |= this.removeGroup((User)user, groupName);
            }
            if (changed) {
                luckPerms.getUserManager().saveUser(user);
                this.plugin.getLogger().info("[GROUP-SYNC] Synced LuckPerms groups for {} ({})", (Object)username, (Object)reason);
            } else {
                this.plugin.debug("[GROUP-SYNC] Groups already up to date for {} ({})", username, reason);
            }
            return changed;
        });
    }

    private boolean addGroup(User user, String groupName) {
        InheritanceNode node = (InheritanceNode)InheritanceNode.builder((String)groupName).build();
        if (user.getNodes().contains(node)) {
            return false;
        }
        return user.data().add((Node)node).wasSuccessful();
    }

    private boolean removeGroup(User user, String groupName) {
        boolean changed = false;
        for (Node node : user.getNodes()) {
            InheritanceNode inheritanceNode;
            if (!(node instanceof InheritanceNode) || !(inheritanceNode = (InheritanceNode)node).getGroupName().equalsIgnoreCase(groupName)) continue;
            changed |= user.data().remove(node).wasSuccessful();
        }
        return changed;
    }

    private LuckPerms getLuckPerms() {
        try {
            return LuckPermsProvider.get();
        }
        catch (IllegalStateException | NoClassDefFoundError e) {
            return null;
        }
    }

    private Map<String, String> getRoleToGroupMapping() {
        Object raw = this.plugin.getConfig().get("group-sync.role-to-group");
        if (!(raw instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> map = (Map<?, ?>)raw;
        LinkedHashMap<String, String> roleToGroup = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String groupName = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
            String roleId = entry.getValue().toString().trim();
            if (groupName.isEmpty() || roleId.isEmpty()) continue;
            roleToGroup.put(roleId, groupName);
        }
        return roleToGroup;
    }
}
