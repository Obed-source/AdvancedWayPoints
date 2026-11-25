package io.github.obedsource.advancedwaypoints.api.event;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/** Base class of every AdvancedWayPoints event. Fired asynchronously when not on the server thread. */
public abstract class WaypointEvent extends Event {

    private final Waypoint waypoint;

    protected WaypointEvent(Waypoint waypoint) {
        super(!Bukkit.isPrimaryThread());
        this.waypoint = waypoint;
    }

    /** A copy of the waypoint involved. */
    public Waypoint getWaypoint() {
        return waypoint;
    }
}
