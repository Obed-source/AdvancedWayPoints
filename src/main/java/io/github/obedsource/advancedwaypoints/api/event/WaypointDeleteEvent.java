package io.github.obedsource.advancedwaypoints.api.event;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before a waypoint is deleted. Cancelling it keeps the waypoint. */
public class WaypointDeleteEvent extends WaypointEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled;

    public WaypointDeleteEvent(Waypoint waypoint) {
        super(waypoint);

    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
