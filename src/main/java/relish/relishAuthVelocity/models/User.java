package relish.relishAuthVelocity.models;

import java.util.UUID;

public class User {
    private final UUID uuid;
    private String username;
    private String discordId;
    private long firstLogin;
    private long lastLogin;
    private String ipAddress;
    private String password;
    private String accountType;
    private boolean joinNotifications;
    private long createdAt;
    private long sessionDuration;
    private String skinData;

    public User(UUID uuid) {
        this.uuid = uuid;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDiscordId() {
        return this.discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public long getFirstLogin() {
        return this.firstLogin;
    }

    public void setFirstLogin(long firstLogin) {
        this.firstLogin = firstLogin;
    }

    public long getLastLogin() {
        return this.lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccountType() {
        return this.accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public boolean isJoinNotifications() {
        return this.joinNotifications;
    }

    public void setJoinNotifications(boolean joinNotifications) {
        this.joinNotifications = joinNotifications;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getSessionDuration() {
        return this.sessionDuration;
    }

    public void setSessionDuration(long sessionDuration) {
        this.sessionDuration = sessionDuration;
    }

    public String getSkinData() {
        return this.skinData;
    }

    public void setSkinData(String skinData) {
        this.skinData = skinData;
    }

    public boolean isPremium() {
        return "PREMIUM".equalsIgnoreCase(this.accountType);
    }
}
