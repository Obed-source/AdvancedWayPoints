package io.github.obedsource.advancedwaypoints.api.event;

import io.github.obedsource.advancedwaypoints.api.Waypoint;

import org.bukkit.event.HandlerList;

/** Fired after a waypoint changed (the waypoint is the new state). */
public class WaypointUpdateEvent extends WaypointEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Change change;

    /** What changed. */
    public enum Change { NAME, TEXT_COLOR, BACKGROUND_COLOR, ICON_COLOR, ICON, FAVORITE }


    public WaypointUpdateEvent(Waypoint waypoint, Change change) {
        super(waypoint);
        this.change = change;
    }

    public Change getChange() {
        return change;
    }


    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
