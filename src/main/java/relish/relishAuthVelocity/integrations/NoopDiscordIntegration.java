package relish.relishAuthVelocity.integrations;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import relish.relishAuthVelocity.integrations.DiscordIntegration;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;

public final class NoopDiscordIntegration
implements DiscordIntegration {
    @Override
    public boolean initialize() {
        return false;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public DiscordUserSearchResult findUserByUsername(String username) {
        return DiscordUserSearchResult.notFound();
    }

    @Override
    public void assignLinkedRole(String discordId) {
    }

    @Override
    public CompletableFuture<Set<String>> getRoleIds(String discordId) {
        return CompletableFuture.completedFuture(Collections.emptySet());
    }

    @Override
    public void sendJoinNotification(String discordId, String playerName, UUID playerUuid) {
    }

    @Override
    public void sendVerificationRequest(String discordId, String playerName, UUID playerUuid, String serverIp, boolean isNewAccount) {
    }

    @Override
    public void sendSessionControl(String discordId, String playerName, UUID playerUuid, String serverIp, String sessionDuration) {
    }
}
