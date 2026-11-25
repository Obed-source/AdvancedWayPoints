package io.github.obedsource.advancedwaypoints.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.obedsource.advancedwaypoints.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Level;

/**
 * Reads update.json from the GitHub repository (in a background thread) and, when a newer version is
 * published, downloads it into the server's update folder: Bukkit installs it on the next restart.
 */
public final class UpdateChecker {

    private final JavaPlugin plugin;
    private final File pluginFile;
    private volatile String latestVersion;
    private volatile String changelog;
    private volatile boolean downloaded;

    public UpdateChecker(JavaPlugin plugin, File pluginFile) {
        this.plugin = plugin;
        this.pluginFile = pluginFile;
    }

    /** Runs the check; call it from an async thread. */
    public void check(Settings settings) {
        if (!settings.updaterEnabled) {
            return;
        }
        try {
            JsonObject json = fetch(settings.updaterUrl);
            String version = json.get("version").getAsString();
            @SuppressWarnings("deprecation")
            String current = plugin.getDescription().getVersion();
            if (compare(version, current) <= 0) {
                plugin.getLogger().info("You are running the latest version (" + current + ").");
                return;
            }
            latestVersion = version;
            changelog = json.has("changelog") ? json.get("changelog").getAsString() : "";
            plugin.getLogger().warning("A new version is available: " + version + " (current " + current + ")");
            if (settings.updaterAutoDownload && json.has("download")) {
                download(json.get("download").getAsString(), json.has("sha256") ? json.get("sha256").getAsString() : null);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not check for updates: " + e.getMessage());
        }
    }

    private static JsonObject fetch(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "AdvancedWayPoints-Updater");
        return connection;
    }

    private void download(String url, String expectedSha256) throws Exception {
        File updateFolder = Bukkit.getUpdateFolderFile();
        if (!updateFolder.exists() && !updateFolder.mkdirs()) {
            throw new IOException("Could not create " + updateFolder);
        }
        File temp = new File(updateFolder, pluginFile.getName() + ".part");
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
        if (expectedSha256 != null && !expectedSha256.isEmpty()) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(temp.toPath()));
            String actual = HexFormat.of().formatHex(digest);
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(temp.toPath());
                throw new IOException("Downloaded file checksum does not match, update skipped");
            }
        }
        Files.move(temp.toPath(), new File(updateFolder, pluginFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        downloaded = true;
        plugin.getLogger().info("Version " + latestVersion + " downloaded, it will be installed on the next restart.");
    }

    /** Compares dotted versions numerically ("1.10.0" &gt; "1.9.2"). */
    static int compare(String a, String b) {
        String[] left = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] right = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length && !left[i].isEmpty() ? Integer.parseInt(left[i]) : 0;
            int r = i < right.length && !right[i].isEmpty() ? Integer.parseInt(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    /** Newer version found, or null. */
    public String latestVersion() {
        return latestVersion;
    }

    public String changelog() {
        return changelog;
    }

    public boolean downloaded() {
        return downloaded;
    }
}
