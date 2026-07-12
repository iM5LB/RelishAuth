package relish.relishAuthVelocity.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import relish.relishAuthVelocity.RelishAuthVelocity;
import relish.relishAuthVelocity.config.Config;

public class SkinFetchService {
    private final RelishAuthVelocity plugin;
    private final Config config;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String DEFAULT_USERNAME_TEXTURES_API = "https://skinsystem.ely.by/textures/{nickname}";
    private static final String DEFAULT_MOJANG_SESSION_API = "https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false";
    private static final String DEFAULT_MOJANG_PROFILE_LOOKUP_API = "https://api.minecraftservices.com/minecraft/profile/lookup/name/{nickname}";
    private String usernameTexturesEndpoint;
    private String uuidSessionEndpoint;
    private String usernameUuidLookupEndpoint;
    private int timeoutSeconds;
    private int retryAttempts;
    private int retryDelayMillis;

    public SkinFetchService(RelishAuthVelocity plugin, Config config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10L)).build();
        this.loadEndpointsFromConfig();
    }

    private void loadEndpointsFromConfig() {
        String customUsernameTextures = this.config.getString("skins.api.username-textures-endpoint", "");
        if (customUsernameTextures == null || customUsernameTextures.trim().isEmpty()) {
            customUsernameTextures = this.config.getString("skins.api.elyby-textures-endpoint", "");
        }
        this.usernameTexturesEndpoint = customUsernameTextures == null || customUsernameTextures.trim().isEmpty() ? DEFAULT_USERNAME_TEXTURES_API : customUsernameTextures;
        String customSession = this.config.getString("skins.api.uuid-session-endpoint", "");
        if (customSession == null || customSession.trim().isEmpty()) {
            customSession = this.config.getString("skins.api.mojang-session-endpoint", "");
        }
        this.uuidSessionEndpoint = customSession == null || customSession.trim().isEmpty() ? DEFAULT_MOJANG_SESSION_API : customSession;
        String customLookup = this.config.getString("skins.api.username-uuid-lookup-endpoint", "");
        if (customLookup == null || customLookup.trim().isEmpty()) {
            customLookup = this.config.getString("skins.api.mojang-profile-lookup-endpoint", "");
        }
        this.usernameUuidLookupEndpoint = customLookup == null || customLookup.trim().isEmpty() ? DEFAULT_MOJANG_PROFILE_LOOKUP_API : customLookup;
        this.timeoutSeconds = this.config.getInt("skins.api.timeout", 10);
        this.retryAttempts = Math.max(0, this.config.getInt("skins.api.retry-attempts", 2));
        this.retryDelayMillis = Math.max(0, this.config.getInt("skins.api.retry-delay", 1000));
        this.logger.debug("[SKIN] Loaded endpoints:");
        this.logger.debug("[SKIN] - Textures by Username: {}", (Object)this.usernameTexturesEndpoint);
        this.logger.debug("[SKIN] - UUID Lookup by Username: {}", (Object)this.usernameUuidLookupEndpoint);
        this.logger.debug("[SKIN] - Session by UUID: {}", (Object)this.uuidSessionEndpoint);
    }

    public CompletableFuture<SkinData> fetchSkinByUsername(String username) {
        if (!this.isValidMinecraftUsername(username)) {
            this.logger.debug("[SKIN] Invalid username for skin fetch: '{}'", (Object)username);
            return CompletableFuture.completedFuture(null);
        }
        String url = this.usernameTexturesEndpoint.replace("{nickname}", username).replace("{username}", username);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(this.timeoutSeconds)).header("Accept", "application/json").GET().build();
        return this.sendWithRetries(request, this.retryAttempts).thenApply(response -> {
            if (response == null) {
                return null;
            }
            int status = response.statusCode();
            if (status != 200) {
                this.logger.debug("[SKIN-API] Non-200 response for {}: {}", (Object)username, (Object)status);
                return null;
            }
            return this.parseTexturesResponse((String)response.body(), username);
        }).exceptionally(e -> {
            this.logger.debug("[SKIN] Failed to fetch skin for {}: {}", (Object)username, (Object)e.getMessage());
            return null;
        });
    }

    public CompletableFuture<SkinData> fetchSkinByMojangUuid(UUID mojangUuid, String username) {
        if (mojangUuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        String url = this.uuidSessionEndpoint.replace("{uuid}", mojangUuid.toString().replace("-", ""));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(this.timeoutSeconds)).header("Accept", "application/json").GET().build();
        return this.sendWithRetries(request, this.retryAttempts).thenApply(response -> {
            if (response == null) {
                return null;
            }
            int status = response.statusCode();
            if (status != 200) {
                this.logger.debug("[SKIN-API] Non-200 response for {} ({}): {}", username, mojangUuid, status);
                return null;
            }
            return this.parseTexturesResponse((String)response.body(), username);
        }).exceptionally(e -> {
            this.logger.debug("[SKIN] Failed to fetch skin for {} ({}): {}", username, mojangUuid, e.getMessage());
            return null;
        });
    }

    public CompletableFuture<UUID> fetchMojangUuidByUsername(String username) {
        if (!this.isValidMinecraftUsername(username)) {
            return CompletableFuture.completedFuture(null);
        }
        String url = this.usernameUuidLookupEndpoint.replace("{nickname}", username).replace("{username}", username);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(this.timeoutSeconds)).header("Accept", "application/json").GET().build();
        return this.sendWithRetries(request, this.retryAttempts).thenApply(response -> {
            if (response == null || response.statusCode() != 200) {
                return null;
            }
            try {
                JsonObject json = this.gson.fromJson((String)response.body(), JsonObject.class);
                if (json == null || !json.has("id")) {
                    return null;
                }
                String rawId = json.get("id").getAsString();
                if (rawId == null || rawId.isBlank()) {
                    return null;
                }
                String formattedUuid = rawId.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                return UUID.fromString(formattedUuid);
            }
            catch (Exception ignored) {
                return null;
            }
        }).exceptionally(e -> null);
    }

    public CompletableFuture<SkinData> fetchPremiumSkinByUsername(String username) {
        return this.fetchMojangUuidByUsername(username).thenCompose(mojangUuid -> mojangUuid == null ? CompletableFuture.completedFuture(null) : this.fetchSkinByMojangUuid((UUID)mojangUuid, username));
    }

    private CompletableFuture<HttpResponse<String>> sendWithRetries(HttpRequest request, int maxRetries) {
        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, exception) -> {
            if (exception == null && response != null && response.statusCode() == 200) {
                return CompletableFuture.completedFuture(response);
            }
            if (maxRetries <= 0) {
                if (exception != null) {
                    this.logger.debug("[SKIN-API] Request failed: {}", (Object)exception.getMessage());
                }
                return CompletableFuture.completedFuture(response);
            }
            return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(this.retryDelayMillis, TimeUnit.MILLISECONDS)).thenCompose(ignored -> this.sendWithRetries(request, maxRetries - 1));
        }).thenCompose(f -> f);
    }

    private SkinData parseTexturesResponse(String json, String username) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            JsonElement element = this.gson.fromJson(json, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject root = element.getAsJsonObject();
            if (root.has("properties") && root.get("properties").isJsonArray()) {
                JsonArray properties = root.getAsJsonArray("properties");
                for (JsonElement propEl : properties) {
                    String signature;
                    JsonObject propObj;
                    String name;
                    if (!propEl.isJsonObject() || !"textures".equals(name = (propObj = propEl.getAsJsonObject()).has("name") ? propObj.get("name").getAsString() : null)) continue;
                    String value = propObj.has("value") ? propObj.get("value").getAsString() : null;
                    String string = signature = propObj.has("signature") ? propObj.get("signature").getAsString() : null;
                    if (value == null || value.isBlank()) {
                        return null;
                    }
                    return new SkinData(value, signature == null ? "" : signature, "properties");
                }
            }
            if (root.has("value") && root.get("value").isJsonPrimitive()) {
                String signature;
                String value = root.get("value").getAsString();
                String string = signature = root.has("signature") ? root.get("signature").getAsString() : "";
                if (value != null && !value.isBlank()) {
                    return new SkinData(value, signature, "value");
                }
            }
            if (root.has("SKIN") && root.get("SKIN").isJsonObject()) {
                JsonObject capeObj;
                JsonObject textures = new JsonObject();
                JsonObject skinObj = root.getAsJsonObject("SKIN");
                if (skinObj.has("url")) {
                    JsonObject skin = new JsonObject();
                    skin.addProperty("url", skinObj.get("url").getAsString());
                    if (skinObj.has("metadata") && skinObj.get("metadata").isJsonObject()) {
                        skin.add("metadata", skinObj.get("metadata"));
                    }
                    textures.add("SKIN", skin);
                }
                if (root.has("CAPE") && root.get("CAPE").isJsonObject() && (capeObj = root.getAsJsonObject("CAPE")).has("url")) {
                    JsonObject cape = new JsonObject();
                    cape.addProperty("url", capeObj.get("url").getAsString());
                    textures.add("CAPE", cape);
                }
                if (textures.size() == 0) {
                    return null;
                }
                JsonObject payload = new JsonObject();
                payload.addProperty("timestamp", System.currentTimeMillis());
                payload.addProperty("profileId", UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
                payload.addProperty("profileName", username);
                payload.add("textures", textures);
                String encodedTextureData = Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
                return new SkinData(encodedTextureData, "", "ely.by");
            }
        }
        catch (Exception e) {
            this.logger.debug("[SKIN-API] Failed to parse textures response for {}: {}", username, e.getMessage(), e);
        }
        return null;
    }

    private boolean isValidMinecraftUsername(String username) {
        if (username == null) {
            return false;
        }
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 16) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); ++i) {
            boolean ok;
            char c = trimmed.charAt(i);
            boolean bl = ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
            if (ok) continue;
            return false;
        }
        return true;
    }

    public static class SkinData {
        private static final Gson GSON = new Gson();
        public final String textureData;
        public final String signature;
        public final String source;
        public final long fetchedAt;

        public SkinData(String textureData, String signature, String source) {
            this(textureData, signature, source, System.currentTimeMillis());
        }

        public SkinData(String textureData, String signature, String source, long fetchedAt) {
            this.textureData = textureData;
            this.signature = signature;
            this.source = source;
            this.fetchedAt = fetchedAt;
        }

        public String toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("textureData", this.textureData);
            obj.addProperty("signature", this.signature);
            obj.addProperty("source", this.source);
            obj.addProperty("fetchedAt", this.fetchedAt);
            return GSON.toJson(obj);
        }

        public static SkinData fromJson(String json) {
            try {
                String textureData;
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                if (obj == null) {
                    return null;
                }
                String string = textureData = obj.has("textureData") ? obj.get("textureData").getAsString() : null;
                if (textureData == null || textureData.isBlank()) {
                    return null;
                }
                String signature = obj.has("signature") ? obj.get("signature").getAsString() : "";
                String source = obj.has("source") ? obj.get("source").getAsString() : "unknown";
                long fetchedAt = obj.has("fetchedAt") ? obj.get("fetchedAt").getAsLong() : System.currentTimeMillis();
                return new SkinData(textureData, signature, source, fetchedAt);
            }
            catch (Exception e) {
                return null;
            }
        }
    }
}
