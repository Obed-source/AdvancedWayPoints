package io.github.obedsource.advancedwaypoints.session;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.scheduler.TaskHandle;
import io.github.obedsource.advancedwaypoints.service.ActionResult;
import io.github.obedsource.advancedwaypoints.service.Feedback;
import io.github.obedsource.advancedwaypoints.service.WaypointFormat;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.function.Supplier;

/** Creates a waypoint where a player dies, and removes death waypoints when their time runs out. */
public final class DeathWaypoints implements Listener {

    private final SessionManager sessions;
    private final WaypointService waypoints;
    private final Feedback feedback;
    private final Supplier<Messages> messages;
    private final Supplier<Settings> settings;
    private TaskHandle expiryTask;

    public DeathWaypoints(SessionManager sessions, WaypointService waypoints, Feedback feedback,
                          Supplier<Messages> messages, Supplier<Settings> settings) {
        this.sessions = sessions;
        this.waypoints = waypoints;
        this.feedback = feedback;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        OwnerData data = sessions.getOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        Location location = player.getLocation();
        ActionResult result = waypoints.createDeath(player, data, location);
        if (result != null) {
            feedback.send(player, result);
        }
    }

    /** Checks every second for expired death waypoints of online players. */
    public void start(PlatformScheduler scheduler) {
        stop();
        expiryTask = scheduler.runGlobalTimer(this::expire, 20L, 20L);
    }

    public void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private void expire() {
        for (OwnerData data : sessions.onlineData()) {
            List<Waypoint> removed = waypoints.removeExpired(data);
            if (removed.isEmpty()) {
                continue;
            }
            Player player = Bukkit.getPlayer(data.owner());
            if (player != null) {
                for (Waypoint waypoint : removed) {
                    messages.get().send(player, "messages.death-expired", WaypointFormat.placeholders(waypoint, settings.get()));
                }
            }
        }
    }
}
