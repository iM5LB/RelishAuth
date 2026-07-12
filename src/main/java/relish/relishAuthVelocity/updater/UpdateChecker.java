package relish.relishAuthVelocity.updater;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UpdateChecker {
    private final Logger logger;
    private final String currentVersion;
    private final String githubApiUrl;
    private static final String DEFAULT_GITHUB_API_URL = "https://api.github.com/repos/im5lb/RelishAuth/releases/latest";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/relishauth";
    private static final int TIMEOUT = 5000;

    public UpdateChecker(Logger logger, String currentVersion) {
        this(logger, currentVersion, DEFAULT_GITHUB_API_URL);
    }

    public UpdateChecker(Logger logger, String currentVersion, String githubApiUrl) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.githubApiUrl = githubApiUrl == null || githubApiUrl.isBlank() ? DEFAULT_GITHUB_API_URL : githubApiUrl.trim();
    }

    public CompletableFuture<UpdateInfo> checkForUpdates() {
        String downloadUrl = MODRINTH_PROJECT_URL;
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL requestUrl = new URL(this.githubApiUrl);
                HttpURLConnection connection = (HttpURLConnection)requestUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "RelishAuth-Updater");
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    this.logger.debug("Update check returned status code: {}", (Object)responseCode);
                    return new UpdateInfo(false, this.currentVersion, downloadUrl, null);
                }
                try (InputStream is = connection.getInputStream();
                     InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr);){
                    String latestVersion;
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    String string = latestVersion = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
                    if (latestVersion != null) {
                        latestVersion = latestVersion.replaceFirst("^v", "");
                    }
                    String changelog = json.has("body") ? json.get("body").getAsString() : null;
                    if (latestVersion == null) return new UpdateInfo(false, this.currentVersion, downloadUrl, null);
                    boolean updateAvailable = this.compareVersions(this.currentVersion, latestVersion) < 0;
                    UpdateInfo updateInfo = new UpdateInfo(updateAvailable, latestVersion, downloadUrl, changelog);
                    return updateInfo;
                }
            }
            catch (Exception e) {
                this.logger.debug("Failed to check for updates: {}", (Object)e.getMessage());
            }
            return new UpdateInfo(false, this.currentVersion, downloadUrl, null);
        });
    }

    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("[.-]");
            String[] parts2 = v2.split("[.-]");
            int length = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < length; ++i) {
                int num2;
                int num1 = i < parts1.length ? this.parseVersionPart(parts1[i]) : 0;
                int n = num2 = i < parts2.length ? this.parseVersionPart(parts2[i]) : 0;
                if (num1 < num2) {
                    return -1;
                }
                if (num1 <= num2) continue;
                return 1;
            }
            return 0;
        }
        catch (Exception e) {
            this.logger.debug("Failed to compare versions: {} vs {}", (Object)v1, (Object)v2);
            return 0;
        }
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public static class UpdateInfo {
        private final boolean updateAvailable;
        private final String latestVersion;
        private final String downloadUrl;
        private final String changelog;

        public UpdateInfo(boolean updateAvailable, String latestVersion, String downloadUrl, String changelog) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.changelog = changelog;
        }

        public boolean isUpdateAvailable() {
            return this.updateAvailable;
        }

        public String getLatestVersion() {
            return this.latestVersion;
        }

        public String getDownloadUrl() {
            return this.downloadUrl;
        }

        public String getChangelog() {
            return this.changelog;
        }
    }
}
