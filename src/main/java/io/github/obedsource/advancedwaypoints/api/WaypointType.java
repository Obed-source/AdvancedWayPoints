package io.github.obedsource.advancedwaypoints.api;

/** Kind of waypoint. */
public enum WaypointType {
    /** Created by the player (or an admin, or shared). */
    NORMAL,
    /** Created automatically where the player died; it may expire. */
    DEATH;

    public static WaypointType byId(int id) {
        return id == 1 ? DEATH : NORMAL;
    }

    public int id() {
        return this == DEATH ? 1 : 0;
    }
}
