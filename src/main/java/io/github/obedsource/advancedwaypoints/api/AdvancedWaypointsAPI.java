package io.github.obedsource.advancedwaypoints.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public developer API of AdvancedWayPoints.
 * <pre>{@code
 * AdvancedWaypointsAPI api = AdvancedWaypointsAPI.get();
 * api.getWaypoints(player.getUniqueId()).thenAccept(list -> ...);
 * }</pre>
 * Futures complete on a background thread: switch back to the server thread before touching the world.
 * Events: {@link io.github.obedsource.advancedwaypoints.api.event.WaypointCreateEvent},
 * {@link io.github.obedsource.advancedwaypoints.api.event.WaypointDeleteEvent},
 * {@link io.github.obedsource.advancedwaypoints.api.event.WaypointShareEvent},
 * {@link io.github.obedsource.advancedwaypoints.api.event.WaypointUpdateEvent},
 * {@link io.github.obedsource.advancedwaypoints.api.event.WaypointVisibilityEvent}.
 */
public interface AdvancedWaypointsAPI {

    /** Returns the API instance registered in Bukkit's ServicesManager. */
    static AdvancedWaypointsAPI get() {
        RegisteredServiceProvider<AdvancedWaypointsAPI> provider =
                Bukkit.getServicesManager().getRegistration(AdvancedWaypointsAPI.class);
        if (provider == null) {
            throw new IllegalStateException("AdvancedWayPoints is not enabled");
        }
        return provider.getProvider();
    }

    /** Copies of every waypoint of a player, online or offline. */
    CompletableFuture<List<Waypoint>> getWaypoints(UUID owner);

    /** Copies of the waypoints of an online player, from memory. Empty when the player is not loaded. */
    Optional<List<Waypoint>> getCachedWaypoints(UUID owner);

    /**
     * Creates a waypoint for a player. Name rules apply, limits, costs and region rules do not.
     * Completes with the new waypoint, or exceptionally when the name is invalid or the event was cancelled.
     */
    CompletableFuture<Waypoint> createWaypoint(UUID owner, String name, Location location);

    /** Deletes a waypoint. Completes with false when it does not exist or the event was cancelled. */
    CompletableFuture<Boolean> deleteWaypoint(UUID owner, String code);

    /** Shows one of the player's own waypoints (respects the player's view mode). */
    boolean showWaypoint(Player player, String code);

    /** Hides one of the player's own waypoints from view (it stays in the list). */
    boolean hideWaypoint(Player player, String code);

    /** Hides every waypoint of the player from view. */
    void hideAll(Player player);

    /** Codes of the player's visible waypoints, in selection order. */
    List<String> getVisibleWaypoints(Player player);

    /** Maximum waypoints the player may own, from wp.limit.&lt;n&gt;. */
    int getLimit(Player player);

    /** Distance in blocks from the player to one of their waypoints, when both are in the same world. */
    OptionalDouble getDistance(Player player, String code);
}
