package relish.relishAuthVelocity.services;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.UUID;
import org.slf4j.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.services.SkinFetchService;

public class SkinFileStorage {
    private final RelishAuthVelocity plugin;
    private final Logger logger;
    private final Path skinsDirectory;
    private final Gson gson;

    public SkinFileStorage(RelishAuthVelocity plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.gson = new Gson();
        this.skinsDirectory = plugin.getDataDirectory().resolve("skins");
        try {
            Files.createDirectories(this.skinsDirectory, new FileAttribute[0]);
            logger.debug("[SKIN-STORAGE] Skins directory created at: {}", (Object)this.skinsDirectory);
        }
        catch (IOException e) {
            logger.warn("[SKIN-STORAGE] Failed to create skins directory: {}", (Object)e.getMessage());
        }
    }

    public void saveSkin(UUID uuid, String username, SkinFetchService.SkinData skinData) {
        if (skinData == null) {
            this.logger.debug("[SKIN-STORAGE] No skin data to save for {}", (Object)username);
            return;
        }
        try {
            File skinFile = this.skinsDirectory.resolve(String.valueOf(uuid) + ".playerskin").toFile();
            JsonObject skinJson = new JsonObject();
            skinJson.addProperty("uniqueId", uuid.toString());
            skinJson.addProperty("lastKnownName", username);
            skinJson.addProperty("value", skinData.textureData);
            skinJson.addProperty("signature", skinData.signature == null ? "" : skinData.signature);
            skinJson.addProperty("timestamp", System.currentTimeMillis());
            skinJson.addProperty("dataVersion", 1);
            try (FileWriter writer = new FileWriter(skinFile);){
                this.gson.toJson((JsonElement)skinJson, (Appendable)writer);
            }
            this.logger.debug("[SKIN-STORAGE] Saved skin for {} ({}) to file", (Object)username, (Object)uuid);
        }
        catch (IOException e) {
            this.logger.warn("[SKIN-STORAGE] Failed to save skin for {}: {}", (Object)username, (Object)e.getMessage());
        }
    }

    public SkinFetchService.SkinData loadSkin(UUID uuid) {
        File skinFile = this.skinsDirectory.resolve(String.valueOf(uuid) + ".playerskin").toFile();
        if (!skinFile.exists()) {
            this.logger.debug("[SKIN-STORAGE] No skin file found for {}", (Object)uuid);
            return null;
        }
        try {
            String content = Files.readString(skinFile.toPath(), StandardCharsets.UTF_8);
            JsonObject skinJson = this.gson.fromJson(content, JsonObject.class);
            if (skinJson == null || !skinJson.has("value") || skinJson.get("value").isJsonNull()) {
                return null;
            }
            String textureData = skinJson.get("value").getAsString();
            if (textureData == null || textureData.isBlank()) {
                return null;
            }
            String signature = "";
            if (skinJson.has("signature") && !skinJson.get("signature").isJsonNull()) {
                signature = skinJson.get("signature").getAsString();
            }
            long timestamp = skinFile.lastModified();
            if (skinJson.has("timestamp") && !skinJson.get("timestamp").isJsonNull()) {
                try {
                    timestamp = skinJson.get("timestamp").getAsLong();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            this.logger.debug("[SKIN-STORAGE] Loaded skin for {}", (Object)uuid);
            return new SkinFetchService.SkinData(textureData, signature, "file", timestamp);
        }
        catch (Exception e) {
            this.logger.warn("[SKIN-STORAGE] Failed to load skin for {}: {}", (Object)uuid, (Object)e.getMessage());
            return null;
        }
    }

    public boolean hasSkin(UUID uuid) {
        return this.skinsDirectory.resolve(String.valueOf(uuid) + ".playerskin").toFile().exists();
    }

    public boolean isSkinFresh(UUID uuid, long maxAgeMillis) {
        if (uuid == null || maxAgeMillis <= 0L) {
            return false;
        }
        Path file = this.skinsDirectory.resolve(String.valueOf(uuid) + ".playerskin");
        if (!Files.exists(file, new LinkOption[0])) {
            return false;
        }
        try {
            long lastModified = Files.getLastModifiedTime(file, new LinkOption[0]).toMillis();
            long age = System.currentTimeMillis() - lastModified;
            return age >= 0L && age <= maxAgeMillis;
        }
        catch (Exception e) {
            return false;
        }
    }

    public void deleteSkin(UUID uuid) {
        try {
            File skinFile = this.skinsDirectory.resolve(String.valueOf(uuid) + ".playerskin").toFile();
            if (skinFile.exists() && skinFile.delete()) {
                this.logger.debug("[SKIN-STORAGE] Deleted skin file for {}", (Object)uuid);
            }
        }
        catch (Exception e) {
            this.logger.warn("[SKIN-STORAGE] Failed to delete skin for {}: {}", (Object)uuid, (Object)e.getMessage());
        }
    }
}
