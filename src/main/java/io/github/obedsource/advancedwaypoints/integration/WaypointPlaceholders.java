package io.github.obedsource.advancedwaypoints.integration;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.service.LimitService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import io.github.obedsource.advancedwaypoints.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * PlaceholderAPI expansion, identifier "awp". Values come from memory (online players only).
 * <pre>
 * %awp_count%  %awp_limit%  %awp_remaining%  %awp_visible_count%  %awp_favorites%  %awp_mode%
 * %awp_selected_name%  %awp_selected_name_plain%  %awp_selected_icon%  %awp_selected_label%
 * %awp_selected_distance%  %awp_selected_distance_formatted%  %awp_selected_distance_raw%
 * %awp_selected_world%  %awp_selected_x%  %awp_selected_y%  %awp_selected_z%  %awp_selected_code%
 * %awp_visible_list%  %awp_visible_distances%  %awp_nearest_name%  %awp_nearest_distance%
 * %awp_name_&lt;code&gt;%  %awp_distance_&lt;code&gt;%  %awp_visible_&lt;code&gt;%
 * </pre>
 */
public final class WaypointPlaceholders extends PlaceholderExpansion {

    private final Plugin plugin;
    private final SessionManager sessions;
    private final LimitService limits;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;

    public WaypointPlaceholders(Plugin plugin, SessionManager sessions, LimitService limits,
                                Supplier<Settings> settings, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.limits = limits;
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public String getIdentifier() {
        return "awp";
    }

    @Override
    public String getAuthor() {
        return "Obed-Source";
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (offline == null || !offline.isOnline()) {
            return "";
        }
        Player player = offline.getPlayer();
        OwnerData data = player == null ? null : sessions.getOnline(player.getUniqueId());
        if (data == null) {
            return "";
        }
        Settings s = settings.get();
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "count":
                return String.valueOf(data.count());
            case "limit":
                return LimitService.display(limits.limitOf(player));
            case "remaining": {
                int limit = limits.limitOf(player);
                return limit == Integer.MAX_VALUE ? LimitService.display(limit) : String.valueOf(Math.max(0, limit - data.count()));
            }
            case "visible_count":
                return String.valueOf(data.visible().size());
            case "favorites":
                return String.valueOf(data.all().stream().filter(Waypoint::isFavorite).count());
            case "mode":
                return messages.get().raw(data.accumulate() ? "placeholders.mode-accumulate" : "placeholders.mode-single");
            case "visible_list": {
                List<String> names = new ArrayList<>();
                for (String code : data.visible()) {
                    data.get(code).ifPresent(w -> names.add(Text.color(w.getTextColor() + w.getName())));
                }
                return names.isEmpty() ? messages.get().raw("placeholders.none") : String.join(Text.color("&7, "), names);
            }
            case "visible_distances": {
                List<String> parts = new ArrayList<>();
                for (String code : data.visible()) {
                    data.get(code).ifPresent(w -> parts.add(WaypointFormat.label(w, s, distanceText(player, w))));
                }
                return parts.isEmpty() ? messages.get().raw("placeholders.none") : String.join(Text.color("&7, "), parts);
            }
            case "nearest_name":
            case "nearest_distance": {
                Waypoint nearest = null;
                double best = Double.MAX_VALUE;
                for (Waypoint waypoint : data.all()) {
                    OptionalDouble d = DisplayManager.distance(player, waypoint);
                    if (d.isPresent() && d.getAsDouble() < best) {
                        best = d.getAsDouble();
                        nearest = waypoint;
                    }
                }
                if (nearest == null) {
                    return messages.get().raw("placeholders.none");
                }
                return key.equals("nearest_name") ? Text.color(nearest.getTextColor() + nearest.getName()) : WaypointFormat.distance(best, s);
            }
            default:
                break;
        }
        if (key.startsWith("selected_")) {
            Optional<Waypoint> selected = data.lastSelected();
            if (!selected.isPresent()) {
                return messages.get().raw("placeholders.none");
            }
            return describe(player, selected.get(), key.substring("selected_".length()), s);
        }
        int split = params.indexOf('_');
        if (split > 0) {
            String field = params.substring(0, split).toLowerCase(Locale.ROOT);
            String code = params.substring(split + 1);
            Optional<Waypoint> waypoint = data.get(code);
            if (!waypoint.isPresent()) {
                return "";
            }
            if (field.equals("visible")) {
                return messages.get().raw(data.isVisible(code) ? "placeholders.answer-yes" : "placeholders.answer-no");
            }
            return describe(player, waypoint.get(), field, s);
        }
        return null;
    }

    private String distanceText(Player player, Waypoint waypoint) {
        OptionalDouble distance = DisplayManager.distance(player, waypoint);
        return distance.isPresent() ? WaypointFormat.distance(distance.getAsDouble(), settings.get()) : null;
    }

    private String describe(Player player, Waypoint waypoint, String field, Settings s) {
        switch (field) {
            case "name":
                return Text.color(waypoint.getTextColor() + waypoint.getName());
            case "name_plain":
                return Text.strip(waypoint.getName());
            case "code":
                return waypoint.getCode();
            case "icon":
                return Text.color(waypoint.getIconColor() + waypoint.getIcon());
            case "label":
                return WaypointFormat.label(waypoint, s, distanceText(player, waypoint));
            case "world":
                return waypoint.getWorld();
            case "x":
                return String.valueOf(waypoint.getBlockX());
            case "y":
                return String.valueOf(waypoint.getBlockY());
            case "z":
                return String.valueOf(waypoint.getBlockZ());
            case "distance": {
                String distance = distanceText(player, waypoint);
                return distance == null ? messages.get().raw("placeholders.other-world") : distance;
            }
            case "distance_formatted": {
                String distance = distanceText(player, waypoint);
                return distance == null ? messages.get().raw("placeholders.other-world")
                        : Text.color(Text.replace(s.distanceFormat, Text.ph("distance", distance)));
            }
            case "distance_raw": {
                OptionalDouble distance = DisplayManager.distance(player, waypoint);
                return distance.isPresent() ? String.valueOf(Math.round(distance.getAsDouble())) : "-1";
            }
            default:
                return "";
        }
    }
}
