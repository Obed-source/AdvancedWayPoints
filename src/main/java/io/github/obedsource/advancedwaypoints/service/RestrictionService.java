package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.Settings.RestrictionMode;
import org.bukkit.Location;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Checks the world and region whitelist/blacklist rules: restrictions.* for creating waypoints,
 * death-waypoint.worlds / death-waypoint.regions for death waypoints.
 */
public final class RestrictionService {

    private final Supplier<Settings> settings;
    private final RegionProvider regions;

    /** @param regions null when WorldGuard is not installed */
    public RestrictionService(Supplier<Settings> settings, RegionProvider regions) {
        this.settings = settings;
        this.regions = regions;
    }

    /** Language key of the reason the location is not allowed for a new waypoint, or empty when allowed. */
    public Optional<String> check(Location location) {
        Settings s = settings.get();
        return check(location, s.worldMode, s.worlds, s.regionMode, s.regions);
    }

    /** Whether a death waypoint may be left at this location. */
    public boolean allowsDeath(Location location) {
        Settings s = settings.get();
        return check(location, s.deathWorldMode, s.deathWorlds, s.deathRegionMode, s.deathRegions).isEmpty();
    }

    private Optional<String> check(Location location, RestrictionMode worldMode, Set<String> worlds,
                                   RestrictionMode regionMode, Set<String> regionList) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName().toLowerCase(Locale.ROOT);
        if (worldMode == RestrictionMode.WHITELIST && !worlds.contains(world)) {
            return Optional.of("messages.world-not-allowed");
        }
        if (worldMode == RestrictionMode.BLACKLIST && worlds.contains(world)) {
            return Optional.of("messages.world-not-allowed");
        }
        if (regionMode == RestrictionMode.DISABLED || regions == null) {
            return Optional.empty();
        }
        Set<String> here = regions.regionsAt(location);
        boolean listed = false;
        for (String region : here) {
            if (regionList.contains(region) || regionList.contains(world + ":" + region)) {
                listed = true;
                break;
            }
        }
        if (regionMode == RestrictionMode.WHITELIST && !listed) {
            return Optional.of("messages.region-not-allowed");
        }
        if (regionMode == RestrictionMode.BLACKLIST && listed) {
            return Optional.of("messages.region-not-allowed");
        }
        return Optional.empty();
    }
}
