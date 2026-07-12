package relish.relishAuthVelocity.services;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.util.ArrayList;
import org.slf4j.Logger;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.models.User;
import relish.relishAuthVelocity.services.SkinFetchService;

public class SkinApplier {
    private final RelishAuthVelocity plugin;
    private final Logger logger;

    public SkinApplier(RelishAuthVelocity plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void applySkinToPlayer(Player player, User user) {
        if (user == null || user.getSkinData() == null || user.getSkinData().isEmpty()) {
            this.logger.debug("[SKIN] No skin data available for {}", (Object)player.getUsername());
            return;
        }
        try {
            SkinFetchService.SkinData skinData = SkinFetchService.SkinData.fromJson(user.getSkinData());
            if (skinData == null) {
                this.logger.debug("[SKIN] Failed to parse skin data for {}", (Object)player.getUsername());
                return;
            }
            GameProfile currentProfile = player.getGameProfile();
            ArrayList<GameProfile.Property> properties = new ArrayList<GameProfile.Property>(currentProfile.getProperties());
            properties.removeIf(prop -> "textures".equals(prop.getName()));
            GameProfile.Property textureProperty = new GameProfile.Property("textures", skinData.textureData, skinData.signature == null ? "" : skinData.signature);
            properties.add(textureProperty);
            GameProfile newProfile = new GameProfile(currentProfile.getId(), currentProfile.getName(), properties);
            this.logger.debug("[SKIN] Skin data available for {} from {}: {} properties", player.getUsername(), skinData.source, properties.size());
        }
        catch (Exception e) {
            this.logger.warn("[SKIN] Failed to process skin for {}: {}", (Object)player.getUsername(), (Object)e.getMessage());
        }
    }
}
