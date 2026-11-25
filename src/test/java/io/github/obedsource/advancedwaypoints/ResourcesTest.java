package io.github.obedsource.advancedwaypoints;

import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.input.InputType;
import io.github.obedsource.advancedwaypoints.menu.MenuService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the bundled YAML files: they parse, they agree with each other and with the code. */
class ResourcesTest {

    private static YamlConfiguration load(String path) {
        try (InputStreamReader reader = new InputStreamReader(
                ResourcesTest.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (Exception e) {
            throw new AssertionError("Could not parse " + path, e);
        }
    }

    @Test
    void everyFileParsesAndHasAVersion() {
        for (String file : new String[]{"config.yml", "menus.yml", "sounds.yml", "lang/es.yml", "lang/en.yml"}) {
            assertTrue(load(file).getInt("config-version") >= 1, file + " has no config-version");
        }
    }

    @Test
    void settingsLoadWithDefaults() {
        Settings settings = new Settings(load("config.yml"), Logger.getLogger("test"));
        assertEquals("waypoints", settings.commandName);
        assertEquals(List.of("wp"), settings.commandAliases);
        assertEquals(32, settings.nameMaxLength);
        assertEquals(InputType.ANVIL, settings.inputType);
        assertEquals(16, settings.palette.all().size());
        assertTrue(settings.palette.get(settings.defaultBackgroundKey).isPresent());
        assertTrue(settings.palette.get(settings.defaultTextColorKey).isPresent());
        assertTrue(settings.nameCharacters.matcher("Casa de amigo ñandú 2").matches());
        assertFalse(settings.nameCharacters.matcher("casa'; DROP TABLE").matches());
        assertTrue(settings.updaterEnabled);
        assertEquals("en", settings.language);
        assertEquals("black", settings.defaultBackgroundKey);
        assertEquals(Settings.DeathMode.ACCUMULATE, settings.deathMode);
        assertEquals(3, settings.deathMax);
        assertTrue(settings.shareEnabled);
        assertFalse(settings.deathShareEnabled);
        assertTrue(settings.deathTeleportEnabled);
        assertFalse(settings.countdownEnabled);
        assertEquals(Settings.CountdownCancel.NONE, settings.countdownCancel);
        assertFalse(settings.defaultIconWithoutPermission);
        assertFalse(settings.deathTeleportCost.active());
        assertFalse(settings.createCost.active());
        assertFalse(settings.shareCost.active());
        assertFalse(settings.teleportCost.active());
        assertTrue(settings.deathEnabled);
        assertEquals("☠", settings.deathIcon);
        assertTrue(settings.palette.get(settings.deathIconColorKey).isPresent());
        assertTrue(settings.teleportEnabled && settings.teleportShortcut && settings.teleportInEditMenu);
    }

    @Test
    void soundsLoad() {
        assertNotNull(new SoundService(load("sounds.yml")));
        YamlConfiguration sounds = load("sounds.yml");
        for (String key : new String[]{SoundService.CREATE, SoundService.DELETE, SoundService.SHOW, SoundService.HIDE,
                SoundService.SHARE_SEND, SoundService.SHARE_RECEIVE, SoundService.LIST, SoundService.MENU_OPEN,
                SoundService.MODE, SoundService.ERROR, SoundService.NO_PERMISSION}) {
            assertTrue(sounds.contains("sounds." + key + ".sound"), "sounds.yml misses " + key);
        }
    }

    @Test
    void languagesHaveTheSameKeys() {
        Set<String> es = new TreeSet<>(load("lang/es.yml").getKeys(true));
        Set<String> en = new TreeSet<>(load("lang/en.yml").getKeys(true));
        Set<String> onlyEs = new TreeSet<>(es);
        onlyEs.removeAll(en);
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(es);
        assertTrue(onlyEs.isEmpty() && onlyEn.isEmpty(), "only es: " + onlyEs + " / only en: " + onlyEn);
    }

    @Test
    void everyKeyUsedInCodeExists() throws IOException {
        YamlConfiguration es = load("lang/es.yml");
        Pattern literal = Pattern.compile("\"((?:messages|input|help|pages|list|placeholders|menu|colors)\\.[a-z0-9.\\-]+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            // Settings.java reads config.yml, not the language file.
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java")
                    && !p.getFileName().toString().equals("Settings.java"))::iterator) {
                // Paths of removed options (migrations) are not expected to exist any more.
                String source = Files.readString(file).replaceAll("(?m)^.*\\.removed\\(.*$", "");
                Matcher matcher = literal.matcher(source);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.endsWith(".") || key.endsWith("-")) {
                        continue; // prefix completed at runtime, checked below
                    }
                    if (!es.contains(key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        // Keys built at runtime.
        for (String purpose : new String[]{"rename", "share", "create"}) {
            for (String prefix : new String[]{"input.anvil-title.", "input.sign-lines.", "input.chat-prompt.", "input.command-prompt."}) {
                if (!es.contains(prefix + purpose)) {
                    missing.add(prefix + purpose);
                }
            }
        }
        for (String target : new String[]{"text", "icon", "background"}) {
            for (String prefix : new String[]{"messages.color-changed.", "menu.color.title."}) {
                if (!es.contains(prefix + target)) {
                    missing.add(prefix + target);
                }
            }
        }
        for (String color : load("config.yml").getConfigurationSection("colors").getKeys(false)) {
            if (!es.contains("colors." + color)) {
                missing.add("colors." + color);
            }
        }
        assertTrue(missing.isEmpty(), "Missing language keys: " + missing);
    }

    @Test
    void everyMenuButtonHasTexts() {
        YamlConfiguration menus = load("menus.yml");
        YamlConfiguration es = load("lang/es.yml");
        List<String> missing = new ArrayList<>();
        for (String menu : new String[]{MenuService.MAIN, MenuService.EDIT, MenuService.COLOR, MenuService.ICON, MenuService.CONFIRM}) {
            assertNotNull(menus.getConfigurationSection(menu), "menus.yml misses " + menu);
            for (String item : menus.getConfigurationSection(menu + ".items").getKeys(false)) {
                if (!es.contains("menu." + menu + ".items." + item + ".name")) {
                    missing.add(menu + "." + item);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Buttons without a name in lang: " + missing);
    }
}
