package relish.relishAuthVelocity.premium;

import java.util.UUID;
import relish.relishAuthVelocity.premium.PremiumStatus;

public class PremiumVerificationResult {
    private final PremiumStatus status;
    private final UUID mojangUuid;
    private final boolean encryptionVerified;
    private final long timestamp;

    public PremiumVerificationResult(PremiumStatus status, UUID mojangUuid, boolean encryptionVerified, long timestamp) {
        this.status = status;
        this.mojangUuid = mojangUuid;
        this.encryptionVerified = encryptionVerified;
        this.timestamp = timestamp;
    }

    public boolean isTrulyPremium() {
        return this.status == PremiumStatus.PREMIUM_VERIFIED && this.encryptionVerified;
    }

    public PremiumStatus getStatus() {
        return this.status;
    }

    public UUID getMojangUuid() {
        return this.mojangUuid;
    }

    public boolean isEncryptionVerified() {
        return this.encryptionVerified;
    }

    public long getTimestamp() {
        return this.timestamp;
    }
}
