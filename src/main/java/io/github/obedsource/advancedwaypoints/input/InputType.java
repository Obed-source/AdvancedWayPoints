package io.github.obedsource.advancedwaypoints.input;

import java.util.Locale;

/** Ways to ask a player for text, from best to most basic. */
public enum InputType {
    /** An anvil whose rename field holds the text (needs Paper or Spigot 1.21+). */
    ANVIL,
    /** A temporary sign only the player edits. */
    SIGN,
    /** A clickable message that pre-fills the command in the chat box. */
    COMMAND,
    /** The next chat message is captured as the value. */
    CHAT;

    public static InputType parse(String value, InputType fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
