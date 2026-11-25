package io.github.obedsource.advancedwaypoints.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * A YAML file in the plugin folder that updates itself.
 * <p>
 * When the bundled file has a newer {@code config-version}, or when keys are missing, the file is rebuilt
 * from the bundled template (so new keys come with their comments) and every value the owner set is kept,
 * including entries the template does not have. The previous file is saved as a backup first.
 */
public final class ConfigFile {

    public static final String VERSION_KEY = "config-version";

    private final JavaPlugin plugin;
    private final String resourcePath;
    private final File file;
    private YamlConfiguration config;

    public ConfigFile(JavaPlugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
    }

    public YamlConfiguration get() {
        return config;
    }

    public File file() {
        return file;
    }

    public boolean hasBundledDefaults() {
        return plugin.getResource(resourcePath) != null;
    }

    /** Loads (and updates if needed) the file. */
    public YamlConfiguration load() {
        YamlConfiguration defaults = loadBundled();
        if (!file.exists()) {
            if (defaults == null) {
                config = new YamlConfiguration();
                return config;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create folder " + parent);
            }
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration current = new YamlConfiguration();
        current.options().parseComments(true);
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid YAML in " + file.getName() + ", a backup was made and the default file restored", e);
            backup("broken");
            if (defaults != null) {
                plugin.saveResource(resourcePath, true);
                config = defaults;
                return config;
            }
            config = new YamlConfiguration();
            return config;
        }

        if (defaults != null && needsUpdate(current, defaults)) {
            int oldVersion = current.getInt(VERSION_KEY, 0);
            backup("v" + oldVersion);
            YamlConfiguration merged = merge(defaults, current);
            try {
                merged.save(file);
                plugin.getLogger().info("Updated " + resourcePath + " (version " + oldVersion + " -> "
                        + defaults.getInt(VERSION_KEY, 0) + "), your values were kept.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save updated " + resourcePath, e);
            }
            current = merged;
        }
        if (defaults != null) {
            current.setDefaults(defaults);
        }
        config = current;
        return config;
    }

    private YamlConfiguration loadBundled() {
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Bundled " + resourcePath + " is invalid", e);
            return null;
        }
    }

    private static boolean needsUpdate(YamlConfiguration current, YamlConfiguration defaults) {
        if (current.getInt(VERSION_KEY, 0) < defaults.getInt(VERSION_KEY, 0)) {
            return true;
        }
        for (String key : defaults.getKeys(true)) {
            if (!current.contains(key, true)) {
                return true;
            }
        }
        return false;
    }

    /** The template (with comments) filled with the owner's values. */
    private static YamlConfiguration merge(YamlConfiguration defaults, YamlConfiguration current) {
        for (String key : current.getKeys(true)) {
            if (VERSION_KEY.equals(key) || current.isConfigurationSection(key)) {
                continue;
            }
            defaults.set(key, current.get(key));
        }
        return defaults;
    }

    private void backup(String suffix) {
        if (!file.exists()) {
            return;
        }
        File target = new File(file.getParentFile(), file.getName() + "." + suffix + ".bak");
        try {
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not back up " + file.getName(), e);
        }
    }
}
