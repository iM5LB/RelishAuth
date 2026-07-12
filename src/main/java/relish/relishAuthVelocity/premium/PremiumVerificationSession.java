package relish.relishAuthVelocity.premium;

import java.util.UUID;
import relish.relishAuthVelocity.premium.PremiumStatus;

public class PremiumVerificationSession {
    private final UUID uuid;
    private final String username;
    private final boolean hasPremiumUUID;
    private final long startTime;
    private PremiumStatus premiumStatus = PremiumStatus.UNKNOWN;
    private UUID mojangUuid;
    private byte[] verifyToken;
    private String serverId;
    private boolean encryptionChallengeStarted = false;
    private boolean encryptionVerified = false;

    public PremiumVerificationSession(UUID uuid, String username, boolean hasPremiumUUID, long startTime) {
        this.uuid = uuid;
        this.username = username;
        this.hasPremiumUUID = hasPremiumUUID;
        this.startTime = startTime;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public String getUsername() {
        return this.username;
    }

    public boolean hasPremiumUUID() {
        return this.hasPremiumUUID;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public PremiumStatus getPremiumStatus() {
        return this.premiumStatus;
    }

    public void setPremiumStatus(PremiumStatus premiumStatus) {
        this.premiumStatus = premiumStatus;
    }

    public UUID getMojangUuid() {
        return this.mojangUuid;
    }

    public void setMojangUuid(UUID mojangUuid) {
        this.mojangUuid = mojangUuid;
    }

    public byte[] getVerifyToken() {
        return this.verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getServerId() {
        return this.serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isEncryptionChallengeStarted() {
        return this.encryptionChallengeStarted;
    }

    public void setEncryptionChallengeStarted(boolean encryptionChallengeStarted) {
        this.encryptionChallengeStarted = encryptionChallengeStarted;
    }

    public boolean isEncryptionVerified() {
        return this.encryptionVerified;
    }

    public void setEncryptionVerified(boolean encryptionVerified) {
        this.encryptionVerified = encryptionVerified;
    }
}
