package relish.relishAuthVelocity.services;

import com.velocitypowered.api.proxy.Player;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        return this.plugin.getConfig() != null
                && this.plugin.getConfig().getBoolean("group-sync.enabled", false)
                && this.discord != null
                && this.discord.isEnabled();
    }

    public void syncPlayer(Player player, String reason) {
        if (player == null || !this.isEnabled() || this.plugin.getAuthService() == null) {
            return;
        }
        try {
            UUID sessionUuid = player.getUniqueId();
            UUID accountUuid = this.plugin.getAuthManager() != null
                    ? this.plugin.getAuthManager().resolveAccountUuid(sessionUuid, player.getUsername())
                    : sessionUuid;
            relish.relishAuthVelocity.models.User user = this.plugin.getAuthService().getUser(accountUuid);
            if (user == null && !accountUuid.equals(sessionUuid)) {
                user = this.plugin.getAuthService().getUser(sessionUuid);
            }
            if (user == null) {
                this.plugin.debug("[GROUP-SYNC] No RelishAuth user for {} (session={}, account={})", player.getUsername(), sessionUuid, accountUuid);
                return;
            }
            // LuckPerms tracks the UUID Velocity currently uses for the online player.
            this.sync(sessionUuid, player.getUsername(), user.getDiscordId(), reason);
        } catch (Exception e) {
            this.plugin.getLogger().warn("[GROUP-SYNC] Failed to prepare sync for {}: {}", player.getUsername(), e.getMessage());
        }
    }

    public void syncDiscordUser(String discordId, String reason) {
        if (!this.isEnabled() || !ValidationUtil.isRealDiscordId(discordId) || this.plugin.getAuthService() == null) {
            return;
        }
        this.plugin.getServer().getScheduler().buildTask((Object) this.plugin, () -> {
            try {
                relish.relishAuthVelocity.models.User user = this.plugin.getAuthService().getDatabase().getUserByDiscordId(discordId);
                if (user == null) {
                    this.plugin.debug("[GROUP-SYNC] No linked Minecraft account for Discord user {}", discordId);
                    return;
                }
                UUID luckPermsUuid = user.getUuid();
                Player online = this.plugin.getServer().getPlayer(user.getUuid()).orElse(null);
                if (online == null) {
                    // Prefer online session UUID if the player is connected under a different profile UUID.
                    for (Player player : this.plugin.getServer().getAllPlayers()) {
                        if (player.getUsername().equalsIgnoreCase(user.getUsername())) {
                            online = player;
                            break;
                        }
                    }
                }
                if (online != null) {
                    luckPermsUuid = online.getUniqueId();
                }
                this.sync(luckPermsUuid, user.getUsername(), discordId, reason);
            } catch (Exception e) {
                this.plugin.getLogger().warn("[GROUP-SYNC] Failed to prepare Discord role sync for {}: {}", discordId, e.getMessage());
            }
        }).schedule();
    }

    /**
     * Removes all mapped LuckPerms groups for a player (e.g. after Discord unlink).
     */
    public void clearSyncedGroups(UUID uuid, String username, String reason) {
        if (uuid == null || this.plugin.getConfig() == null || !this.plugin.getConfig().getBoolean("group-sync.enabled", false)) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("group-sync.remove-groups-when-role-missing", true)) {
            this.plugin.debug("[GROUP-SYNC] Skipping clear for {} because remove-groups-when-role-missing=false", username);
            return;
        }
        Map<String, String> roleToGroup = this.getRoleToGroupMapping();
        if (roleToGroup.isEmpty()) {
            return;
        }
        LuckPerms luckPerms = this.getLuckPerms();
        if (luckPerms == null) {
            this.plugin.getLogger().warn("[GROUP-SYNC] LuckPerms is not available; cannot clear groups for {}", username);
            return;
        }
        Set<String> groups = new LinkedHashSet<>(roleToGroup.values());
        luckPerms.getUserManager().loadUser(uuid).thenCompose(user -> {
            boolean changed = false;
            for (String groupName : groups) {
                changed |= this.removeGroup(user, groupName);
            }
            if (!changed) {
                this.plugin.debug("[GROUP-SYNC] No mapped groups to clear for {} ({})", username, reason);
                return CompletableFuture.completedFuture(false);
            }
            return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> {
                this.plugin.getLogger().info("[GROUP-SYNC] Cleared mapped LuckPerms groups for {} ({})", username, reason);
                return true;
            });
        }).exceptionally(error -> {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            this.plugin.getLogger().warn("[GROUP-SYNC] Failed to clear groups for {}: {}", username, cause.getMessage());
            return false;
        });
    }

    public CompletableFuture<Boolean> sync(UUID uuid, String username, String discordId, String reason) {
        if (!this.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, String> roleToGroup = this.getRoleToGroupMapping();
        if (roleToGroup.isEmpty()) {
            this.plugin.debug("[GROUP-SYNC] Enabled but no group-sync.role-to-group mappings are configured");
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
        return this.discord.getRoleIds(discordId).thenCompose(roleIds -> {
            if (roleIds == null) {
                this.plugin.getLogger().warn("[GROUP-SYNC] Role lookup returned null for {} ({}); skipping to avoid wiping groups", username, reason);
                return CompletableFuture.completedFuture(false);
            }
            return this.applyGroups(luckPerms, uuid, username, roleIds, roleToGroup, reason);
        }).exceptionally(error -> {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            this.plugin.getLogger().warn("[GROUP-SYNC] Failed to sync groups for {}: {}", username, cause.getMessage());
            return false;
        });
    }

    private CompletableFuture<Boolean> applyGroups(
            LuckPerms luckPerms,
            UUID uuid,
            String username,
            Set<String> roleIds,
            Map<String, String> roleToGroup,
            String reason
    ) {
        Set<String> effectiveRoles = roleIds == null ? Collections.emptySet() : roleIds;
        boolean removeMissing = this.plugin.getConfig().getBoolean("group-sync.remove-groups-when-role-missing", true);
        return luckPerms.getUserManager().loadUser(uuid).thenCompose(user -> {
            boolean changed = false;
            for (Map.Entry<String, String> entry : roleToGroup.entrySet()) {
                String roleId = entry.getKey();
                String groupName = entry.getValue();
                boolean hasRole = effectiveRoles.contains(roleId);
                if (hasRole) {
                    changed |= this.addGroup(user, groupName);
                } else if (removeMissing) {
                    changed |= this.removeGroup(user, groupName);
                }
            }
            if (!changed) {
                this.plugin.debug("[GROUP-SYNC] Groups already up to date for {} ({})", username, reason);
                return CompletableFuture.completedFuture(false);
            }
            return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> {
                this.plugin.getLogger().info("[GROUP-SYNC] Synced LuckPerms groups for {} ({})", username, reason);
                return true;
            });
        });
    }

    private boolean addGroup(User user, String groupName) {
        if (this.hasGroup(user, groupName)) {
            return false;
        }
        InheritanceNode node = InheritanceNode.builder(groupName).build();
        return user.data().add(node).wasSuccessful();
    }

    private boolean removeGroup(User user, String groupName) {
        boolean changed = false;
        for (Node node : user.getNodes()) {
            if (node instanceof InheritanceNode inheritanceNode
                    && inheritanceNode.getGroupName().equalsIgnoreCase(groupName)) {
                changed |= user.data().remove(node).wasSuccessful();
            }
        }
        return changed;
    }

    private boolean hasGroup(User user, String groupName) {
        for (Node node : user.getNodes()) {
            if (node instanceof InheritanceNode inheritanceNode
                    && inheritanceNode.getGroupName().equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }

    private LuckPerms getLuckPerms() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return null;
        }
    }

    private Map<String, String> getRoleToGroupMapping() {
        Object raw = this.plugin.getConfig().get("group-sync.role-to-group");
        if (!(raw instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> roleToGroup = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String groupName = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
            String roleId = entry.getValue().toString().trim();
            if (groupName.isEmpty() || roleId.isEmpty()) {
                continue;
            }
            String previous = roleToGroup.put(roleId, groupName);
            if (previous != null && !previous.equals(groupName)) {
                this.plugin.getLogger().warn(
                        "[GROUP-SYNC] Discord role {} maps to multiple groups ({} and {}); using {}",
                        roleId, previous, groupName, groupName
                );
            }
        }
        return roleToGroup;
    }
}
