package relish.relishAuthVelocity.integrations;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import relish.relishAuthVelocity.integrations.DiscordUserSearchResult;

public interface DiscordIntegration {
    public boolean initialize();

    public void shutdown();

    public boolean isEnabled();

    public DiscordUserSearchResult findUserByUsername(String var1);

    public void assignLinkedRole(String var1);

    public CompletableFuture<Set<String>> getRoleIds(String var1);

    public void sendJoinNotification(String var1, String var2, UUID var3);

    public void sendVerificationRequest(String var1, String var2, UUID var3, String var4, boolean var5);

    public void sendSessionControl(String var1, String var2, UUID var3, String var4, String var5);
}
