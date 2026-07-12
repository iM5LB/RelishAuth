package relish.relishAuthVelocity.services;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.premium.PremiumVerificationResult;
import relish.relishAuthVelocity.services.SkinFetchService;
import relish.relishAuthVelocity.services.SkinFileStorage;
import relish.relishAuthVelocity.services.SkinPayloadUtil;

public final class SkinCacheResolver {
    private final RelishAuthVelocity plugin;
    private final Logger logger;
    private final SkinFileStorage skinFileStorage;

    public SkinCacheResolver(RelishAuthVelocity plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.skinFileStorage = new SkinFileStorage(plugin, logger);
    }

    public SkinFetchService.SkinData resolve(UUID profileUuid, String username, String logTag, boolean logSelection) {
        if (profileUuid == null || username == null || username.isBlank()) {
            return null;
        }
        String tag = logTag == null || logTag.isBlank() ? "[SKIN]" : logTag;
        SkinFetchService.SkinData best = this.selectBest(this.validateCandidate(this.loadSkinFromDatabase(profileUuid), username, "db:" + String.valueOf(profileUuid), tag, logSelection), this.validateCandidate(this.loadSkinFromFile(profileUuid), username, "file:" + String.valueOf(profileUuid), tag, logSelection));
        if (best != null) {
            return best;
        }
        UUID offlineUuid = SkinCacheResolver.generateOfflineUuid(username);
        if (!offlineUuid.equals(profileUuid) && (best = this.selectBest(this.validateCandidate(this.loadSkinFromDatabase(offlineUuid), username, "db-offline:" + String.valueOf(offlineUuid), tag, logSelection), this.validateCandidate(this.loadSkinFromFile(offlineUuid), username, "file-offline:" + String.valueOf(offlineUuid), tag, logSelection))) != null) {
            return best;
        }
        try {
            SkinFetchService.SkinData mojangCandidate;
            UUID mojangUuid = this.getCachedMojangUuid(username);
            if (mojangUuid != null && !mojangUuid.equals(profileUuid) && !mojangUuid.equals(offlineUuid) && (mojangCandidate = this.validateCandidate(this.loadSkinFromFile(mojangUuid), username, "file-mojang:" + String.valueOf(mojangUuid), tag, logSelection)) != null) {
                return mojangCandidate;
            }
        }
        catch (Exception e) {
            this.logger.debug("{} Failed premium UUID fallback for {}: {}", tag, username, e.getMessage());
        }
        return null;
    }

    public SkinFetchService.SkinData normalizeUnsignedSkinData(SkinFetchService.SkinData skinData, UUID profileUuid, String username) {
        String withCape;
        if (skinData == null || skinData.textureData == null || skinData.textureData.isBlank() || profileUuid == null || username == null || username.isBlank()) {
            return skinData;
        }
        String signature = skinData.signature;
        if (signature != null && !signature.isEmpty()) {
            return skinData;
        }
        String updated = SkinPayloadUtil.ensureProfileFields(skinData.textureData, profileUuid, username);
        if (updated == null || updated.isBlank()) {
            updated = skinData.textureData;
        }
        String defaultUnsignedCape = null;
        try {
            if (this.plugin.getConfig() != null) {
                defaultUnsignedCape = this.plugin.getConfig().getString("skins.capes.default-unsigned-cape", "");
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (defaultUnsignedCape != null && !defaultUnsignedCape.isBlank() && (withCape = SkinPayloadUtil.injectCapeIfMissing(updated, defaultUnsignedCape)) != null && !withCape.isBlank()) {
            updated = withCape;
        }
        if (updated.equals(skinData.textureData)) {
            return skinData;
        }
        return new SkinFetchService.SkinData(updated, "", skinData.source, skinData.fetchedAt);
    }

    public static UUID generateOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private SkinFetchService.SkinData loadSkinFromDatabase(UUID uuid) {
        try {
            if (this.plugin.getAuthService() == null) {
                return null;
            }
            User user = this.plugin.getAuthService().getUser(uuid);
            if (user == null || user.getSkinData() == null || user.getSkinData().isBlank()) {
                return null;
            }
            return SkinFetchService.SkinData.fromJson(user.getSkinData());
        }
        catch (Exception e) {
            return null;
        }
    }

    private SkinFetchService.SkinData loadSkinFromFile(UUID uuid) {
        try {
            return this.skinFileStorage.loadSkin(uuid);
        }
        catch (Exception e) {
            return null;
        }
    }

    private UUID getCachedMojangUuid(String username) {
        if (this.plugin.getAuthManager() == null) {
            return null;
        }
        PremiumVerificationResult premiumResult = this.plugin.getAuthManager().getPremiumVerificationResultByUsername(username);
        return premiumResult != null ? premiumResult.getMojangUuid() : null;
    }

    private SkinFetchService.SkinData validateCandidate(SkinFetchService.SkinData skinData, String username, String candidateSource, String logTag, boolean logSelection) {
        if (skinData == null || skinData.textureData == null || skinData.textureData.isBlank()) {
            return null;
        }
        String payloadProfileName = SkinPayloadUtil.tryExtractProfileName(skinData.textureData);
        if (payloadProfileName != null && !payloadProfileName.isBlank() && !payloadProfileName.equalsIgnoreCase(username)) {
            this.logger.warn("{} Refusing cached skin ({}): username={}, payloadProfileName={}, source={}", logTag, candidateSource, username, payloadProfileName, skinData.source);
            return null;
        }
        if (logSelection) {
            if (payloadProfileName != null && !payloadProfileName.isBlank()) {
                this.plugin.debug("{} Selected skin ({}): username={}, payloadProfileName={}, source={}", logTag, candidateSource, username, payloadProfileName, skinData.source);
            } else {
                this.plugin.debug("{} Selected skin ({}): username={}, source={}", logTag, candidateSource, username, skinData.source);
            }
        }
        return skinData;
    }

    private SkinFetchService.SkinData selectBest(SkinFetchService.SkinData a, SkinFetchService.SkinData b) {
        boolean bSigned;
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aSigned = a.signature != null && !a.signature.isEmpty();
        boolean bl = bSigned = b.signature != null && !b.signature.isEmpty();
        if (aSigned != bSigned) {
            return aSigned ? a : b;
        }
        return a.fetchedAt >= b.fetchedAt ? a : b;
    }
}
