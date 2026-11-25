package io.github.obedsource.advancedwaypoints;

import io.github.obedsource.advancedwaypoints.config.ConfigFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The self-updating config: keeps every existing value, adds missing ones, never recurses. */
class ConfigFileTest {

    private static final String BUNDLED = String.join("\n",
            "# Do not edit",
            "config-version: 3",
            "# Language",
            "language: en",
            "waypoints:",
            "  default-background-color: black",
            "  new-option: 7",
            "colors:",
            "  red: {hex: \"#FF5555\"}",
            "");

    @TempDir
    File folder;

    private ConfigFile file(String bundled) {
        return new ConfigFile(path -> new ByteArrayInputStream(bundled.getBytes(StandardCharsets.UTF_8)),
                folder, Logger.getLogger("test"), "config.yml")
                .removed(3, "economy");
    }

    @Test
    void updateKeepsExistingValuesAddsMissingOnesAndDoesNotRecurse() throws Exception {
        Files.writeString(new File(folder, "config.yml").toPath(), String.join("\n",
                "config-version: 2",
                "language: es",
                "waypoints:",
                "  default-background-color: blue",
                "economy:",
                "  create: {enabled: true}",
                "colors:",
                "  red: {hex: \"#FF0000\"}",
                "  mine: {hex: \"#123456\"}",
                ""), StandardCharsets.UTF_8);

        YamlConfiguration config = file(BUNDLED).load();

        // Reading a missing key used to overflow the stack when the file was its own defaults.
        assertEquals("fallback", config.getString("does.not.exist", "fallback"));
        assertEquals("es", config.getString("language"));
        assertEquals("blue", config.getString("waypoints.default-background-color"));
        assertEquals(7, config.getInt("waypoints.new-option"));
        assertEquals("#FF0000", config.getString("colors.red.hex"));
        assertEquals("#123456", config.getString("colors.mine.hex"));
        assertFalse(config.contains("economy", true));
        assertEquals(3, config.getInt("config-version"));
        assertTrue(new File(folder, "config.yml.v2.bak").exists());

        // Saved to disk, with the template's comments, and loads again cleanly.
        String saved = Files.readString(new File(folder, "config.yml").toPath(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("# Language"));
        YamlConfiguration again = file(BUNDLED).load();
        assertEquals("es", again.getString("language"));
        assertEquals("fallback", again.getString("missing", "fallback"));
    }

    @Test
    void firstLoadWritesTheBundledFile() {
        YamlConfiguration config = file(BUNDLED).load();
        assertEquals("en", config.getString("language"));
        assertTrue(new File(folder, "config.yml").exists());
        assertEquals("x", config.getString("missing", "x"));
    }
}
