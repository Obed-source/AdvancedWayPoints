package io.github.obedsource.advancedwaypoints.input;

import org.bukkit.entity.Player;

import java.util.UUID;

/** One way of asking a player for text. */
public interface InputProvider {

    InputType type();

    /** Whether this method works on this server for this request. */
    boolean isAvailable(InputRequest request);

    /** Opens the input. Returns false when it could not be opened (the next method is tried). */
    boolean open(Player player, InputRequest request);

    /** Drops any pending input of the player without calling back. */
    void cancel(UUID player);

    /** Drops every pending input (plugin disable). */
    void shutdown();
}
