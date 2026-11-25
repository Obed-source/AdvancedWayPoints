package io.github.obedsource.advancedwaypoints.config;

import io.github.obedsource.advancedwaypoints.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** The color list from config.yml (colors:), used for text, icon and background colors. */
public final class ColorPalette {

    /** One configured color. */
    public static final class Entry {
        private final String key;
        private final int rgb;
        private final Material material;

        Entry(String key, int rgb, Material material) {
            this.key = key;
            this.rgb = rgb;
            this.material = material;
        }

        public String key() {
            return key;
        }

        public int rgb() {
            return rgb;
        }

        public Material material() {
            return material;
        }

        public String hex() {
            return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
        }

        /** Legacy text color, for example "&amp;#FF0000". */
        public String legacy() {
            return "&" + hex();
        }

        public int argb(int alpha) {
            return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
        }
    }

    private final Map<String, Entry> entries;

    private ColorPalette(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    public static ColorPalette load(ConfigurationSection section, Logger logger) {
        Map<String, Entry> map = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection colorSection = section.getConfigurationSection(key);
                if (colorSection == null) {
                    continue;
                }
                Integer rgb = parseHex(colorSection.getString("hex"));
                if (rgb == null) {
                    logger.warning("Invalid hex for color '" + key + "'");
                    continue;
                }
                Material material = Items.material(colorSection.getString("material"), Material.WHITE_DYE, logger);
                String normalized = key.toLowerCase(Locale.ROOT);
                map.put(normalized, new Entry(normalized, rgb, material));
            }
        }
        if (map.isEmpty()) {
            map.put("white", new Entry("white", 0xFFFFFF, Material.WHITE_DYE));
            map.put("black", new Entry("black", 0x000000, Material.BLACK_DYE));
        }
        return new ColorPalette(map);
    }

    public Optional<Entry> get(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(entries.get(key.toLowerCase(Locale.ROOT)));
    }

    public Collection<Entry> all() {
        return entries.values();
    }

    public List<String> keys() {
        return new ArrayList<>(entries.keySet());
    }

    public Entry random() {
        List<Entry> list = new ArrayList<>(entries.values());
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** Finds the palette entry whose legacy code equals the given text color. */
    public Optional<Entry> byLegacy(String legacy) {
        if (legacy == null) {
            return Optional.empty();
        }
        for (Entry entry : entries.values()) {
            if (entry.legacy().equalsIgnoreCase(legacy)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Finds the palette entry for a background color, ignoring alpha. */
    public Optional<Entry> byRgb(int argb) {
        for (Entry entry : entries.values()) {
            if ((entry.rgb() & 0xFFFFFF) == (argb & 0xFFFFFF)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Parses "#RRGGBB" or "RRGGBB" into RGB. */
    public static Integer parseHex(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim();
        if (hex.startsWith("&")) {
            hex = hex.substring(1);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
