package io.github.obedsource.advancedwaypoints.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.obedsource.advancedwaypoints.service.RegionProvider;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** WorldGuard bridge. Only loaded when WorldGuard is installed. */
public final class WorldGuardRegions implements RegionProvider {

    @Override
    public Set<String> regionsAt(Location location) {
        Set<String> ids = new HashSet<>();
        ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));
        for (ProtectedRegion region : set) {
            ids.add(region.getId().toLowerCase(Locale.ROOT));
        }
        return ids;
    }
}
