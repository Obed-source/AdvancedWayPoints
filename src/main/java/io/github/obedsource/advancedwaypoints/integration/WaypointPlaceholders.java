package io.github.obedsource.advancedwaypoints.integration;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.CostSpec;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.service.CostService;
import io.github.obedsource.advancedwaypoints.service.LimitService;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import io.github.obedsource.advancedwaypoints.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * PlaceholderAPI expansion, identifier "awp". Values come from memory (online players only).
 * <p>Account: {@code count limit remaining visible_count favorites mode deaths_count deaths_max}
 * <p>Lists: {@code visible_list visible_distances}
 * <p>Waypoint fields ({@code <field>}: name, name_plain, code, icon, label, world, x, y, z, distance,
 * distance_formatted, distance_raw, time_left, created, type, favorite, visible):
 * {@code selected_<field>}, {@code nearest_<field>}, {@code death_<field>} (newest death point),
 * {@code death_<n>_<field>} (n = 1 newest), {@code <field>_<code>}
 * <p>Prices ({@code <action>}: create, share, teleport, death_share, death_teleport):
 * {@code cost_<action>} (as the player would pay it), {@code cost_<action>_type} (MONEY, ITEM, NONE),
 * {@code cost_<action>_amount}, {@code cost_<action>_item}
 */
public final class WaypointPlaceholders extends PlaceholderExpansion {

    private static final String[] FIELDS = {"distance_formatted", "distance_raw", "name_plain", "time_left", "distance",
            "favorite", "created", "visible", "label", "world", "name", "code", "icon", "type", "x", "y", "z"};

    private final Plugin plugin;
    private final SessionManager sessions;
    private final LimitService limits;
    private final CostService costs;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;

    public WaypointPlaceholders(Plugin plugin, SessionManager sessions, LimitService limits, CostService costs,
                                Supplier<Settings> settings, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.limits = limits;
        this.costs = costs;
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

    private String none() {
        return messages.get().raw("placeholders.none");
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
                return limit == Integer.MAX_VALUE ? LimitService.display(limit)
                        : String.valueOf(Math.max(0, limit - data.countForLimit(s.deathCountsTowardLimit)));
            }
            case "visible_count":
                return String.valueOf(data.visible().size());
            case "favorites":
                return String.valueOf(data.all().stream().filter(Waypoint::isFavorite).count());
            case "deaths_count":
                return String.valueOf(deaths(data).size());
            case "deaths_max":
                return String.valueOf(s.deathMax);
            case "mode":
                return messages.get().raw(data.accumulate() ? "placeholders.mode-accumulate" : "placeholders.mode-single");
            case "visible_list": {
                List<String> names = new ArrayList<>();
                for (String code : data.visible()) {
                    data.get(code).ifPresent(w -> names.add(Text.color(w.getTextColor() + w.getName())));
                }
                return names.isEmpty() ? none() : String.join(Text.color("&7, "), names);
            }
            case "visible_distances": {
                List<String> parts = new ArrayList<>();
                for (String code : data.visible()) {
                    data.get(code).ifPresent(w -> parts.add(WaypointFormat.label(w, s, distanceText(player, w))));
                }
                return parts.isEmpty() ? none() : String.join(Text.color("&7, "), parts);
            }
            default:
                break;
        }
        if (key.startsWith("cost_")) {
            return cost(player, key.substring("cost_".length()), s);
        }
        if (key.startsWith("selected_")) {
            Optional<Waypoint> selected = data.lastSelected();
            return selected.isPresent() ? describe(player, data, selected.get(), key.substring(9), s) : none();
        }
        if (key.startsWith("nearest_")) {
            Waypoint nearest = nearest(player, data);
            return nearest == null ? none() : describe(player, data, nearest, key.substring(8), s);
        }
        if (key.startsWith("death_")) {
            String rest = key.substring(6);
            int index = 0;
            int underscore = rest.indexOf('_');
            if (underscore > 0 && rest.substring(0, underscore).chars().allMatch(Character::isDigit)) {
                index = Integer.parseInt(rest.substring(0, underscore)) - 1;
                rest = rest.substring(underscore + 1);
            }
            List<Waypoint> deaths = deaths(data);
            return index >= 0 && index < deaths.size() ? describe(player, data, deaths.get(index), rest, s) : none();
        }
        // <field>_<code>: the code keeps its case, so read it from the original params.
        for (String field : FIELDS) {
            if (key.startsWith(field + "_") && params.length() > field.length() + 1) {
                Optional<Waypoint> waypoint = data.get(params.substring(field.length() + 1));
                return waypoint.map(w -> describe(player, data, w, field, s)).orElse("");
            }
        }
        return null;
    }

    private static List<Waypoint> deaths(OwnerData data) {
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint waypoint : data.sorted(false)) {
            if (waypoint.isDeath()) {
                list.add(waypoint);
            }
        }
        return list;
    }

    private static Waypoint nearest(Player player, OwnerData data) {
        Waypoint nearest = null;
        double best = Double.MAX_VALUE;
        for (Waypoint waypoint : data.all()) {
            OptionalDouble d = DisplayManager.distance(player, waypoint);
            if (d.isPresent() && d.getAsDouble() < best) {
                best = d.getAsDouble();
                nearest = waypoint;
            }
        }
        return nearest;
    }

    private String cost(Player player, String rest, Settings s) {
        String[] actions = {"death_teleport", "death_share", "teleport", "create", "share"};
        for (String action : actions) {
            if (!rest.startsWith(action)) {
                continue;
            }
            String suffix = rest.substring(action.length());
            CostSpec cost;
            switch (action) {
                case "death_teleport":
                    cost = s.deathTeleportCost;
                    break;
                case "death_share":
                    cost = s.deathShareCost;
                    break;
                case "teleport":
                    cost = s.teleportCost;
                    break;
                case "create":
                    cost = s.createCost;
                    break;
                default:
                    cost = s.shareCost;
                    break;
            }
            boolean charged = costs.isCharged(player, cost);
            switch (suffix) {
                case "":
                    return charged ? costs.describe(cost) : messages.get().raw("placeholders.free");
                case "_type":
                    return cost.active() ? cost.type.name() : "NONE";
                case "_amount":
                    return !cost.active() ? "0" : cost.type == CostSpec.Type.MONEY
                            ? String.format(Locale.ROOT, "%.2f", cost.money) : String.valueOf(cost.amount);
                case "_item":
                    return cost.active() && cost.type == CostSpec.Type.ITEM ? cost.material.name() : "";
                default:
                    return null;
            }
        }
        return null;
    }

    private String distanceText(Player player, Waypoint waypoint) {
        OptionalDouble distance = DisplayManager.distance(player, waypoint);
        return distance.isPresent() ? WaypointFormat.distance(distance.getAsDouble(), settings.get()) : null;
    }

    private String describe(Player player, OwnerData data, Waypoint waypoint, String field, Settings s) {
        Messages m = messages.get();
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
                return distance == null ? m.raw("placeholders.other-world") : distance;
            }
            case "distance_formatted": {
                String distance = distanceText(player, waypoint);
                return distance == null ? m.raw("placeholders.other-world")
                        : Text.color(Text.replace(s.distanceFormat, Text.ph("distance", distance)));
            }
            case "distance_raw": {
                OptionalDouble distance = DisplayManager.distance(player, waypoint);
                return distance.isPresent() ? String.valueOf(Math.round(distance.getAsDouble())) : "-1";
            }
            case "time_left": {
                String left = WaypointFormat.timeLeft(waypoint, s);
                return left.isEmpty() ? m.raw("placeholders.never") : left;
            }
            case "created":
                return s.dateFormat.format(Instant.ofEpochMilli(waypoint.getCreatedAt()));
            case "type":
                return m.raw(waypoint.isDeath() ? "placeholders.type-death" : "placeholders.type-normal");
            case "favorite":
                return m.raw(waypoint.isFavorite() ? "placeholders.answer-yes" : "placeholders.answer-no");
            case "visible":
                return m.raw(data.isVisible(waypoint.getCode()) ? "placeholders.answer-yes" : "placeholders.answer-no");
            default:
                return "";
        }
    }
}
