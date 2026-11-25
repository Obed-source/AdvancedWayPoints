package io.github.obedsource.advancedwaypoints.integration;

import io.github.obedsource.advancedwaypoints.api.AdvancedWaypointsAPI;
import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.service.ActionResult;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Implementation of the public API on top of the plugin's services. */
public final class WaypointsApiImpl implements AdvancedWaypointsAPI {

    private final SessionManager sessions;
    private final WaypointService waypoints;
    private final PlatformScheduler scheduler;

    public WaypointsApiImpl(SessionManager sessions, WaypointService waypoints, PlatformScheduler scheduler) {
        this.sessions = sessions;
        this.waypoints = waypoints;
        this.scheduler = scheduler;
    }

    private static List<Waypoint> copies(OwnerData data) {
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint waypoint : data.all()) {
            list.add(waypoint.copy());
        }
        return list;
    }

    @Override
    public CompletableFuture<List<Waypoint>> getWaypoints(UUID owner) {
        return sessions.getOrLoad(owner, null).thenApply(WaypointsApiImpl::copies);
    }

    @Override
    public Optional<List<Waypoint>> getCachedWaypoints(UUID owner) {
        OwnerData data = sessions.getOnline(owner);
        return data == null ? Optional.empty() : Optional.of(copies(data));
    }

    @Override
    public CompletableFuture<Waypoint> createWaypoint(UUID owner, String name, Location location) {
        return sessions.getOrLoad(owner, null).thenCompose(data -> {
            CompletableFuture<Waypoint> result = new CompletableFuture<>();
            scheduler.runAtLocation(location, () -> {
                ActionResult action = waypoints.create(null, data, name, location, false);
                if (action.success()) {
                    result.complete(action.waypoint().copy());
                } else {
                    result.completeExceptionally(new IllegalArgumentException(action.messageKey()));
                }
            });
            return result;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWaypoint(UUID owner, String code) {
        return sessions.getOrLoad(owner, null).thenCompose(data -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            scheduler.runGlobal(() -> result.complete(data.hasCode(code) && waypoints.delete(data, code).success()));
            return result;
        });
    }

    @Override
    public boolean showWaypoint(Player player, String code) {
        OwnerData data = sessions.getOnline(player.getUniqueId());
        return data != null && waypoints.show(data, code).success();
    }

    @Override
    public boolean hideWaypoint(Player player, String code) {
        OwnerData data = sessions.getOnline(player.getUniqueId());
        return data != null && waypoints.hide(data, code).success();
    }

    @Override
    public void hideAll(Player player) {
        OwnerData data = sessions.getOnline(player.getUniqueId());
        if (data != null) {
            waypoints.hideAll(data);
        }
    }

    @Override
    public List<String> getVisibleWaypoints(Player player) {
        OwnerData data = sessions.getOnline(player.getUniqueId());
        return data == null ? Collections.emptyList() : data.visible();
    }

    @Override
    public int getLimit(Player player) {
        return waypoints.limits().limitOf(player);
    }

    @Override
    public OptionalDouble getDistance(Player player, String code) {
        OwnerData data = sessions.getOnline(player.getUniqueId());
        if (data == null) {
            return OptionalDouble.empty();
        }
        return data.get(code).map(w -> DisplayManager.distance(player, w)).orElse(OptionalDouble.empty());
    }
}
