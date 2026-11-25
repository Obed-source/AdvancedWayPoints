package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.ColorPalette;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.util.Text;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared text formatting of waypoints (chat, menus, labels and placeholders). */
public final class WaypointFormat {

    private WaypointFormat() {
    }

    /** Placeholders describing a waypoint: {name} {name_plain} {code} {icon} {icon_color} {text_color} {world} {x} {y} {z} {created}. */
    public static Map<String, String> placeholders(Waypoint waypoint, Settings settings) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("code", waypoint.getCode());
        map.put("id", waypoint.getCode());
        map.put("name", nullToEmpty(waypoint.getTextColor()) + waypoint.getName());
        map.put("name_plain", Text.strip(waypoint.getName()));
        map.put("icon", waypoint.getIcon());
        map.put("icon_color", nullToEmpty(waypoint.getIconColor()));
        map.put("text_color", nullToEmpty(waypoint.getTextColor()));
        map.put("world", waypoint.getWorld());
        map.put("x", String.valueOf(waypoint.getBlockX()));
        map.put("y", String.valueOf(waypoint.getBlockY()));
        map.put("z", String.valueOf(waypoint.getBlockZ()));
        map.put("created", settings.dateFormat.format(Instant.ofEpochMilli(waypoint.getCreatedAt())));
        map.put("background", hex(waypoint.getBackgroundArgb()));
        map.put("time_left", timeLeft(waypoint, settings));
        return map;
    }

    /** "{icon_color}{icon} {text_color}{name}" style label, before distance. */
    public static String label(Waypoint waypoint, Settings settings, String distanceRaw) {
        String distance = "";
        if (settings.showDistance && distanceRaw != null) {
            distance = Text.replace(settings.distanceFormat, Map.of("distance", distanceRaw));
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("icon_color", nullToEmpty(waypoint.getIconColor()));
        map.put("icon", waypoint.getIcon());
        map.put("text_color", nullToEmpty(waypoint.getTextColor()));
        map.put("name", waypoint.getName());
        map.put("distance", distance);
        map.put("distance_raw", distanceRaw == null ? "" : distanceRaw);
        map.put("time_left", timeLeft(waypoint, settings));
        return Text.color(Text.replace(waypoint.isDeath() ? settings.deathFormat : settings.displayFormat, map));
    }

    /** Time until the waypoint disappears, e.g. "9:41", or "" when it never expires. */
    public static String timeLeft(Waypoint waypoint, Settings settings) {
        if (waypoint.getExpiresAt() <= 0) {
            return "";
        }
        long seconds = Math.max(0L, (waypoint.getExpiresAt() - System.currentTimeMillis() + 999L) / 1000L);
        long hours = seconds / 3600L;
        String template = hours > 0 ? settings.timeLeftHoursFormat : settings.timeLeftFormat;
        long minutes = hours > 0 ? (seconds % 3600L) / 60L : seconds / 60L;
        return template.replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", hours > 0 ? String.format(Locale.ROOT, "%02d", minutes) : String.valueOf(minutes))
                .replace("{seconds}", String.format(Locale.ROOT, "%02d", seconds % 60L));
    }

    /** Raw distance text, e.g. "30m" or "1.2km". */
    public static String distance(double blocks, Settings settings) {
        if (blocks >= settings.kilometersThreshold) {
            return settings.kilometersFormat.replace("{value}", String.format(Locale.ROOT, "%.1f", blocks / 1000D));
        }
        return settings.metersFormat.replace("{value}", String.valueOf(Math.round(blocks)));
    }

    public static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /** Palette key of a text/icon color, or null for custom colors. */
    public static String paletteKeyOfLegacy(String legacy, ColorPalette palette) {
        return palette.byLegacy(legacy).map(ColorPalette.Entry::key).orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
