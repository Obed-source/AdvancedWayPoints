package io.github.obedsource.advancedwaypoints.api.event;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Fired after a waypoint was shown to or hidden from its owner. */
public class WaypointVisibilityEvent extends WaypointEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean visible;


    public WaypointVisibilityEvent(Waypoint waypoint, Player player, boolean visible) {
        super(waypoint);
        this.player = player;
        this.visible = visible;
    }

    public Player getPlayer() {
        return player;
    }

    /** True when the waypoint became visible, false when it was hidden. */
    public boolean isVisible() {
        return visible;
    }


    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
