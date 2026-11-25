package io.github.obedsource.advancedwaypoints;

import io.github.obedsource.advancedwaypoints.config.ConfigFile;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.service.NameValidator;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpersTest {

    private static Settings settings() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                HelpersTest.class.getClassLoader().getResourceAsStream("config.yml"), StandardCharsets.UTF_8)) {
            return new Settings(YamlConfiguration.loadConfiguration(reader), Logger.getLogger("test"));
        }
    }

    @Test
    void names() throws Exception {
        Settings s = settings();
        NameValidator validator = new NameValidator(() -> s);
        assertEquals("casa de amigo", validator.validate("  casa   de amigo ", false).name());
        assertTrue(validator.validate("Árbol Ñ 12", false).ok());
        assertEquals("messages.name-invalid", validator.validate("casa&c", false).errorKey());
        assertTrue(validator.validate("&ccasa", true).ok());
        assertEquals("messages.name-empty", validator.validate("&c", true).errorKey());
        assertEquals("messages.name-too-long", validator.validate("a".repeat(33), false).errorKey());
        assertTrue(validator.validate("a".repeat(32), false).ok());
        assertEquals("messages.name-invalid", validator.validate("hola!", false).errorKey());
    }

    @Test
    void distanceAndLabel() throws Exception {
        Settings s = settings();
        assertEquals("30m", WaypointFormat.distance(30.4, s));
        assertEquals("1.5km", WaypointFormat.distance(1500, s));
        long now = System.currentTimeMillis();
        io.github.obedsource.advancedwaypoints.api.Waypoint death = new io.github.obedsource.advancedwaypoints.api.Waypoint(
                0L, java.util.UUID.randomUUID(), "abcd", "Death", "world", 0, 64, 0, "&c", "x", "&c", 0,
                false, 0L, now, io.github.obedsource.advancedwaypoints.api.WaypointType.DEATH, now + 125_000L);
        assertEquals("2:05", WaypointFormat.timeLeft(death, s));
        assertTrue(WaypointFormat.label(death, s, "30m").contains("2:05"));
        assertFalse(death.isExpired(now));
        assertTrue(death.isExpired(now + 125_000L));
    }

    @Test
    void textHelpers() {
        assertTrue(Text.isColorCode("&c"));
        assertTrue(Text.isColorCode("&#FFAA00&l"));
        assertFalse(Text.isColorCode("red"));
        assertEquals("casa", Text.strip("&#FF0000&lcasa"));
        assertEquals("a b", Text.replace("{x} {y}", Text.ph("x", "a", "y", "b")));
    }

    @Test
    void slotParsing() throws Exception {
        Method parse = Class.forName("io.github.obedsource.advancedwaypoints.menu.MenuLayout")
                .getDeclaredMethod("parseSlots", String.class, int.class);
        parse.setAccessible(true);
        assertEquals(List.of(0, 1, 2, 17, 53), parse.invoke(null, "0-2, 17, 53, 54, x", 54));
    }

    @Test
    void versionCompare() throws Exception {
        Method compare = Class.forName("io.github.obedsource.advancedwaypoints.integration.UpdateChecker")
                .getDeclaredMethod("compare", String.class, String.class);
        compare.setAccessible(true);
        assertTrue((int) compare.invoke(null, "1.10.0", "1.9.2") > 0);
        assertEquals(0, (int) compare.invoke(null, "1.0", "1.0.0"));
        assertTrue((int) compare.invoke(null, "1.0.0", "1.0.1") < 0);
    }

    @Test
    void configFileConstantExists() {
        assertEquals("config-version", ConfigFile.VERSION_KEY);
    }
}
