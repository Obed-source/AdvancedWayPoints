package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.config.CostSpec;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Charges the price of an action: money (Vault) or items from the inventory. Players with
 * wp.bypass.cost never pay. Without Vault, money prices are free.
 */
public final class CostService {

    private final Supplier<EconomyProvider> economy;

    public CostService(Supplier<EconomyProvider> economy) {
        this.economy = economy;
    }

    private boolean applies(Player player, CostSpec cost) {
        if (player == null || cost == null || !cost.active() || player.hasPermission(Permissions.BYPASS_COST)) {
            return false;
        }
        return cost.type == CostSpec.Type.ITEM || economy.get() != EconomyProvider.NONE;
    }

    /** True when the action costs this player something. */
    public boolean isCharged(Player player, CostSpec cost) {
        return applies(player, cost);
    }

    /** Whether the player can pay (does not take anything). */
    public boolean canAfford(Player player, CostSpec cost) {
        if (!applies(player, cost)) {
            return true;
        }
        if (cost.type == CostSpec.Type.MONEY) {
            return economy.get().has(player, cost.money);
        }
        return count(player.getInventory(), cost) >= cost.amount;
    }

    /** Takes the price. Returns false (taking nothing) when the player cannot pay. */
    public boolean charge(Player player, CostSpec cost) {
        if (!applies(player, cost)) {
            return true;
        }
        if (cost.type == CostSpec.Type.MONEY) {
            return economy.get().withdraw(player, cost.money);
        }
        PlayerInventory inventory = player.getInventory();
        if (count(inventory, cost) < cost.amount) {
            return false;
        }
        int left = cost.amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!matches(item, cost)) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            contents[i] = item.getAmount() <= 0 ? null : item;
            left -= take;
        }
        inventory.setStorageContents(contents);
        return true;
    }

    /** Human readable price, e.g. "$100.00" or "2x Diamond". */
    public String describe(CostSpec cost) {
        if (cost == null || !cost.active()) {
            return "";
        }
        if (cost.type == CostSpec.Type.MONEY) {
            return economy.get().format(cost.money);
        }
        String name = cost.itemName != null && !cost.itemName.isEmpty()
                ? Text.color(cost.itemName)
                : pretty(cost.material.name());
        return cost.amount + "x " + name;
    }

    public Map<String, String> placeholders(CostSpec cost) {
        return Text.ph("price", describe(cost));
    }

    private static String pretty(String materialName) {
        StringBuilder out = new StringBuilder();
        for (String part : materialName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static int count(PlayerInventory inventory, CostSpec cost) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (matches(item, cost)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Same material, and the same NBT key/value, custom model data and name when configured. */
    static boolean matches(ItemStack item, CostSpec cost) {
        if (item == null || item.getType() != cost.material) {
            return false;
        }
        boolean special = !cost.nbtKey.isEmpty() || cost.customModelData > 0 || !cost.itemName.isEmpty();
        if (!special) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (!cost.nbtKey.isEmpty()) {
            NamespacedKey key = NamespacedKey.fromString(cost.nbtKey.toLowerCase(Locale.ROOT));
            if (key == null || !meta.getPersistentDataContainer().getKeys().contains(key)) {
                return false;
            }
            if (!cost.nbtValue.isEmpty()) {
                String value;
                try {
                    value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                } catch (IllegalArgumentException notAString) {
                    return false;
                }
                if (value == null || !value.equals(cost.nbtValue)) {
                    return false;
                }
            }
        }
        if (cost.customModelData > 0 && (!meta.hasCustomModelData() || meta.getCustomModelData() != cost.customModelData)) {
            return false;
        }
        return cost.itemName.isEmpty()
                || (meta.hasDisplayName() && Text.strip(meta.getDisplayName()).equals(Text.strip(cost.itemName)));
    }
}
