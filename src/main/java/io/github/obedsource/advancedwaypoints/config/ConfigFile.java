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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A YAML file in the plugin folder that updates itself.
 * <p>
 * When the bundled file has a newer {@code config-version}, or when keys are missing, the file is rebuilt
 * from the bundled template (so new keys come with their comments) and every value the owner set is kept,
 * including entries the template does not have. The previous file is saved as a backup first.
 */
public final class ConfigFile {

    public static final String VERSION_KEY = "config-version";

    private final Function<String, InputStream> resources;
    private final Logger logger;
    private final String resourcePath;
    private final File file;
    private YamlConfiguration config;

    public ConfigFile(JavaPlugin plugin, String resourcePath) {
        this(plugin::getResource, plugin.getDataFolder(), plugin.getLogger(), resourcePath);
    }

    /** Without a plugin instance (used by tests). */
    public ConfigFile(Function<String, InputStream> resources, File dataFolder, Logger logger, String resourcePath) {
        this.resources = resources;
        this.logger = logger;
        this.resourcePath = resourcePath;
        this.file = new File(dataFolder, resourcePath);
    }

    /** Writes the bundled file to disk. */
    private void copyBundled() {
        try (InputStream in = resources.apply(resourcePath)) {
            if (in != null) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not write " + resourcePath, e);
        }
    }

    /**
     * An option that no longer exists: dropped when updating from a file older than {@code version}.
     * Existing options are never replaced; only missing ones are added.
     */
    private record Removal(int version, String path) {
    }

    private final java.util.List<Removal> removals = new java.util.ArrayList<>();

    /** Drops an option that no longer exists when updating from an older file. */
    public ConfigFile removed(int version, String path) {
        removals.add(new Removal(version, path));
        return this;
    }

    public YamlConfiguration get() {
        return config;
    }

    public File file() {
        return file;
    }

    public boolean hasBundledDefaults() {
        return resources.apply(resourcePath) != null;
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
                logger.warning("Could not create folder " + parent);
            }
            copyBundled();
        }

        YamlConfiguration current = new YamlConfiguration();
        current.options().parseComments(true);
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Invalid YAML in " + file.getName() + ", a backup was made and the default file restored", e);
            backup("broken");
            if (defaults != null) {
                copyBundled();
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
                logger.info("Updated " + resourcePath + " (version " + oldVersion + " -> "
                        + defaults.getInt(VERSION_KEY, 0) + "), your values were kept.");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Could not save updated " + resourcePath, e);
            }
            current = merged;
            // merge() filled the template object itself: load a separate copy for the defaults,
            // otherwise the file would be its own defaults and every lookup would recurse forever.
            defaults = loadBundled();
        }
        if (defaults != null && defaults != current) {
            current.setDefaults(defaults);
        }
        config = current;
        return config;
    }

    private YamlConfiguration loadBundled() {
        InputStream stream = resources.apply(resourcePath);
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Bundled " + resourcePath + " is invalid", e);
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
    private YamlConfiguration merge(YamlConfiguration defaults, YamlConfiguration current) {
        int fileVersion = current.getInt(VERSION_KEY, 0);
        for (String key : current.getKeys(true)) {
            if (VERSION_KEY.equals(key) || current.isConfigurationSection(key) || removedOption(key, fileVersion)) {
                continue;
            }
            // Every value the owner has is kept as is; only missing options come from the template.
            defaults.set(key, current.get(key));
        }
        return defaults;
    }

    private boolean removedOption(String key, int fileVersion) {
        for (Removal removal : removals) {
            if (fileVersion < removal.version()
                    && (key.equals(removal.path()) || key.startsWith(removal.path() + "."))) {
                return true;
            }
        }
        return false;
    }

    private void backup(String suffix) {
        if (!file.exists()) {
            return;
        }
        File target = new File(file.getParentFile(), file.getName() + "." + suffix + ".bak");
        try {
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not back up " + file.getName(), e);
        }
    }
}
