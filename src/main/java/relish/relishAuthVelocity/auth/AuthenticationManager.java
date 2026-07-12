package relish.relishAuthVelocity.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.premium.PremiumStatus;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.services.SkinCacheResolver;

public class AuthenticationManager {
    private final RelishAuthVelocity plugin;
    private final Map<String, PremiumVerificationResult> premiumResultsByUsername = new ConcurrentHashMap<String, PremiumVerificationResult>();
    private final Map<UUID, Long> connectionTimestamps = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Boolean> authenticatedPlayers = new ConcurrentHashMap<UUID, Boolean>();
    private final Map<String, IpBlock> blockedIps = new ConcurrentHashMap<String, IpBlock>();
    private final Map<String, Long> joinNotificationCooldowns = new ConcurrentHashMap<String, Long>();
    private final Object premiumCacheLock = new Object();
    private final ScheduledExecutorService cleanupScheduler;
    private static final long CLEANUP_INTERVAL_MINUTES = 5L;
    private static final long STALE_CONNECTION_THRESHOLD_MS = 1800000L;

    public AuthenticationManager(RelishAuthVelocity plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RelishAuth-Cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(this::performCleanup, 5L, 5L, TimeUnit.MINUTES);
    }

    public void cachePremiumVerificationResultByUsername(String username, PremiumVerificationResult result) {
        if (username == null || username.isEmpty()) {
            this.plugin.getLogger().warn("[AUTH-MANAGER] Attempted to cache premium result with null/empty username");
            return;
        }
        Object object = this.premiumCacheLock;
        synchronized (object) {
            try {
                this.premiumResultsByUsername.put(username.toLowerCase(), result);
                this.plugin.debug("[AUTH-MANAGER] Cached premium verification for {}: {}", username, result != null ? result.getStatus() : "null");
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH-MANAGER] Error caching premium result for {}: {}", (Object)username, (Object)e.getMessage());
            }
        }
    }

    public PremiumVerificationResult getPremiumVerificationResultByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        Object object = this.premiumCacheLock;
        synchronized (object) {
            try {
                return this.premiumResultsByUsername.get(username.toLowerCase());
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH-MANAGER] Error getting premium result for {}: {}", (Object)username, (Object)e.getMessage());
                return null;
            }
        }
    }

    public UUID resolveAccountUuid(UUID connectionUuid, String username) {
        UUID baseUuid = connectionUuid;
        if (baseUuid == null && username != null && !username.isBlank()) {
            baseUuid = SkinCacheResolver.generateOfflineUuid(username);
        }
        if (this.plugin.getConfig() == null) {
            return baseUuid;
        }
        boolean preferOfficial = this.plugin.getConfig().getBoolean("authentication.premium-use-official-uuid", false);
        if (!preferOfficial || username == null || username.isBlank()) {
            return baseUuid;
        }
        PremiumVerificationResult result = this.getPremiumVerificationResultByUsername(username);
        if (result == null || result.getMojangUuid() == null) {
            return baseUuid;
        }
        PremiumStatus status = result.getStatus();
        boolean verified = status == PremiumStatus.PREMIUM_VERIFIED || status == PremiumStatus.PREMIUM_PENDING_ENCRYPTION;
        return verified ? result.getMojangUuid() : baseUuid;
    }

    public void recordConnection(UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            if (!this.connectionTimestamps.containsKey(uuid)) {
                this.connectionTimestamps.put(uuid, System.currentTimeMillis());
                this.plugin.debug("[AUTH-MANAGER] Recording connection timestamp for UUID: {}", uuid);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error recording connection for {}: {}", (Object)uuid, (Object)e.getMessage());
        }
    }

    public Long getConnectionTimestamp(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return this.connectionTimestamps.get(uuid);
    }

    public void cleanupOnDisconnect(UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            this.connectionTimestamps.remove(uuid);
            this.authenticatedPlayers.remove(uuid);
            this.plugin.debug("[AUTH-MANAGER] Cleaned up disconnect data for {}", uuid);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error cleaning up disconnect for {}: {}", (Object)uuid, (Object)e.getMessage());
        }
    }

    public void cleanupOnDisconnect(UUID uuid, String username) {
        UUID mojangUuid;
        PremiumVerificationResult premiumResult;
        this.cleanupOnDisconnect(uuid);
        if (username == null || username.isBlank()) {
            return;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        if (!offlineUuid.equals(uuid)) {
            this.cleanupOnDisconnect(offlineUuid);
        }
        if ((premiumResult = this.getPremiumVerificationResultByUsername(username)) != null && premiumResult.getMojangUuid() != null && !(mojangUuid = premiumResult.getMojangUuid()).equals(uuid) && !mojangUuid.equals(offlineUuid)) {
            this.cleanupOnDisconnect(mojangUuid);
        }
    }

    public void cleanupUsername(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        Object object = this.premiumCacheLock;
        synchronized (object) {
            try {
                this.premiumResultsByUsername.remove(username.toLowerCase());
                this.plugin.debug("[AUTH-MANAGER] Cleaned up username cache for {}", username);
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH-MANAGER] Error cleaning up username {}: {}", (Object)username, (Object)e.getMessage());
            }
        }
    }

    public boolean isAuthenticated(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return this.authenticatedPlayers.getOrDefault(uuid, false);
    }

    public boolean isAuthenticated(UUID uuid, String username) {
        UUID mojangUuid;
        if (this.isAuthenticated(uuid)) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        if (!offlineUuid.equals(uuid) && this.isAuthenticated(offlineUuid)) {
            return true;
        }
        PremiumVerificationResult premiumResult = this.getPremiumVerificationResultByUsername(username);
        return premiumResult != null && premiumResult.getMojangUuid() != null && !(mojangUuid = premiumResult.getMojangUuid()).equals(uuid) && !mojangUuid.equals(offlineUuid) && this.isAuthenticated(mojangUuid);
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (uuid == null) {
            return;
        }
        try {
            if (authenticated) {
                this.authenticatedPlayers.put(uuid, true);
            } else {
                this.authenticatedPlayers.remove(uuid);
            }
            this.plugin.debug("[AUTH-MANAGER] Set authenticated status for {}: {}", uuid, authenticated);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error setting auth status for {}: {}", (Object)uuid, (Object)e.getMessage());
        }
    }

    public void setAuthenticated(UUID uuid, String username, boolean authenticated) {
        UUID mojangUuid;
        PremiumVerificationResult premiumResult;
        this.setAuthenticated(uuid, authenticated);
        if (username == null || username.isBlank()) {
            return;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        if (!offlineUuid.equals(uuid)) {
            this.setAuthenticated(offlineUuid, authenticated);
        }
        if ((premiumResult = this.getPremiumVerificationResultByUsername(username)) != null && premiumResult.getMojangUuid() != null && !(mojangUuid = premiumResult.getMojangUuid()).equals(uuid) && !mojangUuid.equals(offlineUuid)) {
            this.setAuthenticated(mojangUuid, authenticated);
        }
    }

    public void blockIpForName(String ip, String username, int durationSeconds) {
        if (ip == null || username == null) {
            return;
        }
        try {
            String key = ip + ":" + username.toLowerCase();
            boolean permanent = durationSeconds == -1;
            long expiresAt = permanent ? Long.MAX_VALUE : System.currentTimeMillis() + (long)durationSeconds * 1000L;
            this.blockedIps.put(key, new IpBlock(username, expiresAt, permanent));
            this.plugin.debug("[AUTH-MANAGER] Blocked IP {} for username {} (duration: {}s)", ip, username, durationSeconds);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error blocking IP {} for {}: {}", ip, username, e.getMessage());
        }
    }

    public boolean isIpBlockedForName(String ip, String username) {
        if (ip == null || username == null) {
            return false;
        }
        try {
            String key = ip + ":" + username.toLowerCase();
            IpBlock block = this.blockedIps.get(key);
            if (block == null) {
                return false;
            }
            if (block.isExpired()) {
                this.blockedIps.remove(key);
                return false;
            }
            return true;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error checking IP block for {} / {}: {}", ip, username, e.getMessage());
            return false;
        }
    }

    public long getIpBlockRemainingTime(String ip, String username) {
        if (ip == null || username == null) {
            return 0L;
        }
        try {
            String key = ip + ":" + username.toLowerCase();
            IpBlock block = this.blockedIps.get(key);
            if (block == null || block.isExpired()) {
                return 0L;
            }
            if (block.isPermanent()) {
                return -1L;
            }
            return block.expiresAt - System.currentTimeMillis();
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error getting block time for {} / {}: {}", ip, username, e.getMessage());
            return 0L;
        }
    }

    public boolean unblockIpForName(String ip, String username) {
        if (ip == null || username == null) {
            return false;
        }
        try {
            boolean removed;
            String key = ip + ":" + username.toLowerCase();
            boolean bl = removed = this.blockedIps.remove(key) != null;
            if (removed) {
                this.plugin.debug("[AUTH-MANAGER] Unblocked IP {} for username {}", ip, username);
            }
            return removed;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error unblocking IP {} for {}: {}", ip, username, e.getMessage());
            return false;
        }
    }

    public int clearAllBlocksForUsername(String username) {
        if (username == null) {
            return 0;
        }
        try {
            String lowerUsername = username.toLowerCase();
            int count = 0;
            Iterator<Map.Entry<String, IpBlock>> iterator = this.blockedIps.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, IpBlock> entry = iterator.next();
                if (!entry.getValue().username.equalsIgnoreCase(lowerUsername)) continue;
                iterator.remove();
                ++count;
            }
            if (count > 0) {
                this.plugin.debug("[AUTH-MANAGER] Cleared {} IP blocks for username {}", count, username);
            }
            return count;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error clearing blocks for {}: {}", (Object)username, (Object)e.getMessage());
            return 0;
        }
    }

    public List<String> getBlockedIpsForUsername(String username) {
        if (username == null) {
            return Collections.emptyList();
        }
        try {
            String lowerUsername = username.toLowerCase();
            ArrayList<String> ips = new ArrayList<String>();
            for (Map.Entry<String, IpBlock> entry : this.blockedIps.entrySet()) {
                String[] parts;
                if (!entry.getValue().username.equalsIgnoreCase(lowerUsername) || (parts = entry.getKey().split(":")).length <= 0) continue;
                ips.add(parts[0]);
            }
            return ips;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error getting blocked IPs for {}: {}", (Object)username, (Object)e.getMessage());
            return Collections.emptyList();
        }
    }

    private void performCleanup() {
        try {
            long now = System.currentTimeMillis();
            int cleanedAuth = 0;
            int cleanedBlocks = 0;
            Iterator<Map.Entry<UUID, Long>> connIterator = this.connectionTimestamps.entrySet().iterator();
            while (connIterator.hasNext()) {
                Map.Entry<UUID, Long> entry = connIterator.next();
                if (now - entry.getValue() <= 1800000L) continue;
                UUID uuid = entry.getKey();
                if (!this.plugin.getServer().getPlayer(uuid).isEmpty()) continue;
                connIterator.remove();
                this.authenticatedPlayers.remove(uuid);
                ++cleanedAuth;
            }
            Iterator<Map.Entry<String, IpBlock>> blockIterator = this.blockedIps.entrySet().iterator();
            while (blockIterator.hasNext()) {
                Map.Entry<String, IpBlock> entry = blockIterator.next();
                if (!entry.getValue().isExpired()) continue;
                blockIterator.remove();
                ++cleanedBlocks;
            }
            int cleanedNotifications = 0;
            int cooldownSeconds = this.plugin.getConfig().getInt("discord.join-notifications.cooldown", 60);
            long cooldownMs = (long)cooldownSeconds * 1000L;
            Iterator<Map.Entry<String, Long>> notifyIterator = this.joinNotificationCooldowns.entrySet().iterator();
            while (notifyIterator.hasNext()) {
                Map.Entry<String, Long> entry = notifyIterator.next();
                if (now - entry.getValue() <= cooldownMs * 2L) continue;
                notifyIterator.remove();
                ++cleanedNotifications;
            }
            if (cleanedAuth > 0 || cleanedBlocks > 0 || cleanedNotifications > 0) {
                this.plugin.debug("[AUTH-MANAGER] Cleanup: removed {} stale Auth, {} expired blocks, {} old notifications", cleanedAuth, cleanedBlocks, cleanedNotifications);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH-MANAGER] Error during cleanup: {}", (Object)e.getMessage());
        }
    }

    public void shutdown() {
        try {
            this.cleanupScheduler.shutdown();
            if (!this.cleanupScheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.cleanupScheduler.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            this.cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean canSendJoinNotification(String discordId) {
        if (discordId == null) {
            return false;
        }
        Long lastNotification = this.joinNotificationCooldowns.get(discordId);
        if (lastNotification == null) {
            return true;
        }
        int cooldownSeconds = this.plugin.getConfig().getInt("discord.join-notifications.cooldown", 60);
        long cooldownMs = (long)cooldownSeconds * 1000L;
        return System.currentTimeMillis() - lastNotification >= cooldownMs;
    }

    public void recordJoinNotification(String discordId) {
        if (discordId != null) {
            this.joinNotificationCooldowns.put(discordId, System.currentTimeMillis());
        }
    }

    public Map<String, Integer> getStats() {
        HashMap<String, Integer> stats = new HashMap<String, Integer>();
        stats.put("authenticatedPlayers", this.authenticatedPlayers.size());
        stats.put("connectionTimestamps", this.connectionTimestamps.size());
        stats.put("premiumCache", this.premiumResultsByUsername.size());
        stats.put("blockedIps", this.blockedIps.size());
        stats.put("joinNotificationCooldowns", this.joinNotificationCooldowns.size());
        return stats;
    }

    private static class IpBlock {
        final String username;
        final long expiresAt;
        final boolean permanent;

        IpBlock(String username, long expiresAt, boolean permanent) {
            this.username = username;
            this.expiresAt = expiresAt;
            this.permanent = permanent;
        }

        boolean isExpired() {
            if (this.permanent) {
                return false;
            }
            return System.currentTimeMillis() > this.expiresAt;
        }

        boolean isPermanent() {
            return this.permanent;
        }
    }
}
