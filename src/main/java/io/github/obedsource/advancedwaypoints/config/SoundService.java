package io.github.obedsource.advancedwaypoints.config;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays the sounds configured in sounds.yml. Sounds are stored as namespaced keys
 * (e.g. "minecraft:ui.button.click"), which work on every version from 1.19.4 on.
 */
public final class SoundService {

    /** Sound keys; commands and menus share the same key for the same action. */
    public static final String CREATE = "create";
    public static final String DELETE = "delete";
    public static final String RENAME = "rename";
    public static final String COLOR = "color";
    public static final String ICON = "icon";
    public static final String FAVORITE_ADD = "favorite-add";
    public static final String FAVORITE_REMOVE = "favorite-remove";
    public static final String SHARE_SEND = "share-send";
    public static final String SHARE_RECEIVE = "share-receive";
    public static final String SHOW = "show";
    public static final String HIDE = "hide";
    public static final String HIDE_ALL = "hide-all";
    public static final String MODE = "mode";
    public static final String LIST = "list";
    public static final String HELP = "help";
    public static final String ERROR = "error";
    public static final String NO_PERMISSION = "no-permission";
    public static final String MENU_OPEN = "menu-open";
    public static final String MENU_CLICK = "menu-click";
    public static final String MENU_PAGE = "menu-page";
    public static final String MENU_BACK = "menu-back";
    public static final String INPUT_OPEN = "input-open";
    public static final String INPUT_CANCEL = "input-cancel";
    public static final String TELEPORT = "teleport";
    public static final String RESTART = "restart";

    private static final class Entry {
        final String sound;
        final float volume;
        final float pitch;

        Entry(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private final boolean enabled;
    private final SoundCategory category;
    private final Map<String, Entry> sounds;

    public SoundService(FileConfiguration config) {
        enabled = config.getBoolean("enabled", true);
        SoundCategory parsed;
        try {
            parsed = SoundCategory.valueOf(config.getString("category", "MASTER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = SoundCategory.MASTER;
        }
        category = parsed;
        Map<String, Entry> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("sounds");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null || !s.getBoolean("enabled", true)) {
                    continue;
                }
                String sound = s.getString("sound", "");
                if (sound.isEmpty()) {
                    continue;
                }
                map.put(key, new Entry(normalize(sound), (float) s.getDouble("volume", 1D), (float) s.getDouble("pitch", 1D)));
            }
        }
        sounds = Collections.unmodifiableMap(map);
    }

    /** Accepts "minecraft:ui.button.click", "ui.button.click" or the old enum style "UI_BUTTON_CLICK". */
    private static String normalize(String sound) {
        String value = sound.trim();
        if (value.contains(":") || value.contains(".")) {
            return value.toLowerCase(Locale.ROOT);
        }
        return "minecraft:" + value.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    public void play(Player player, String key) {
        if (!enabled || player == null) {
            return;
        }
        Entry entry = sounds.get(key);
        if (entry == null) {
            return;
        }
        player.playSound(player.getLocation(), entry.sound, category, entry.volume, entry.pitch);
    }
}
