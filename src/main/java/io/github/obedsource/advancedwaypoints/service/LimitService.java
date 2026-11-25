package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.util.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;
import java.util.function.IntSupplier;

/** Reads the waypoint limit of a player from wp.limit.&lt;n&gt; (highest wins). */
public final class LimitService {

    private final IntSupplier defaultLimit;

    public LimitService(IntSupplier defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int limitOf(Player player) {
        if (player.hasPermission(Permissions.LIMIT_UNLIMITED)) {
            return Integer.MAX_VALUE;
        }
        int highest = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(Permissions.LIMIT_PREFIX)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(permission.substring(Permissions.LIMIT_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // not a numeric node (e.g. wp.limit.unlimited handled above)
            }
        }
        return highest < 0 ? defaultLimit.getAsInt() : highest;
    }

    public static String display(int limit) {
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }
}
