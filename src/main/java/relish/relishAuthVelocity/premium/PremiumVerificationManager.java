package relish.relishAuthVelocity.premium;

import com.velocitypowered.api.event.connection.LoginEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.premium.PremiumStatus;
import relish.relishAuthVelocity.premium.PremiumVerificationSession;

public class PremiumVerificationManager {
    private final RelishAuthVelocity plugin;
    private final Map<UUID, PremiumVerificationSession> verificationSessions = new ConcurrentHashMap<UUID, PremiumVerificationSession>();
    private final Map<String, PremiumVerificationSession> verificationSessionsByUsername = new ConcurrentHashMap<String, PremiumVerificationSession>();

    public PremiumVerificationManager(RelishAuthVelocity plugin) {
        this.plugin = plugin;
    }

    public void addVerificationSession(UUID uuid, PremiumVerificationSession session) {
        this.verificationSessions.put(uuid, session);
        this.verificationSessionsByUsername.put(session.getUsername().toLowerCase(), session);
    }

    public PremiumVerificationSession getVerificationSession(UUID uuid) {
        return this.verificationSessions.get(uuid);
    }

    public PremiumVerificationSession getVerificationSessionByUsername(String username) {
        return this.verificationSessionsByUsername.get(username.toLowerCase());
    }

    public void removeVerificationSession(UUID uuid) {
        PremiumVerificationSession session = this.verificationSessions.remove(uuid);
        if (session != null) {
            this.verificationSessionsByUsername.remove(session.getUsername().toLowerCase());
        }
    }

    public void handleLoginEvent(LoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PremiumVerificationSession session = this.getVerificationSession(uuid);
        if (session != null && session.getPremiumStatus() == PremiumStatus.PREMIUM_PENDING_ENCRYPTION) {
            session.setEncryptionVerified(true);
            session.setPremiumStatus(PremiumStatus.PREMIUM_VERIFIED);
            if (this.plugin.isDebugEnabled()) {
                this.plugin.getLogger().info("Encryption verification completed for {}", (Object)event.getPlayer().getUsername());
            }
        }
    }
}
