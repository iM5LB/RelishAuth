package relish.relishAuthVelocity.models;

import java.util.UUID;

public class PlayerSession {
    private UUID uuid;
    private String discordId;
    private String ipAddress;
    private long lastSeen;

    public PlayerSession(UUID uuid, String ipAddress) {
        this.uuid = uuid;
        this.ipAddress = ipAddress;
        this.lastSeen = System.currentTimeMillis();
    }

    public PlayerSession(UUID uuid, String discordId, String ipAddress) {
        this.uuid = uuid;
        this.discordId = discordId;
        this.ipAddress = ipAddress;
        this.lastSeen = System.currentTimeMillis();
    }

    public boolean isExpired(long sessionDuration) {
        if (sessionDuration == 0L) {
            return true;
        }
        return System.currentTimeMillis() - this.lastSeen > sessionDuration;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getDiscordId() {
        return this.discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public long getLastSeen() {
        return this.lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isAuthenticated() {
        return true;
    }
}
