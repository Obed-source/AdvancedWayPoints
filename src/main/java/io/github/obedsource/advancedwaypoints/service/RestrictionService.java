package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.Settings.RestrictionMode;
import org.bukkit.Location;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Checks the world and region whitelist/blacklist rules for creating waypoints. */
public final class RestrictionService {

    private final Supplier<Settings> settings;
    private final RegionProvider regions;

    /** @param regions null when WorldGuard is not installed */
    public RestrictionService(Supplier<Settings> settings, RegionProvider regions) {
        this.settings = settings;
        this.regions = regions;
    }

    /** Language key of the reason the location is not allowed, or empty when allowed. */
    public Optional<String> check(Location location) {
        Settings s = settings.get();
        String world = location.getWorld() == null ? "" : location.getWorld().getName().toLowerCase(Locale.ROOT);
        if (s.worldMode == RestrictionMode.WHITELIST && !s.worlds.contains(world)) {
            return Optional.of("messages.world-not-allowed");
        }
        if (s.worldMode == RestrictionMode.BLACKLIST && s.worlds.contains(world)) {
            return Optional.of("messages.world-not-allowed");
        }
        if (s.regionMode == RestrictionMode.DISABLED || regions == null) {
            return Optional.empty();
        }
        Set<String> here = regions.regionsAt(location);
        boolean listed = false;
        for (String region : here) {
            if (s.regions.contains(region) || s.regions.contains(world + ":" + region)) {
                listed = true;
                break;
            }
        }
        if (s.regionMode == RestrictionMode.WHITELIST && !listed) {
            return Optional.of("messages.region-not-allowed");
        }
        if (s.regionMode == RestrictionMode.BLACKLIST && listed) {
            return Optional.of("messages.region-not-allowed");
        }
        return Optional.empty();
    }
}
