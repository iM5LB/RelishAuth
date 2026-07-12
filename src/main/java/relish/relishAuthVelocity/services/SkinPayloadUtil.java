package relish.relishAuthVelocity.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class SkinPayloadUtil {
    private SkinPayloadUtil() {
    }

    public static String tryExtractProfileName(String textureDataBase64) {
        JsonObject obj = SkinPayloadUtil.tryDecodePayloadObject(textureDataBase64);
        if (obj == null) {
            return null;
        }
        JsonElement name = obj.get("profileName");
        return name != null && name.isJsonPrimitive() ? name.getAsString() : null;
    }

    public static String tryExtractSkinUrl(String textureDataBase64) {
        JsonObject skin;
        JsonObject textures = SkinPayloadUtil.tryGetTexturesObject(textureDataBase64);
        if (textures == null) {
            return null;
        }
        JsonObject jsonObject = skin = textures.has("SKIN") && textures.get("SKIN").isJsonObject() ? textures.getAsJsonObject("SKIN") : null;
        if (skin == null) {
            return null;
        }
        JsonElement url = skin.get("url");
        return url != null && url.isJsonPrimitive() ? url.getAsString() : null;
    }

    public static String tryExtractCapeUrl(String textureDataBase64) {
        JsonObject cape;
        JsonObject textures = SkinPayloadUtil.tryGetTexturesObject(textureDataBase64);
        if (textures == null) {
            return null;
        }
        JsonObject jsonObject = cape = textures.has("CAPE") && textures.get("CAPE").isJsonObject() ? textures.getAsJsonObject("CAPE") : null;
        if (cape == null) {
            return null;
        }
        JsonElement url = cape.get("url");
        return url != null && url.isJsonPrimitive() ? url.getAsString() : null;
    }

    public static String ensureProfileFields(String textureDataBase64, UUID profileUuid, String username) {
        if (textureDataBase64 == null || textureDataBase64.isBlank() || profileUuid == null || username == null || username.isBlank()) {
            return textureDataBase64;
        }
        JsonObject obj = SkinPayloadUtil.tryDecodePayloadObject(textureDataBase64);
        if (obj == null) {
            return textureDataBase64;
        }
        if (!obj.has("textures") || !obj.get("textures").isJsonObject()) {
            return textureDataBase64;
        }
        boolean changed = false;
        String desiredProfileId = SkinPayloadUtil.undashed(profileUuid);
        String existingProfileId = null;
        if (obj.has("profileId") && obj.get("profileId").isJsonPrimitive()) {
            existingProfileId = obj.get("profileId").getAsString();
        }
        if (existingProfileId == null || existingProfileId.isBlank() || !existingProfileId.equalsIgnoreCase(desiredProfileId)) {
            obj.addProperty("profileId", desiredProfileId);
            changed = true;
        }
        String existingProfileName = null;
        if (obj.has("profileName") && obj.get("profileName").isJsonPrimitive()) {
            existingProfileName = obj.get("profileName").getAsString();
        }
        if (existingProfileName == null || existingProfileName.isBlank() || !existingProfileName.equalsIgnoreCase(username)) {
            obj.addProperty("profileName", username);
            changed = true;
        }
        if (!obj.has("timestamp") || !obj.get("timestamp").isJsonPrimitive()) {
            obj.addProperty("timestamp", System.currentTimeMillis());
            changed = true;
        }
        if (!changed) {
            return textureDataBase64;
        }
        return Base64.getEncoder().encodeToString(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String injectCapeIfMissing(String textureDataBase64, String capeUrlOrHash) {
        JsonObject cape;
        JsonElement url;
        if (textureDataBase64 == null || textureDataBase64.isBlank()) {
            return textureDataBase64;
        }
        String capeUrl = SkinPayloadUtil.normalizeCapeUrl(capeUrlOrHash);
        if (capeUrl == null || capeUrl.isBlank()) {
            return textureDataBase64;
        }
        JsonObject obj = SkinPayloadUtil.tryDecodePayloadObject(textureDataBase64);
        if (obj == null) {
            return textureDataBase64;
        }
        if (!obj.has("textures") || !obj.get("textures").isJsonObject()) {
            return textureDataBase64;
        }
        JsonObject textures = obj.getAsJsonObject("textures");
        if (textures.has("CAPE") && textures.get("CAPE").isJsonObject() && (url = (cape = textures.getAsJsonObject("CAPE")).get("url")) != null && url.isJsonPrimitive() && !url.getAsString().isBlank()) {
            return textureDataBase64;
        }
        cape = new JsonObject();
        cape.addProperty("url", capeUrl);
        textures.add("CAPE", cape);
        obj.addProperty("timestamp", System.currentTimeMillis());
        return Base64.getEncoder().encodeToString(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject tryGetTexturesObject(String textureDataBase64) {
        JsonObject obj = SkinPayloadUtil.tryDecodePayloadObject(textureDataBase64);
        if (obj == null) {
            return null;
        }
        return obj.has("textures") && obj.get("textures").isJsonObject() ? obj.getAsJsonObject("textures") : null;
    }

    private static JsonObject tryDecodePayloadObject(String textureDataBase64) {
        if (textureDataBase64 == null || textureDataBase64.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(textureDataBase64);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeCapeUrl(String capeUrlOrHash) {
        if (capeUrlOrHash == null) {
            return null;
        }
        String trimmed = capeUrlOrHash.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.matches("(?i)^[0-9a-f]{32,128}$")) {
            return "http://textures.minecraft.net/texture/" + trimmed;
        }
        return null;
    }

    private static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
