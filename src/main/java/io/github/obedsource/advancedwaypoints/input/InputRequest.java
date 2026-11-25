package io.github.obedsource.advancedwaypoints.input;

import java.util.function.Consumer;

/**
 * A request for one line of text.
 *
 * @param purpose        language key part: "rename", "share" or "create"
 * @param initial        text shown first (may be empty)
 * @param commandSuggestion command written for the player in COMMAND mode, without the value (may be null)
 * @param onSubmit       receives the text, on the player's thread
 * @param onCancel       runs when the player closes/cancels, on the player's thread
 */
public record InputRequest(String purpose, String initial, String commandSuggestion,
                           Consumer<String> onSubmit, Runnable onCancel) {
}
