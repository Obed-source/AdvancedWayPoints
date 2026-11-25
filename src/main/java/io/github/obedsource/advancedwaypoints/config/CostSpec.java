package io.github.obedsource.advancedwaypoints.config;

import io.github.obedsource.advancedwaypoints.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Price of an action (config.yml costs.&lt;action&gt;): money through Vault, or items taken from the
 * inventory. Items can be plain (material) or special ones, matched by a persistent data (NBT) key,
 * custom model data and/or display name.
 */
public final class CostSpec {

    public enum Type { MONEY, ITEM }

    public final boolean enabled;
    public final Type type;
    public final double money;
    public final Material material;
    public final int amount;
    public final String nbtKey;
    public final String nbtValue;
    public final int customModelData;
    public final String itemName;

    private CostSpec(boolean enabled, Type type, double money, Material material, int amount, String nbtKey,
                     String nbtValue, int customModelData, String itemName) {
        this.enabled = enabled;
        this.type = type;
        this.money = money;
        this.material = material;
        this.amount = amount;
        this.nbtKey = nbtKey;
        this.nbtValue = nbtValue;
        this.customModelData = customModelData;
        this.itemName = itemName;
    }

    public static final CostSpec FREE = new CostSpec(false, Type.MONEY, 0, Material.AIR, 0, "", "", 0, "");

    public static CostSpec load(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return FREE;
        }
        Type type;
        try {
            type = Type.valueOf(section.getString("type", "MONEY").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown cost type in " + section.getCurrentPath() + ", using MONEY");
            type = Type.MONEY;
        }
        return new CostSpec(
                section.getBoolean("enabled", false),
                type,
                Math.max(0D, section.getDouble("money", 0D)),
                Items.material(section.getString("item.material", "DIAMOND"), Material.DIAMOND, logger),
                Math.max(1, section.getInt("item.amount", 1)),
                section.getString("item.nbt-key", "").trim(),
                section.getString("item.nbt-value", ""),
                section.getInt("item.custom-model-data", 0),
                section.getString("item.name", ""));
    }

    /** Whether the action has a price at all. */
    public boolean active() {
        return enabled && (type == Type.ITEM ? amount > 0 : money > 0);
    }
}
