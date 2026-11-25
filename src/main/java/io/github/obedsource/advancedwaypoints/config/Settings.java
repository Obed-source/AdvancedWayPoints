package io.github.obedsource.advancedwaypoints.config;

import io.github.obedsource.advancedwaypoints.input.InputType;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable snapshot of config.yml. A new instance is built on every load or restart. */
public final class Settings {

    public enum RestrictionMode {
        DISABLED, WHITELIST, BLACKLIST;

        static RestrictionMode parse(String value) {
            if (value == null) {
                return DISABLED;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return DISABLED;
            }
        }
    }

    // General
    public final String language;
    public final String messagePrefix;
    public final String commandName;
    public final List<String> commandAliases;
    public final String noArgsAction;
    public final boolean debug;
    public final String databaseFile;

    // Waypoints
    public final int nameMaxLength;
    public final Pattern nameCharacters;
    public final int defaultLimit;
    public final boolean showOnCreate;
    public final boolean newestFirst;
    public final List<String> icons;
    public final String defaultIcon;
    public final boolean randomIcon;
    public final boolean randomIconColor;
    public final boolean randomTextColor;
    public final String defaultTextColorKey;
    public final String defaultIconColorKey;
    public final String defaultBackgroundKey;
    public final int backgroundAlpha;

    // Restrictions
    public final RestrictionMode worldMode;
    public final Set<String> worlds;
    public final RestrictionMode regionMode;
    public final Set<String> regions;

    // Display
    public final String displayFormat;
    public final boolean showDistance;
    public final String distanceFormat;
    public final String metersFormat;
    public final String kilometersFormat;
    public final double kilometersThreshold;
    public final float baseScale;
    public final boolean scaleWithDistance;
    public final double scaleReferenceDistance;
    public final float maxScale;
    public final double maxRenderDistance;
    public final double hideWithin;
    public final double heightOffset;
    public final long updateInterval;
    public final boolean seeThrough;
    public final boolean shadowed;
    public final boolean fullBright;
    public final int lineWidth;
    public final int textOpacity;
    public final int teleportDuration;

    // Chat list and help
    public final int listPageSize;
    public final boolean showIdOnHover;
    public final DateTimeFormatter dateFormat;
    public final int helpPageSize;

    // Input
    public final InputType inputType;
    public final int inputTimeoutSeconds;
    public final String inputCancelWord;

    // Menus
    public final boolean hideItemsWithoutPermission;

    // Sharing
    public final boolean shareAllowOffline;
    public final boolean shareCheckTargetLimit;

    // Costs (money or items) per action: create, share, teleport
    public final CostSpec createCost;
    public final CostSpec shareCost;
    public final CostSpec teleportCost;

    // Teleport
    public final boolean teleportEnabled;
    public final boolean teleportShortcut;
    public final boolean teleportInEditMenu;

    // Death waypoints
    public final boolean deathEnabled;
    public final RestrictionMode deathWorldMode;
    public final Set<String> deathWorlds;
    public final long deathDurationSeconds;
    public final int deathKeep;
    public final boolean deathShow;
    public final boolean deathCountsTowardLimit;
    public final String deathIcon;
    public final String deathIconColorKey;
    public final String deathTextColorKey;
    public final String deathBackgroundKey;
    public final String deathFormat;
    public final String timeLeftFormat;
    public final String timeLeftHoursFormat;

    // Updater
    public final boolean updaterEnabled;
    public final boolean updaterAutoDownload;
    public final boolean updaterNotifyAdmins;
    public final String updaterUrl;

    public final ColorPalette palette;

    public Settings(FileConfiguration c, Logger logger) {
        language = c.getString("language", "en").toLowerCase(Locale.ROOT);
        messagePrefix = c.getString("message-prefix", "");
        commandName = c.getString("command.name", "waypoints").toLowerCase(Locale.ROOT).trim();
        List<String> aliases = new ArrayList<>();
        for (String alias : c.getStringList("command.aliases")) {
            String value = alias.toLowerCase(Locale.ROOT).trim();
            if (!value.isEmpty() && !value.equals(commandName)) {
                aliases.add(value);
            }
        }
        commandAliases = Collections.unmodifiableList(aliases);
        noArgsAction = c.getString("command.no-args-action", "menu").toLowerCase(Locale.ROOT);
        debug = c.getBoolean("debug", false);
        databaseFile = c.getString("storage.file", "waypoints.db");

        nameMaxLength = Math.max(1, c.getInt("waypoints.name.max-length", 32));
        Pattern pattern;
        try {
            pattern = Pattern.compile("[" + c.getString("waypoints.name.allowed-characters", "\\p{L}\\p{N} ") + "]+");
        } catch (PatternSyntaxException e) {
            logger.warning("Invalid waypoints.name.allowed-characters, using letters, digits and spaces");
            pattern = Pattern.compile("[\\p{L}\\p{N} ]+");
        }
        nameCharacters = pattern;
        defaultLimit = Math.max(0, c.getInt("waypoints.default-limit", 3));
        showOnCreate = c.getBoolean("waypoints.show-on-create", true);
        newestFirst = c.getBoolean("waypoints.newest-first", false);
        List<String> iconList = new ArrayList<>(c.getStringList("waypoints.icons"));
        iconList.removeIf(s -> s == null || s.isEmpty());
        defaultIcon = c.getString("waypoints.default-icon", "•");
        if (iconList.isEmpty()) {
            iconList.add(defaultIcon);
        }
        icons = Collections.unmodifiableList(iconList);
        randomIcon = c.getBoolean("waypoints.random-icon", true);
        randomIconColor = c.getBoolean("waypoints.random-icon-color", true);
        randomTextColor = c.getBoolean("waypoints.random-text-color", false);
        defaultTextColorKey = c.getString("waypoints.default-text-color", "white").toLowerCase(Locale.ROOT);
        defaultIconColorKey = c.getString("waypoints.default-icon-color", "white").toLowerCase(Locale.ROOT);
        defaultBackgroundKey = c.getString("waypoints.default-background-color", "blue").toLowerCase(Locale.ROOT);
        backgroundAlpha = clamp(c.getInt("waypoints.background-alpha", 160), 0, 255);

        worldMode = RestrictionMode.parse(c.getString("restrictions.worlds.mode"));
        worlds = lowerSet(c.getStringList("restrictions.worlds.list"));
        regionMode = RestrictionMode.parse(c.getString("restrictions.regions.mode"));
        regions = lowerSet(c.getStringList("restrictions.regions.list"));

        displayFormat = c.getString("display.format", "{icon_color}{icon} {text_color}{name}{distance}");
        showDistance = c.getBoolean("display.distance.enabled", true);
        distanceFormat = c.getString("display.distance.format", " &7({distance})");
        metersFormat = c.getString("display.distance.meters", "{value}m");
        kilometersFormat = c.getString("display.distance.kilometers", "{value}km");
        kilometersThreshold = c.getDouble("display.distance.kilometers-from", 1000D);
        baseScale = (float) c.getDouble("display.scale.base", 1.0D);
        scaleWithDistance = c.getBoolean("display.scale.grow-with-distance", true);
        scaleReferenceDistance = Math.max(1D, c.getDouble("display.scale.reference-distance", 12D));
        maxScale = (float) Math.max(baseScale, c.getDouble("display.scale.max", 4D));
        maxRenderDistance = Math.max(4D, c.getDouble("display.max-render-distance", 28D));
        hideWithin = Math.max(0D, c.getDouble("display.hide-within", 0D));
        heightOffset = c.getDouble("display.height-offset", 1.5D);
        updateInterval = Math.max(1L, c.getLong("display.update-interval-ticks", 2L));
        seeThrough = c.getBoolean("display.see-through", true);
        shadowed = c.getBoolean("display.text-shadow", true);
        fullBright = c.getBoolean("display.full-bright", true);
        lineWidth = Math.max(10, c.getInt("display.line-width", 200));
        textOpacity = clamp(c.getInt("display.text-opacity", 255), 0, 255);
        teleportDuration = clamp(c.getInt("display.smooth-movement-ticks", 2), 0, 59);

        listPageSize = Math.max(1, c.getInt("list.page-size", 8));
        showIdOnHover = c.getBoolean("list.show-id-on-hover", false);
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(c.getString("list.date-format", "dd/MM/yyyy HH:mm"));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid list.date-format, using dd/MM/yyyy HH:mm");
            formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        }
        dateFormat = formatter.withZone(ZoneId.systemDefault());
        helpPageSize = Math.max(1, c.getInt("help.page-size", 8));

        inputType = InputType.parse(c.getString("input.type"), InputType.ANVIL);
        inputTimeoutSeconds = Math.max(5, c.getInt("input.timeout-seconds", 60));
        inputCancelWord = c.getString("input.cancel-word", "cancel");

        hideItemsWithoutPermission = c.getBoolean("menus.hide-items-without-permission", false);

        shareAllowOffline = c.getBoolean("share.allow-offline-players", true);
        shareCheckTargetLimit = c.getBoolean("share.respect-target-limit", true);

        createCost = CostSpec.load(c.getConfigurationSection("costs.create"), logger);
        shareCost = CostSpec.load(c.getConfigurationSection("costs.share"), logger);
        teleportCost = CostSpec.load(c.getConfigurationSection("costs.teleport"), logger);

        teleportEnabled = c.getBoolean("teleport.enabled", true);
        teleportShortcut = c.getBoolean("teleport.shortcut", true);
        teleportInEditMenu = c.getBoolean("teleport.in-edit-menu", true);

        deathEnabled = c.getBoolean("death-waypoint.enabled", true);
        deathWorldMode = RestrictionMode.parse(c.getString("death-waypoint.worlds.mode"));
        deathWorlds = lowerSet(c.getStringList("death-waypoint.worlds.list"));
        deathDurationSeconds = Math.max(0L, c.getLong("death-waypoint.duration-seconds", 600L));
        deathKeep = Math.max(1, c.getInt("death-waypoint.keep", 1));
        deathShow = c.getBoolean("death-waypoint.show-automatically", true);
        deathCountsTowardLimit = c.getBoolean("death-waypoint.counts-toward-limit", false);
        deathIcon = c.getString("death-waypoint.icon", "☠");
        deathIconColorKey = c.getString("death-waypoint.icon-color", "red").toLowerCase(Locale.ROOT);
        deathTextColorKey = c.getString("death-waypoint.text-color", "red").toLowerCase(Locale.ROOT);
        deathBackgroundKey = c.getString("death-waypoint.background-color", "black").toLowerCase(Locale.ROOT);
        deathFormat = c.getString("death-waypoint.format", "{icon_color}{icon} {text_color}{name}{distance} &7{time_left}");
        timeLeftFormat = c.getString("death-waypoint.time-left.format", "{minutes}:{seconds}");
        timeLeftHoursFormat = c.getString("death-waypoint.time-left.hours-format", "{hours}:{minutes}:{seconds}");

        updaterEnabled = c.getBoolean("updater.enabled", true);
        updaterAutoDownload = c.getBoolean("updater.auto-download", true);
        updaterNotifyAdmins = c.getBoolean("updater.notify-admins", true);
        updaterUrl = c.getString("updater.url",
                "https://raw.githubusercontent.com/Obed-source/AdvancedWayPoints/main/update.json");

        palette = ColorPalette.load(c.getConfigurationSection("colors"), logger);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            set.add(value.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(set);
    }
}
