package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.CostSpec;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.scheduler.TaskHandle;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.util.Permissions;
import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Teleports players to their waypoints. Normal and death waypoints have their own permission
 * (wp.teleport / wp.teleport.death), switch and price; admins (wp.admin.tp) teleport instantly for free.
 * <p>
 * The optional countdown checks the player once per second (no move event listener): MOVEMENT cancels
 * when they moved away from where they started, DAMAGE cancels on any damage.
 */
public final class TeleportService implements Listener {

    private static final double MOVE_TOLERANCE_SQUARED = 0.5D * 0.5D;

    private static final class Pending {
        final Waypoint waypoint;
        final OwnerData owner;
        final CostSpec cost;
        final Location start;
        int secondsLeft;
        TaskHandle task;

        Pending(Waypoint waypoint, OwnerData owner, CostSpec cost, Location start, int secondsLeft) {
            this.waypoint = waypoint;
            this.owner = owner;
            this.cost = cost;
            this.start = start;
            this.secondsLeft = secondsLeft;
        }
    }

    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final WaypointService waypoints;
    private final CostService costs;
    private final PlatformScheduler scheduler;
    private final Feedback feedback;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public TeleportService(Supplier<Settings> settings, Supplier<Messages> messages, WaypointService waypoints,
                           CostService costs, PlatformScheduler scheduler, Feedback feedback) {
        this.settings = settings;
        this.messages = messages;
        this.waypoints = waypoints;
        this.costs = costs;
        this.scheduler = scheduler;
        this.feedback = feedback;
    }

    /** Whether this player may teleport to this waypoint at all (switches and permissions). */
    public boolean canTeleport(Player player, Waypoint waypoint, boolean admin) {
        if (admin) {
            return player.hasPermission(Permissions.ADMIN_TP);
        }
        Settings s = settings.get();
        return waypoint.isDeath()
                ? s.deathTeleportEnabled && player.hasPermission(Permissions.TELEPORT_DEATH)
                : s.teleportEnabled && player.hasPermission(Permissions.TELEPORT);
    }

    /** Price of teleporting to this waypoint. */
    public CostSpec costOf(Waypoint waypoint) {
        Settings s = settings.get();
        return waypoint.isDeath() ? s.deathTeleportCost : s.teleportCost;
    }

    /**
     * Teleports now, or starts the countdown.
     *
     * @param admin admin teleport: wp.admin.tp, no price, no countdown
     */
    public ActionResult teleport(Player player, OwnerData owner, String input, boolean admin) {
        Optional<Waypoint> found = waypoints.resolve(owner, input);
        if (!found.isPresent()) {
            return waypoints.notFound(input);
        }
        Waypoint waypoint = found.get();
        Settings s = settings.get();
        if (!admin) {
            boolean enabled = waypoint.isDeath() ? s.deathTeleportEnabled : s.teleportEnabled;
            if (!enabled) {
                return ActionResult.fail("messages.teleport-disabled", waypoints.placeholders(waypoint));
            }
        }
        if (!canTeleport(player, waypoint, admin)) {
            return ActionResult.fail("messages.no-permission");
        }
        if (waypoint.toLocation() == null) {
            return ActionResult.fail("messages.world-not-loaded", waypoints.placeholders(waypoint));
        }
        CostSpec cost = admin ? CostSpec.FREE : costOf(waypoint);
        Map<String, String> ph = Text.merge(waypoints.placeholders(waypoint), costs.placeholders(cost));
        if (costs.isCharged(player, cost) && !costs.canAfford(player, cost)) {
            return ActionResult.fail("messages.cost.not-enough", ph);
        }
        if (admin || !s.countdownEnabled) {
            return finish(player, waypoint, cost);
        }
        cancel(player.getUniqueId(), null);
        Pending request = new Pending(waypoint, owner, cost, player.getLocation(), s.countdownSeconds);
        pending.put(player.getUniqueId(), request);
        request.task = scheduler.runAtEntityTimer(player, () -> tick(player, request), 20L, 20L);
        ph.put("seconds", String.valueOf(s.countdownSeconds));
        return ActionResult.ok("teleport.countdown-start." + s.countdownCancel.name().toLowerCase(java.util.Locale.ROOT),
                ph, SoundService.TELEPORT_COUNTDOWN, waypoint);
    }

    private void tick(Player player, Pending request) {
        if (pending.get(player.getUniqueId()) != request) {
            if (request.task != null) {
                request.task.cancel();
            }
            return;
        }
        if (!player.isOnline()) {
            cancel(player.getUniqueId(), null);
            return;
        }
        Settings s = settings.get();
        if (s.countdownCancel == Settings.CountdownCancel.MOVEMENT) {
            Location now = player.getLocation();
            if (now.getWorld() != request.start.getWorld() || now.distanceSquared(request.start) > MOVE_TOLERANCE_SQUARED) {
                cancel(player.getUniqueId(), "teleport.cancelled-movement");
                return;
            }
        }
        request.secondsLeft--;
        if (request.secondsLeft <= 0) {
            pending.remove(player.getUniqueId(), request);
            request.task.cancel();
            feedback.send(player, finish(player, request.waypoint, request.cost));
            return;
        }
        int left = request.secondsLeft;
        // Every 10 seconds, then every 5 from 10, then every second from 5.
        if (left % 10 == 0 || (left <= 10 && left % 5 == 0) || left <= 5) {
            messages.get().send(player, "teleport.countdown",
                    Text.merge(waypoints.placeholders(request.waypoint), Text.ph("seconds", left)));
            feedback.sound(player, SoundService.TELEPORT_COUNTDOWN);
        }
    }

    private ActionResult finish(Player player, Waypoint waypoint, CostSpec cost) {
        Location location = waypoint.toLocation();
        Map<String, String> ph = Text.merge(waypoints.placeholders(waypoint), costs.placeholders(cost));
        if (location == null) {
            return ActionResult.fail("messages.world-not-loaded", ph);
        }
        boolean pay = costs.isCharged(player, cost);
        if (pay && !costs.charge(player, cost)) {
            return ActionResult.fail("messages.cost.not-enough", ph);
        }
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        scheduler.teleport(player, location);
        return ActionResult.ok(pay ? "messages.teleported-paid" : "messages.teleported", ph, SoundService.TELEPORT, waypoint);
    }

    /** Cancels a running countdown; sends the message when given. */
    public void cancel(UUID player, String messageKey) {
        Pending request = pending.remove(player);
        if (request == null) {
            return;
        }
        if (request.task != null) {
            request.task.cancel();
        }
        if (messageKey != null) {
            Player online = org.bukkit.Bukkit.getPlayer(player);
            if (online != null) {
                feedback.error(online, messageKey, waypoints.placeholders(request.waypoint));
            }
        }
    }

    public boolean isCountingDown(UUID player) {
        return pending.containsKey(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || pending.isEmpty()) {
            return;
        }
        if (settings.get().countdownCancel == Settings.CountdownCancel.DAMAGE) {
            cancel(event.getEntity().getUniqueId(), "teleport.cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), null);
    }

    public void shutdown() {
        for (UUID uuid : java.util.List.copyOf(pending.keySet())) {
            cancel(uuid, null);
        }
    }
}
