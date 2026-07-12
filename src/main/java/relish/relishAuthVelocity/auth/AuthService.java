package relish.relishAuthVelocity.auth;

import java.net.InetAddress;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.database.AuthDatabase;
import relish.relishAuthVelocity.exceptions.AuthenticationException;
import relish.relishAuthVelocity.exceptions.DatabaseException;
import relish.relishAuthVelocity.exceptions.PluginException;
import relish.relishAuthVelocity.models.PlayerSession;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.services.SkinFetchService;
import relish.relishAuthVelocity.services.SkinFileStorage;
import relish.relishAuthVelocity.utils.DurationUtil;
import relish.relishAuthVelocity.utils.PasswordHasher;

public class AuthService {
    private final RelishAuthVelocity plugin;
    private final AuthDatabase database;
    private final Config config;
    private final SkinFileStorage skinFileStorage;
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<UUID, PlayerSession>();
    private final Map<UUID, Integer> passwordAttempts = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> lockoutTimes = new ConcurrentHashMap<UUID, Long>();
    private final Object sessionLock = new Object();

    public AuthService(RelishAuthVelocity plugin, AuthDatabase database, Config config) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.database = Objects.requireNonNull(database, "Database cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.skinFileStorage = new SkinFileStorage(plugin, plugin.getLogger());
    }

    public boolean isAuthenticated(UUID uuid, InetAddress address) {
        if (uuid == null || address == null) {
            return false;
        }
        Object object = this.sessionLock;
        synchronized (object) {
            try {
                PlayerSession dbSession;
                User user;
                PlayerSession session = this.activeSessions.get(uuid);
                if (session != null && session.isAuthenticated()) {
                    String discordId = session.getDiscordId();
                    long sessionDuration = this.parseSessionDuration(this.database.getPlayerSessionDuration(discordId));
                    if (!session.isExpired(sessionDuration)) {
                        boolean ipMatch = session.getIpAddress().equals(address.getHostAddress());
                        if (ipMatch) {
                            session.setLastSeen(System.currentTimeMillis());
                            this.saveSessionSafely(session);
                            this.plugin.debug("[AUTH-SESSION] Valid active session for {} from same IP", uuid);
                            return true;
                        }
                        this.plugin.debug("[AUTH-SESSION] IP mismatch for session continuation {}: expected {}, got {} - rejecting session", uuid, session.getIpAddress(), address.getHostAddress());
                        this.activeSessions.remove(uuid);
                        this.deleteSessionSafely(uuid);
                        return false;
                    }
                    this.plugin.debug("[AUTH-SESSION] Session expired for {}", uuid);
                    this.activeSessions.remove(uuid);
                    this.deleteSessionSafely(uuid);
                }
                if ((user = this.database.getUser(uuid)) != null && (dbSession = this.database.getSession(uuid)) != null) {
                    String discordId = dbSession.getDiscordId();
                    long sessionDuration = this.parseSessionDuration(this.database.getPlayerSessionDuration(discordId));
                    boolean expired = dbSession.isExpired(sessionDuration);
                    if (!expired) {
                        boolean ipMatch = dbSession.getIpAddress().equals(address.getHostAddress());
                        if (ipMatch) {
                            dbSession.setLastSeen(System.currentTimeMillis());
                            this.activeSessions.put(uuid, dbSession);
                            this.saveSessionSafely(dbSession);
                            this.plugin.debug("[AUTH-SESSION] Valid database session for {} from same IP", uuid);
                            return true;
                        }
                        this.plugin.debug("[AUTH-SESSION] IP mismatch for database session continuation {}: expected {}, got {} - rejecting session", uuid, dbSession.getIpAddress(), address.getHostAddress());
                        this.deleteSessionSafely(uuid);
                        return false;
                    }
                    this.plugin.debug("[AUTH-SESSION] Database session expired for {}", uuid);
                    this.deleteSessionSafely(uuid);
                }
                return false;
            }
            catch (DatabaseException e) {
                this.plugin.getLogger().error("[AUTH] Database error checking authentication for {}: {}", (Object)uuid, (Object)e.getMessage());
                return false;
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH] Unexpected error checking authentication for {}: {}", uuid, e.getMessage(), e);
                return false;
            }
        }
    }

    public boolean isPremiumAutoLogin(UUID uuid) {
        return this.config.getBoolean("authentication.premium-auto-login", true);
    }

    public boolean register(UUID uuid, String username, String password, boolean isPremium, InetAddress address) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        try {
            if (this.isRegistered(uuid)) {
                throw new AuthenticationException(PluginException.ErrorCode.AUTH_ALREADY_REGISTERED, "User already registered", uuid, username);
            }
            User user = new User(uuid);
            user.setUsername(username);
            user.setPassword(PasswordHasher.hash(password, this.config.getString("authentication.password.hashing", "argon2"), this.config));
            user.setDiscordId(null);
            user.setAccountType(isPremium ? "PREMIUM" : "CRACKED");
            user.setFirstLogin(System.currentTimeMillis());
            user.setLastLogin(System.currentTimeMillis());
            user.setIpAddress(address.getHostAddress());
            this.database.createUser(user);
            this.plugin.debug("[AUTH] Registered new user: {} ({})", username, isPremium ? "PREMIUM" : "CRACKED");
            this.fetchAndSaveSkin(uuid, username, isPremium);
            return true;
        }
        catch (AuthenticationException e) {
            throw e;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error registering user {}: {}", (Object)username, (Object)e.getMessage());
            throw e;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Error registering user {}: {}", username, e.getMessage(), e);
            throw new AuthenticationException(PluginException.ErrorCode.AUTH_NOT_REGISTERED, "Registration failed: " + e.getMessage(), uuid, (Throwable)e);
        }
    }

    public boolean registerPremium(UUID uuid, String username, InetAddress address) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        try {
            User user = new User(uuid);
            user.setUsername(username);
            user.setPassword(null);
            user.setDiscordId(null);
            user.setAccountType("PREMIUM");
            user.setFirstLogin(System.currentTimeMillis());
            user.setLastLogin(System.currentTimeMillis());
            user.setIpAddress(address.getHostAddress());
            this.database.createUser(user);
            this.plugin.debug("[AUTH] Registered premium user: {}", username);
            this.fetchAndSaveSkin(uuid, username, true);
            return true;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error registering premium user {}: {}", (Object)username, (Object)e.getMessage());
            throw e;
        }
        catch (Exception e) {
            this.plugin.getLogger().error("[AUTH] Error registering premium user {}: {}", username, e.getMessage(), e);
            throw new AuthenticationException(PluginException.ErrorCode.AUTH_NOT_REGISTERED, "Premium registration failed: " + e.getMessage(), uuid, (Throwable)e);
        }
    }

    public AuthResult login(UUID uuid, String password, InetAddress address) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        Object object = this.sessionLock;
        synchronized (object) {
            try {
                if (this.isLockedOut(uuid)) {
                    long remainingTime = this.getRemainingLockoutTime(uuid);
                    return AuthResult.lockedOut(remainingTime);
                }
                User user = this.database.getUser(uuid);
                if (user == null) {
                    return AuthResult.notRegistered();
                }
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    return AuthResult.success();
                }
                if (!PasswordHasher.verify(password, user.getPassword())) {
                    this.incrementPasswordAttempts(uuid);
                    int attempts = this.passwordAttempts.getOrDefault(uuid, 0);
                    int maxAttempts = this.config.getInt("security.password-attempts.max-attempts", 3);
                    if (attempts >= maxAttempts) {
                        this.lockoutPlayer(uuid);
                        return AuthResult.lockedOut(this.getRemainingLockoutTime(uuid));
                    }
                    return AuthResult.wrongPassword(maxAttempts - attempts);
                }
                this.passwordAttempts.remove(uuid);
                user.setLastLogin(System.currentTimeMillis());
                user.setIpAddress(address.getHostAddress());
                this.database.updateUser(user);
                PlayerSession session = new PlayerSession(uuid, user.getDiscordId(), address.getHostAddress());
                session.setLastSeen(System.currentTimeMillis());
                this.activeSessions.put(uuid, session);
                this.saveSessionSafely(session);
                if (user.getSkinData() == null || user.getSkinData().isEmpty()) {
                    boolean isPremium = "PREMIUM".equals(user.getAccountType());
                    this.fetchAndSaveSkin(uuid, user.getUsername(), isPremium);
                }
                this.plugin.debug("[AUTH] User {} logged in successfully", user.getUsername());
                return AuthResult.success();
            }
            catch (DatabaseException e) {
                this.plugin.getLogger().error("[AUTH] Database error during login for {}: {}", (Object)uuid, (Object)e.getMessage());
                return AuthResult.error("Database error");
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH] Unexpected error during login for {}: {}", uuid, e.getMessage(), e);
                return AuthResult.error("Login failed");
            }
        }
    }

    public void authenticatePremium(UUID uuid, InetAddress address) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        Object object = this.sessionLock;
        synchronized (object) {
            try {
                User user = this.database.getUser(uuid);
                if (user != null) {
                    user.setLastLogin(System.currentTimeMillis());
                    user.setIpAddress(address.getHostAddress());
                    this.database.updateUser(user);
                    if (user.getSkinData() == null || user.getSkinData().isEmpty()) {
                        this.fetchAndSaveSkin(uuid, user.getUsername(), true);
                    }
                }
                String discordId = user != null ? user.getDiscordId() : null;
                PlayerSession session = new PlayerSession(uuid, discordId, address.getHostAddress());
                session.setLastSeen(System.currentTimeMillis());
                this.activeSessions.put(uuid, session);
                this.saveSessionSafely(session);
                this.plugin.debug("[AUTH] Premium authentication completed for {}", uuid);
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH] Error authenticating premium player {}: {}", uuid, e.getMessage(), e);
            }
        }
    }

    public User getUser(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        try {
            return this.database.getUser(uuid);
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error getting user {}: {}", (Object)uuid, (Object)e.getMessage());
            return null;
        }
    }

    public boolean isRegistered(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            return this.database.getUser(uuid) != null;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error checking registration for {}: {}", (Object)uuid, (Object)e.getMessage());
            return false;
        }
    }

    public String getDiscordId(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        try {
            User user = this.database.getUser(uuid);
            return user != null ? user.getDiscordId() : null;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error getting Discord ID for {}: {}", (Object)uuid, (Object)e.getMessage());
            return null;
        }
    }

    public boolean registerDiscordUser(UUID uuid, String username, String discordId, InetAddress address, boolean isPremium) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(discordId, "Discord ID cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        try {
            User existingUser = this.database.getUserByDiscordId(discordId);
            if (existingUser != null) {
                this.plugin.getLogger().warn("[SECURITY] Attempted to register {} with Discord ID {} already linked to {}", username, discordId, existingUser.getUsername());
                return false;
            }
            User user = new User(uuid);
            user.setUsername(username);
            user.setPassword(null);
            user.setDiscordId(discordId);
            user.setAccountType(isPremium ? "PREMIUM" : "CRACKED");
            user.setFirstLogin(System.currentTimeMillis());
            user.setLastLogin(System.currentTimeMillis());
            user.setIpAddress(address.getHostAddress());
            this.database.createUser(user);
            this.plugin.debug("[SECURITY] Registered new Discord user {} with ID {}", username, discordId);
            this.fetchAndSaveSkin(uuid, username, isPremium);
            return true;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error registering Discord user {}: {}", (Object)username, (Object)e.getMessage());
            throw e;
        }
    }

    public void authenticateDiscord(UUID uuid, InetAddress address) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(address, "Address cannot be null");
        Object object = this.sessionLock;
        synchronized (object) {
            try {
                User user = this.database.getUser(uuid);
                if (user != null) {
                    user.setLastLogin(System.currentTimeMillis());
                    user.setIpAddress(address.getHostAddress());
                    this.database.updateUser(user);
                    if (user.getSkinData() == null || user.getSkinData().isEmpty()) {
                        boolean isPremium = "PREMIUM".equals(user.getAccountType());
                        this.fetchAndSaveSkin(uuid, user.getUsername(), isPremium);
                    }
                }
                String discordId = user != null ? user.getDiscordId() : null;
                PlayerSession session = new PlayerSession(uuid, discordId, address.getHostAddress());
                session.setLastSeen(System.currentTimeMillis());
                this.activeSessions.put(uuid, session);
                this.saveSessionSafely(session);
                this.plugin.debug("[AUTH] Discord authentication completed for {}", uuid);
            }
            catch (Exception e) {
                this.plugin.getLogger().error("[AUTH] Error authenticating Discord user {}: {}", uuid, e.getMessage(), e);
            }
        }
    }

    public boolean linkDiscordToUser(UUID uuid, String discordId) {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(discordId, "Discord ID cannot be null");
        try {
            User existingUser = this.database.getUserByDiscordId(discordId);
            if (existingUser != null && !existingUser.getUuid().equals(uuid)) {
                this.plugin.getLogger().warn("[SECURITY] Attempted to link Discord ID {} to {} but already linked to {}", discordId, uuid, existingUser.getUsername());
                return false;
            }
            User user = this.database.getUser(uuid);
            if (user != null) {
                user.setDiscordId(discordId);
                this.database.updateUser(user);
                this.plugin.debug("[SECURITY] Linked Discord ID {} to {}", discordId, user.getUsername());
                return true;
            }
            return false;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error linking Discord to user {}: {}", (Object)uuid, (Object)e.getMessage());
            throw e;
        }
    }

    public UUID getUuidByDiscordId(String discordId) {
        if (discordId == null || discordId.isEmpty()) {
            return null;
        }
        try {
            User user = this.database.getUserByDiscordId(discordId);
            return user != null ? user.getUuid() : null;
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error getting UUID by Discord ID {}: {}", (Object)discordId, (Object)e.getMessage());
            return null;
        }
    }

    public User getUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        try {
            return this.database.getUserByUsername(username);
        }
        catch (DatabaseException e) {
            this.plugin.getLogger().error("[AUTH] Database error getting user by username {}: {}", (Object)username, (Object)e.getMessage());
            return null;
        }
    }

    private void incrementPasswordAttempts(UUID uuid) {
        this.passwordAttempts.put(uuid, this.passwordAttempts.getOrDefault(uuid, 0) + 1);
    }

    private void lockoutPlayer(UUID uuid) {
        int lockDuration = this.config.getInt("security.password-attempts.lock-duration", 15);
        this.lockoutTimes.put(uuid, System.currentTimeMillis() + (long)(lockDuration * 60) * 1000L);
        this.plugin.getLogger().warn("[SECURITY] Player {} locked out for {} minutes", (Object)uuid, (Object)lockDuration);
    }

    private boolean isLockedOut(UUID uuid) {
        Long lockoutTime = this.lockoutTimes.get(uuid);
        if (lockoutTime == null) {
            return false;
        }
        if (System.currentTimeMillis() >= lockoutTime) {
            this.lockoutTimes.remove(uuid);
            this.passwordAttempts.remove(uuid);
            return false;
        }
        return true;
    }

    private long getRemainingLockoutTime(UUID uuid) {
        Long lockoutTime = this.lockoutTimes.get(uuid);
        if (lockoutTime == null) {
            return 0L;
        }
        return Math.max(0L, lockoutTime - System.currentTimeMillis());
    }

    private long parseSessionDuration(String duration) {
        return DurationUtil.parseToMillis(duration);
    }

    private void saveSessionSafely(PlayerSession session) {
        try {
            this.database.saveSession(session);
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[AUTH] Failed to save session: {}", (Object)e.getMessage());
        }
    }

    private void deleteSessionSafely(UUID uuid) {
        try {
            this.database.deleteSession(uuid);
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[AUTH] Failed to delete session: {}", (Object)e.getMessage());
        }
    }

    public void removeSession(UUID uuid) {
        if (uuid == null) {
            return;
        }
        this.activeSessions.remove(uuid);
        this.deleteSessionSafely(uuid);
        this.passwordAttempts.remove(uuid);
        this.lockoutTimes.remove(uuid);
    }

    public void clearMemorySession(UUID uuid) {
        if (uuid == null) {
            return;
        }
        this.activeSessions.remove(uuid);
        this.passwordAttempts.remove(uuid);
        this.lockoutTimes.remove(uuid);
        this.plugin.debug("[AUTH] Cleared in-memory session for {}", uuid);
    }

    public AuthDatabase getDatabase() {
        return this.database;
    }

    private void fetchAndSaveSkin(UUID uuid, String username, boolean isPremium) {
        if (!this.config.getBoolean("skins.enabled", true)) {
            this.plugin.debug("[SKIN] Skin fetching disabled in config", new Object[0]);
            return;
        }
        this.plugin.debug("[SKIN] Starting skin fetch for {} ({})", username, isPremium ? "premium" : "cracked");
        this.plugin.getServer().getScheduler().buildTask((Object)this.plugin, () -> {
            CompletionStage<SkinFetchService.SkinData> fetchFuture;
            SkinFetchService skinService = new SkinFetchService(this.plugin, this.config, this.plugin.getLogger());
            if (isPremium) {
                UUID mojangUuid = null;
                try {
                    PremiumVerificationResult premiumResult;
                    if (this.plugin.getAuthManager() != null && (premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username)) != null) {
                        mojangUuid = premiumResult.getMojangUuid();
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
                fetchFuture = mojangUuid != null ? skinService.fetchSkinByMojangUuid(mojangUuid, username) : skinService.fetchPremiumSkinByUsername(username);
                fetchFuture = fetchFuture.thenCompose(skinData -> skinData != null ? CompletableFuture.completedFuture(skinData) : skinService.fetchSkinByUsername(username));
            } else {
                fetchFuture = skinService.fetchSkinByUsername(username);
            }
            fetchFuture.thenAccept(skinData -> {
                if (skinData == null) {
                    this.plugin.debug("[SKIN] No skin data returned for {}", username);
                    return;
                }
                try {
                    this.skinFileStorage.saveSkin(uuid, username, skinData);
                    User user = this.database.getUser(uuid);
                    if (user != null) {
                        user.setSkinData(skinData.toJson());
                        this.database.updateUser(user);
                        this.plugin.debug("[SKIN] Successfully saved skin for {} from {}", username, skinData.source);
                    } else {
                        this.plugin.debug("[SKIN] User not found in database for {}", username);
                    }
                }
                catch (Exception e) {
                    this.plugin.getLogger().warn("[SKIN] Failed to save skin for {}: {}", username, e.getMessage(), e);
                }
            }).exceptionally(e -> {
                this.plugin.getLogger().warn("[SKIN] Failed to fetch skin for {}: {}", username, e.getMessage(), e);
                return null;
            });
        }).schedule();
    }

    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final int attemptsRemaining;
        private final long lockoutTime;

        private AuthResult(boolean success, String message, int attemptsRemaining, long lockoutTime) {
            this.success = success;
            this.message = message;
            this.attemptsRemaining = attemptsRemaining;
            this.lockoutTime = lockoutTime;
        }

        public static AuthResult success() {
            return new AuthResult(true, "success", 0, 0L);
        }

        public static AuthResult notRegistered() {
            return new AuthResult(false, "not_registered", 0, 0L);
        }

        public static AuthResult wrongPassword(int remaining) {
            return new AuthResult(false, "wrong_password", remaining, 0L);
        }

        public static AuthResult lockedOut(long time) {
            return new AuthResult(false, "locked_out", 0, time);
        }

        public static AuthResult error(String message) {
            return new AuthResult(false, "error: " + message, 0, 0L);
        }

        public boolean isSuccess() {
            return this.success;
        }

        public String getMessage() {
            return this.message;
        }

        public int getAttemptsRemaining() {
            return this.attemptsRemaining;
        }

        public long getLockoutTime() {
            return this.lockoutTime;
        }
    }
}
