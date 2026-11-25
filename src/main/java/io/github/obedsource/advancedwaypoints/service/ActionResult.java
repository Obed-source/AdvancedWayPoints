package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.api.Waypoint;

import java.util.Collections;
import java.util.Map;

/**
 * Outcome of a business action: success flag, language key of the message to show,
 * its placeholders and the sound to play. The service never talks to players directly.
 */
public final class ActionResult {

    private final boolean success;
    private final String messageKey;
    private final Map<String, String> placeholders;
    private final String sound;
    private final Waypoint waypoint;

    private ActionResult(boolean success, String messageKey, Map<String, String> placeholders, String sound, Waypoint waypoint) {
        this.success = success;
        this.messageKey = messageKey;
        this.placeholders = placeholders == null ? Collections.emptyMap() : placeholders;
        this.sound = sound;
        this.waypoint = waypoint;
    }

    public static ActionResult ok(String messageKey, Map<String, String> placeholders, String sound, Waypoint waypoint) {
        return new ActionResult(true, messageKey, placeholders, sound, waypoint);
    }

    public static ActionResult fail(String messageKey, Map<String, String> placeholders) {
        return new ActionResult(false, messageKey, placeholders, null, null);
    }

    public static ActionResult fail(String messageKey) {
        return fail(messageKey, null);
    }

    public boolean success() {
        return success;
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }

    /** Sound key, or null to use the error sound. */
    public String sound() {
        return sound;
    }

    public Waypoint waypoint() {
        return waypoint;
    }
}
