package io.github.obedsource.advancedwaypoints.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Small item helpers that work from 1.19.4 onward. */
public final class Items {

    private static final Enchantment GLOW = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));

    private Items() {
    }

    public static Material material(String name, Material fallback, Logger logger) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null || !material.isItem()) {
            if (logger != null) {
                logger.warning("Unknown item material '" + name + "', using " + fallback);
            }
            return fallback;
        }
        return material;
    }

    /** Builds an item whose name and lore are already colored. */
    public static ItemStack build(Material material, String name, List<String> lore, Integer customModelData, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            meta.setDisplayName(name);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(new ArrayList<>(lore));
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        if (glow && GLOW != null) {
            meta.addEnchant(GLOW, 1, true);
        }
        // Hides vanilla tooltips (enchantments, map info, attributes...) on every version.
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }
}
