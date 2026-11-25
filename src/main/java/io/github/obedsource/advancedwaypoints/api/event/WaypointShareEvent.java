package io.github.obedsource.advancedwaypoints.api.event;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import org.bukkit.event.Cancellable;
import java.util.UUID;
import org.bukkit.event.HandlerList;

/** Fired before a waypoint copy is given to another player. */
public class WaypointShareEvent extends WaypointEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID target;
    private boolean cancelled;

    public WaypointShareEvent(Waypoint waypoint, UUID target) {
        super(waypoint);
        this.target = target;
    }

    /** The player who will receive a copy of the waypoint. */
    public UUID getTarget() {
        return target;
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
