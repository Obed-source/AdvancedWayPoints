package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Text input from the player's next chat message (the message is not broadcast). */
public final class ChatInputProvider implements InputProvider, Listener {

    private static final class Session {
        final InputRequest request;

        Session(InputRequest request) {
            this.request = request;
        }
    }

    private final InputContext ctx;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ChatInputProvider(InputContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public InputType type() {
        return InputType.CHAT;
    }

    @Override
    public boolean isAvailable(InputRequest request) {
        return true;
    }

    @Override
    public boolean open(Player player, InputRequest request) {
        Session session = new Session(request);
        sessions.put(player.getUniqueId(), session);
        ctx.messages().get().send(player, "input.chat-prompt." + request.purpose(),
                Text.ph("cancel", ctx.settings().get().inputCancelWord,
                        "seconds", ctx.settings().get().inputTimeoutSeconds));
        ctx.scheduler().runAtEntityLater(player, () -> {
            if (sessions.remove(player.getUniqueId(), session)) {
                ctx.messages().get().send(player, "input.timeout");
                request.onCancel().run();
            }
        }, ctx.settings().get().inputTimeoutSeconds * 20L);
        return true;
    }

    // AsyncPlayerChatEvent is deprecated on Paper but still fired everywhere, Spigot included.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String message = Text.stripSectionCodes(event.getMessage()).trim();
        boolean cancelled = message.isEmpty() || message.equalsIgnoreCase(ctx.settings().get().inputCancelWord);
        ctx.scheduler().runAtEntity(player, () -> {
            if (cancelled) {
                session.request.onCancel().run();
            } else {
                session.request.onSubmit().accept(message);
            }
        });
    }

    @Override
    public void cancel(UUID player) {
        sessions.remove(player);
    }

    @Override
    public void shutdown() {
        sessions.clear();
    }
}
