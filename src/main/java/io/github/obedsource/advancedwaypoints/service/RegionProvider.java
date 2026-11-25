package io.github.obedsource.advancedwaypoints.service;

import org.bukkit.Location;

import java.util.Set;

/** Region plugin bridge (WorldGuard). Keeps the business code free of WorldGuard classes. */
public interface RegionProvider {

    /** Lower case ids of the regions at the location. */
    Set<String> regionsAt(Location location);
}
