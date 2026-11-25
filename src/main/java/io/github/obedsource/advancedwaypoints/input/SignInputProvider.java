package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text input with a sign editor, without any dependency: a temporary sign is placed in the player's own
 * block space (the server only accepts edits from nearby signs), the editor is opened, and the original
 * block is put back as soon as the player confirms, leaves or the time runs out.
 * The first two lines are the input; the last two show the prompt.
 */
public final class SignInputProvider implements InputProvider, Listener {

    private static final class Session {
        final InputRequest request;
        final Location location;
        final BlockState original;
        final long token;
        boolean done;

        Session(InputRequest request, Location location, BlockState original, long token) {
            this.request = request;
            this.location = location;
            this.original = original;
            this.token = token;
        }
    }

    private final InputContext ctx;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private long tokens;

    public SignInputProvider(InputContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public InputType type() {
        return InputType.SIGN;
    }

    @Override
    public boolean isAvailable(InputRequest request) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean open(Player player, InputRequest request) {
        cancel(player.getUniqueId());
        Block block = pickBlock(player);
        if (block == null) {
            return false;
        }
        BlockState original = block.getState();
        block.setType(Material.OAK_SIGN, false);
        if (!(block.getState() instanceof Sign)) {
            original.update(true, false);
            return false;
        }
        Sign sign = (Sign) block.getState();
        String initial = request.initial() == null ? "" : Text.stripSectionCodes(request.initial());
        sign.setLine(0, initial.length() > 15 ? initial.substring(0, 15) : initial);
        sign.setLine(1, initial.length() > 15 ? initial.substring(15, Math.min(30, initial.length())) : "");
        List<String> prompt = ctx.messages().get().getList("input.sign-lines." + request.purpose(), null);
        sign.setLine(2, prompt.size() > 0 ? prompt.get(0) : "");
        sign.setLine(3, prompt.size() > 1 ? prompt.get(1) : "");
        try {
            sign.setEditable(true);
        } catch (RuntimeException | LinkageError ignored) {
            // 1.20+: openSign() sets the allowed editor itself
        }
        sign.update(true, false);
        long token = ++tokens;
        Session session = new Session(request, block.getLocation(), original, token);
        sessions.put(player.getUniqueId(), session);

        ctx.scheduler().runAtEntityLater(player, () -> {
            if (sessions.get(player.getUniqueId()) != session) {
                return;
            }
            if (block.getState() instanceof Sign) {
                player.openSign((Sign) block.getState());
            } else {
                finish(player.getUniqueId(), session);
                ctx.scheduler().runAtEntity(player, session.request.onCancel());
            }
        }, 2L);
        long timeout = ctx.settings().get().inputTimeoutSeconds * 20L;
        ctx.scheduler().runAtEntityLater(player, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current != null && current.token == token && !current.done) {
                finish(player.getUniqueId(), current);
                ctx.messages().get().send(player, "input.timeout");
                current.request.onCancel().run();
            }
        }, timeout);
        return true;
    }

    /** The player's feet or head block when empty, else the block below their feet. */
    private static Block pickBlock(Player player) {
        Block feet = player.getLocation().getBlock();
        if (feet.getType().isAir()) {
            return feet;
        }
        Block head = feet.getRelative(0, 1, 0);
        if (head.getType().isAir()) {
            return head;
        }
        Block below = feet.getRelative(0, -1, 0);
        return below.getState() instanceof org.bukkit.inventory.InventoryHolder ? null : below;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.done || !event.getBlock().getLocation().equals(session.location)) {
            return;
        }
        event.setCancelled(true);
        String first = event.getLine(0) == null ? "" : event.getLine(0);
        String second = event.getLine(1) == null ? "" : event.getLine(1);
        String value = Text.stripSectionCodes((first + " " + second).trim()).trim();
        Player player = event.getPlayer();
        session.done = true;
        sessions.remove(player.getUniqueId(), session);
        // Restore one tick later, after the server finished handling the sign packet.
        ctx.scheduler().runAtEntityLater(player, () -> {
            session.original.update(true, false);
            if (value.isEmpty()) {
                session.request.onCancel().run();
            } else {
                session.request.onSubmit().accept(value);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        for (Session session : sessions.values()) {
            if (!session.done && event.getBlock().getLocation().equals(session.location)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void finish(UUID uuid, Session session) {
        session.done = true;
        sessions.remove(uuid, session);
        Runnable restore = () -> session.original.update(true, false);
        ctx.scheduler().runAtLocation(session.location, restore);
    }

    @Override
    public void cancel(UUID player) {
        Session session = sessions.get(player);
        if (session != null) {
            finish(player, session);
        }
    }

    @Override
    public void shutdown() {
        for (Session session : sessions.values()) {
            session.done = true;
            try {
                session.original.update(true, false);
            } catch (RuntimeException ignored) {
                // Folia: other region; the sign is harmless
            }
        }
        sessions.clear();
    }
}
