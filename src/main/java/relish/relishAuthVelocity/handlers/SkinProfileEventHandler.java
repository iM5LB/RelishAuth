package relish.relishAuthVelocity.handlers;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.premium.PremiumStatus;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.services.SkinCacheResolver;
import relish.relishAuthVelocity.services.SkinFetchService;
import relish.relishAuthVelocity.services.SkinPayloadUtil;

public final class SkinProfileEventHandler {
    private final RelishAuthVelocity plugin;
    private final SkinCacheResolver skinCacheResolver;

    public SkinProfileEventHandler(RelishAuthVelocity plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.skinCacheResolver = new SkinCacheResolver(plugin, Objects.requireNonNull(plugin.getLogger(), "logger"));
    }

    @Subscribe(order=PostOrder.LAST)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        boolean isBedrock;
        if (this.plugin.getConfig() == null) {
            return;
        }
        boolean skinsEnabled = this.plugin.getConfig().getBoolean("skins.enabled", true);
        boolean injectOfficialUuid = this.plugin.getConfig().getBoolean("authentication.premium-use-official-uuid", false);
        boolean preserveExistingTextures = this.plugin.getConfig().getBoolean("skins.preserve-existing-textures", true);
        if (!skinsEnabled && !injectOfficialUuid) {
            return;
        }
        GameProfile profile = event.getGameProfile();
        if (profile == null) {
            return;
        }
        UUID uuid = profile.getId();
        String username = profile.getName();
        if (uuid == null || username == null || username.isBlank()) {
            return;
        }
        UUID finalUuid = uuid;
        boolean bl = isBedrock = this.plugin.getFloodgateHelper() != null && this.plugin.getFloodgateHelper().isFloodgatePlayer(uuid);
        if (injectOfficialUuid) {
            UUID mojangUuid;
            PremiumVerificationResult premiumResult;
            PremiumVerificationResult premiumVerificationResult = premiumResult = this.plugin.getAuthManager() != null ? this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username) : null;
            if (this.shouldInjectOfficialUuid(premiumResult) && (mojangUuid = premiumResult.getMojangUuid()) != null && !mojangUuid.equals(finalUuid)) {
                this.maybeMigrateDatabaseUuid(username, mojangUuid);
                this.plugin.debug("[UUID-INJECT] Using Mojang UUID for {}: {} -> {}", username, finalUuid, mojangUuid);
                finalUuid = mojangUuid;
            }
        }
        ArrayList<GameProfile.Property> properties = new ArrayList<GameProfile.Property>(profile.getProperties());
        if (skinsEnabled) {
            boolean hasAnyTexture;
            if (isBedrock) {
                if (!finalUuid.equals(uuid)) {
                    event.setGameProfile(new GameProfile(finalUuid, username, properties));
                }
                return;
            }
            boolean hasTexturesProperty = properties.stream().anyMatch(prop -> prop != null && "textures".equalsIgnoreCase(prop.getName()) && prop.getValue() != null && !prop.getValue().isBlank());
            if (preserveExistingTextures && hasTexturesProperty) {
                this.plugin.debug("[SKIN-PROFILE] Keeping existing textures for {}", username);
                if (!finalUuid.equals(uuid)) {
                    event.setGameProfile(new GameProfile(finalUuid, username, properties));
                }
                return;
            }
            SkinFetchService.SkinData skinData = this.skinCacheResolver.resolve(finalUuid, username, "[SKIN-PROFILE]", true);
            if (skinData == null || skinData.textureData == null || skinData.textureData.isBlank()) {
                if (!finalUuid.equals(uuid)) {
                    event.setGameProfile(new GameProfile(finalUuid, username, properties));
                }
                return;
            }
            skinData = this.skinCacheResolver.normalizeUnsignedSkinData(skinData, finalUuid, username);
            String extractedSkinUrl = SkinPayloadUtil.tryExtractSkinUrl(skinData.textureData);
            String extractedCapeUrl = SkinPayloadUtil.tryExtractCapeUrl(skinData.textureData);
            boolean bl2 = hasAnyTexture = extractedSkinUrl != null && !extractedSkinUrl.isBlank() || extractedCapeUrl != null && !extractedCapeUrl.isBlank();
            if (!hasAnyTexture) {
                this.plugin.getLogger().warn("[SKIN-PROFILE] Refusing to inject invalid textures payload for {} (uuid: {}) (source: {}).", username, finalUuid, skinData.source);
                if (!finalUuid.equals(uuid)) {
                    event.setGameProfile(new GameProfile(finalUuid, username, properties));
                }
                return;
            }
            String signature = skinData.signature == null ? "" : skinData.signature;
            properties.removeIf(prop -> "textures".equalsIgnoreCase(prop.getName()));
            properties.add(new GameProfile.Property("textures", skinData.textureData, signature));
            if (extractedSkinUrl != null && !extractedSkinUrl.isBlank()) {
                if (extractedCapeUrl != null && !extractedCapeUrl.isBlank()) {
                    this.plugin.debug("[SKIN-PROFILE] Restored skin for {} (uuid: {}) (source: {}) (skinUrl: {}) (capeUrl: {})", username, finalUuid, skinData.source, extractedSkinUrl, extractedCapeUrl);
                } else {
                    this.plugin.debug("[SKIN-PROFILE] Restored skin for {} (uuid: {}) (source: {}) (skinUrl: {})", username, finalUuid, skinData.source, extractedSkinUrl);
                }
            } else if (extractedCapeUrl != null && !extractedCapeUrl.isBlank()) {
                this.plugin.debug("[SKIN-PROFILE] Restored skin for {} (uuid: {}) (source: {}) (capeUrl: {})", username, finalUuid, skinData.source, extractedCapeUrl);
            } else {
                this.plugin.debug("[SKIN-PROFILE] Restored skin for {} (uuid: {}) (source: {})", username, finalUuid, skinData.source);
            }
        }
        if (!finalUuid.equals(uuid) || skinsEnabled) {
            event.setGameProfile(new GameProfile(finalUuid, username, properties));
        }
    }

    private boolean shouldInjectOfficialUuid(PremiumVerificationResult result) {
        if (result == null || result.getMojangUuid() == null) {
            return false;
        }
        PremiumStatus status = result.getStatus();
        return status == PremiumStatus.PREMIUM_VERIFIED || status == PremiumStatus.PREMIUM_PENDING_ENCRYPTION;
    }

    private void maybeMigrateDatabaseUuid(String username, UUID mojangUuid) {
        if (this.plugin.getConfig() == null || mojangUuid == null) {
            return;
        }
        boolean migrate = this.plugin.getConfig().getBoolean("authentication.premium-use-official-uuid-migrate-database", true);
        if (!migrate) {
            return;
        }
        try {
            if (this.plugin.getDatabase() == null) {
                return;
            }
            UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
            if (offlineUuid.equals(mojangUuid)) {
                return;
            }
            boolean migrated = this.plugin.getDatabase().migrateUserUuid(offlineUuid, mojangUuid);
            if (migrated) {
                this.plugin.debug("[UUID-INJECT] Migrated RelishAuth UUID for {}: {} -> {}", username, offlineUuid, mojangUuid);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warn("[UUID-INJECT] Failed to migrate RelishAuth UUID for {}: {}", (Object)username, (Object)e.getMessage());
        }
    }
}
