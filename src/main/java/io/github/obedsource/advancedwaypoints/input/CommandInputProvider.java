package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.util.Chat;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * "Input" by command: sends a clickable message that writes the command in the player's chat box,
 * so they only type the value and press enter. The command itself performs the action.
 */
public final class CommandInputProvider implements InputProvider {

    private final InputContext ctx;

    public CommandInputProvider(InputContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public InputType type() {
        return InputType.COMMAND;
    }

    @Override
    public boolean isAvailable(InputRequest request) {
        return request.commandSuggestion() != null;
    }

    @Override
    public boolean open(Player player, InputRequest request) {
        String text = ctx.messages().get().prefix()
                + ctx.messages().get().raw("input.command-prompt." + request.purpose());
        Chat.send(player, Chat.line(text, ctx.messages().get().rawList("input.command-hover"),
                ClickEvent.Action.SUGGEST_COMMAND, request.commandSuggestion() + " ", null));
        return true;
    }

    @Override
    public void cancel(UUID player) {
    }

    @Override
    public void shutdown() {
    }
}
