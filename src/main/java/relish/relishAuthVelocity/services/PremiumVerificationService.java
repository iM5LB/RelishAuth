package relish.relishAuthVelocity.services;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;
import relish.relishAuthVelocity.premium.PremiumStatus;
import relish.relishAuthVelocity.premium.PremiumVerificationManager;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.premium.PremiumVerificationSession;
import relish.relishAuthVelocity.services.SkinCacheResolver;
import relish.relishAuthVelocity.services.SkinFetchService;
import relish.relishAuthVelocity.services.SkinFileStorage;

public class PremiumVerificationService {
    private static final String VERIFY_TAG = "[ACCOUNT-VERIFY]";
    private static final String LOOKUP_TAG = "[PROFILE-LOOKUP]";
    private final RelishAuthVelocity plugin;
    private final Config config;
    private final PremiumVerificationManager premiumManager;

    public PremiumVerificationService(RelishAuthVelocity plugin, Config config, PremiumVerificationManager premiumManager) {
        this.plugin = plugin;
        this.config = config;
        this.premiumManager = premiumManager;
    }

    private String getMojangApiUrl() {
        String endpoint = this.config.getString("skins.api.username-uuid-lookup-endpoint", "");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = this.config.getString("skins.api.mojang-profile-lookup-endpoint", "");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = this.config.getString("security.premium.api-url", "");
        }
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
        }
        return endpoint;
    }

    public void handlePreLogin(PreLoginEvent event, UUID connectionUuid, String username, InetAddress address) {
        boolean isBedrock;
        this.plugin.getLogger().info("{} Starting account verification for {} ({})", VERIFY_TAG, username, connectionUuid);
        boolean bl = isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(connectionUuid);
        if (isBedrock) {
            this.plugin.debug("{} {} detected as Floodgate/Bedrock player; skipping Java profile lookup", VERIFY_TAG, username);
        }
        UUID mojangUuid = isBedrock ? null : this.fetchMojangUUID(username);
        boolean isPremium = mojangUuid != null;
        this.plugin.getLogger().info("{} Lookup result for {}: premium={}, mojangUuid={}", VERIFY_TAG, username, isPremium, mojangUuid);
        if (isPremium) {
            boolean allowPremiumUsernameImpersonation;
            UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
            boolean isUsingOfflineUuid = connectionUuid.equals(offlineUuid);
            this.plugin.getLogger().info("{} Premium username check for {} -> connectionUuid={}, mojangUuid={}, offlineUuid={}, usingOfflineUuid={}", VERIFY_TAG, username, connectionUuid, mojangUuid, offlineUuid, isUsingOfflineUuid);
            if (isUsingOfflineUuid && !(allowPremiumUsernameImpersonation = this.isPremiumUsernameImpersonationAllowed())) {
                this.plugin.getLogger().warn("[SECURITY] Blocking offline connection for premium username: {}", (Object)username);
                Component kickMessage = this.plugin.getMessageManager() != null ? Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), this.plugin.getMessageManager().getMessageList("premium-offline-blocked", new String[0])) : Component.text((String)"This username is registered as a premium account.\nPlease connect with a valid Minecraft account.", (TextColor)NamedTextColor.RED);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied((Component)kickMessage));
                return;
            }
        }
        PremiumVerificationSession session = new PremiumVerificationSession(connectionUuid, username, isPremium, System.currentTimeMillis());
        if (isPremium) {
            this.plugin.getLogger().info("{} {} resolved as premium", (Object)VERIFY_TAG, (Object)username);
            session.setPremiumStatus(PremiumStatus.PREMIUM_VERIFIED);
            session.setMojangUuid(mojangUuid);
            PremiumVerificationResult result = new PremiumVerificationResult(PremiumStatus.PREMIUM_VERIFIED, mojangUuid, true, System.currentTimeMillis());
            this.plugin.getAuthManager().cachePremiumVerificationResultByUsername(username, result);
            this.fetchAndSavePremiumSkin(connectionUuid, mojangUuid, username);
            this.plugin.getLogger().info("{} Premium verification completed for {} with Mojang UUID {}", VERIFY_TAG, username, mojangUuid);
        } else {
            this.plugin.getLogger().info("{} {} resolved as offline/non-premium", (Object)VERIFY_TAG, (Object)username);
            session.setPremiumStatus(PremiumStatus.CRACKED);
            PremiumVerificationResult result = new PremiumVerificationResult(PremiumStatus.CRACKED, null, false, System.currentTimeMillis());
            this.plugin.getAuthManager().cachePremiumVerificationResultByUsername(username, result);
            if (!isBedrock) {
                this.fetchAndSaveCrackedSkin(username);
            }
        }
        this.premiumManager.addVerificationSession(connectionUuid, session);
        this.plugin.getLogger().info("{} Verification session stored for {}", (Object)VERIFY_TAG, (Object)username);
        this.plugin.getLogger().info("{} Account verification completed for {}", (Object)VERIFY_TAG, (Object)username);
    }

    private boolean isPremiumUsernameImpersonationAllowed() {
        if (this.config.contains("authentication.allow-premium-username-impersonation")) {
            return this.config.getBoolean("authentication.allow-premium-username-impersonation", false);
        }
        return this.config.getBoolean("authentication.allow-premium-offline", false);
    }

    private UUID fetchMojangUUID(String username) {
        HttpURLConnection connection = null;
        try {
            Object urlString;
            String endpoint = this.getMojangApiUrl();
            if (endpoint != null && endpoint.contains("{")) {
                urlString = endpoint.replace("{nickname}", username).replace("{username}", username);
            } else {
                Object base;
                Object object = base = endpoint != null ? endpoint.trim() : "";
                if (!((String)base).endsWith("/")) {
                    base = (String)base + "/";
                }
                urlString = (String)base + username;
            }
            URL url = new URL((String)urlString);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(this.config.getInt("security.premium.api-connect-timeout", 5000));
            connection.setReadTimeout(this.config.getInt("security.premium.api-read-timeout", 5000));
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));){
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    String uuidString = json.get("id").getAsString();
                    String formattedUuid = uuidString.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                    this.plugin.getLogger().info("{} Profile lookup returned UUID {} for {}", LOOKUP_TAG, formattedUuid, username);
                    UUID uUID = UUID.fromString(formattedUuid);
                    return uUID;
                }
            }
            this.plugin.getLogger().warn("{} Profile lookup returned HTTP {} for {}", LOOKUP_TAG, responseCode, username);
        }
        catch (Exception e) {
            this.plugin.getLogger().error("{} Error looking up UUID for {}: {}", LOOKUP_TAG, username, e.getMessage());
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private void fetchAndSavePremiumSkin(UUID connectionUuid, UUID mojangUuid, String username) {
        SkinFetchService.SkinData cached;
        if (!this.config.getBoolean("skins.enabled", true)) {
            this.plugin.getLogger().info("[SKIN] Skin fetching disabled for premium player {}", (Object)username);
            return;
        }
        SkinFetchService skinService = new SkinFetchService(this.plugin, this.config, this.plugin.getLogger());
        SkinFileStorage skinStorage = new SkinFileStorage(this.plugin, this.plugin.getLogger());
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        long cacheDurationSeconds = Math.max(0L, this.config.getLong("skins.cache-duration", 604800L));
        long cacheMaxAgeMillis = cacheDurationSeconds * 1000L;
        if (cacheMaxAgeMillis > 0L && skinStorage.isSkinFresh(offlineUuid, cacheMaxAgeMillis) && (cached = skinStorage.loadSkin(offlineUuid)) != null && cached.signature != null && !cached.signature.isEmpty()) {
            this.plugin.getLogger().debug("[SKIN] Using cached skin for premium player {}", (Object)username);
            return;
        }
        CompletableFuture<SkinFetchService.SkinData> fetchFuture = mojangUuid != null ? skinService.fetchSkinByMojangUuid(mojangUuid, username).thenCompose(skinData -> skinData != null ? CompletableFuture.completedFuture(skinData) : skinService.fetchSkinByUsername(username)) : skinService.fetchSkinByUsername(username);
        LinkedHashSet<UUID> saveTargets = new LinkedHashSet<UUID>();
        saveTargets.add(connectionUuid);
        saveTargets.add(offlineUuid);
        saveTargets.add(mojangUuid);
        saveTargets.remove(null);
        int loginWaitSeconds = Math.max(0, this.config.getInt("skins.api.login-wait-timeout", 3));
        try {
            SkinFetchService.SkinData skinData2;
            if (loginWaitSeconds > 0 && (skinData2 = (SkinFetchService.SkinData)fetchFuture.get(loginWaitSeconds, TimeUnit.SECONDS)) != null) {
                for (UUID target : saveTargets) {
                    skinStorage.saveSkin(target, username, skinData2);
                }
                return;
            }
        }
        catch (TimeoutException skinData2) {
        }
        catch (Exception e2) {
            this.plugin.getLogger().warn("[SKIN] Failed to fetch premium skin for {}: {}", username, e2.getMessage(), e2);
            return;
        }
        fetchFuture.thenAccept(skinData -> {
            if (skinData == null) {
                this.plugin.getLogger().debug("[SKIN] No skin data returned for {}", (Object)username);
                return;
            }
            for (UUID target : saveTargets) {
                skinStorage.saveSkin(target, username, skinData);
            }
        }).exceptionally(e -> {
            this.plugin.getLogger().warn("[SKIN] Failed to fetch skin for {}: {}", username, e.getMessage(), e);
            return null;
        });
    }

    private void fetchAndSaveCrackedSkin(String username) {
        if (!this.config.getBoolean("skins.enabled", true)) {
            this.plugin.getLogger().info("[SKIN] Skin fetching disabled for cracked player {}", (Object)username);
            return;
        }
        SkinFetchService skinService = new SkinFetchService(this.plugin, this.config, this.plugin.getLogger());
        SkinFileStorage skinStorage = new SkinFileStorage(this.plugin, this.plugin.getLogger());
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        long cacheDurationSeconds = Math.max(0L, this.config.getLong("skins.cache-duration", 604800L));
        long cacheMaxAgeMillis = cacheDurationSeconds * 1000L;
        if (cacheMaxAgeMillis > 0L && skinStorage.isSkinFresh(offlineUuid, cacheMaxAgeMillis)) {
            this.plugin.getLogger().debug("[SKIN] Using cached skin for cracked player {}", (Object)username);
            return;
        }
        CompletableFuture<SkinFetchService.SkinData> fetchFuture = skinService.fetchSkinByUsername(username);
        int loginWaitSeconds = Math.max(0, this.config.getInt("skins.api.login-wait-timeout", 3));
        try {
            SkinFetchService.SkinData skinData2;
            if (loginWaitSeconds > 0 && (skinData2 = fetchFuture.get(loginWaitSeconds, TimeUnit.SECONDS)) != null) {
                skinStorage.saveSkin(offlineUuid, username, skinData2);
                return;
            }
        }
        catch (TimeoutException skinData2) {
        }
        catch (Exception e2) {
            this.plugin.getLogger().warn("[SKIN] Failed to fetch cracked skin for {}: {}", username, e2.getMessage(), e2);
            return;
        }
        fetchFuture.thenAccept(skinData -> {
            if (skinData == null) {
                this.plugin.getLogger().debug("[SKIN] No skin data returned for {}", (Object)username);
                return;
            }
            skinStorage.saveSkin(offlineUuid, username, skinData);
            this.plugin.getLogger().debug("[SKIN] Saved skin for {} ({})", (Object)username, (Object)offlineUuid);
        }).exceptionally(e -> {
            this.plugin.getLogger().warn("[SKIN] Failed to fetch skin for {}: {}", username, e.getMessage(), e);
            return null;
        });
    }
}
