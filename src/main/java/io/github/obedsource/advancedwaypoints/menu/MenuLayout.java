package io.github.obedsource.advancedwaypoints.menu;

import io.github.obedsource.advancedwaypoints.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Layout of one menu from menus.yml: size, content slots and buttons. */
public final class MenuLayout {

    /** One configurable button. Texts come from the language file. */
    public static final class Item {
        private final String key;
        private final Material material;
        private final List<Integer> slots;
        private final Integer customModelData;
        private final boolean enabled;

        Item(String key, Material material, List<Integer> slots, Integer customModelData, boolean enabled) {
            this.key = key;
            this.material = material;
            this.slots = slots;
            this.customModelData = customModelData;
            this.enabled = enabled;
        }

        public String key() {
            return key;
        }

        public Material material() {
            return material;
        }

        public List<Integer> slots() {
            return slots;
        }

        public Integer customModelData() {
            return customModelData;
        }

        public boolean enabled() {
            return enabled && !slots.isEmpty();
        }
    }

    private final String id;
    private final int rows;
    private final List<Integer> contentSlots;
    private final Map<String, Item> items;
    private final ConfigurationSection section;

    private MenuLayout(String id, int rows, List<Integer> contentSlots, Map<String, Item> items, ConfigurationSection section) {
        this.id = id;
        this.rows = rows;
        this.contentSlots = contentSlots;
        this.items = items;
        this.section = section;
    }

    public static MenuLayout load(String id, ConfigurationSection section, Logger logger) {
        if (section == null) {
            logger.warning("menus.yml has no '" + id + "' section");
            return new MenuLayout(id, 3, Collections.emptyList(), Collections.emptyMap(), null);
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 6)));
        int size = rows * 9;
        List<Integer> content = parseSlots(section.getString("content-slots", ""), size);
        Map<String, Item> items = new LinkedHashMap<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection s = itemsSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                List<Integer> slots = s.contains("slots")
                        ? parseSlots(s.getString("slots", ""), size)
                        : parseSlots(String.valueOf(s.getInt("slot", -1)), size);
                Integer model = s.contains("custom-model-data") ? s.getInt("custom-model-data") : null;
                Material material = Items.material(s.getString("material"), Material.STONE, logger);
                items.put(key, new Item(key, material, slots, model, s.getBoolean("enabled", true)));
            }
        }
        return new MenuLayout(id, rows, content, items, section);
    }

    /** Parses "0-8, 17, 18" into slot numbers inside the menu. */
    static List<Integer> parseSlots(String value, int size) {
        List<Integer> slots = new ArrayList<>();
        if (value == null) {
            return slots;
        }
        for (String part : value.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                if (token.contains("-")) {
                    String[] range = token.split("-", 2);
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        if (i >= 0 && i < size && !slots.contains(i)) {
                            slots.add(i);
                        }
                    }
                } else {
                    int slot = Integer.parseInt(token);
                    if (slot >= 0 && slot < size && !slots.contains(slot)) {
                        slots.add(slot);
                    }
                }
            } catch (NumberFormatException ignored) {
                // invalid entry, skipped
            }
        }
        return slots;
    }

    public String id() {
        return id;
    }

    public int rows() {
        return rows;
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public Item item(String key) {
        Item item = items.get(key);
        return item != null && item.enabled() ? item : null;
    }

    public Material material(String path, Material fallback, Logger logger) {
        return section == null ? fallback : Items.material(section.getString(path), fallback, logger);
    }

    public boolean bool(String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }
}
