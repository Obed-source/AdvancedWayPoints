package io.github.obedsource.advancedwaypoints.session;

import io.github.obedsource.advancedwaypoints.display.DisplayManager;
import io.github.obedsource.advancedwaypoints.input.InputService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads player data while they log in, shows their labels on join, frees memory on quit. */
public final class PlayerListener implements Listener {

    private final SessionManager sessions;
    private final DisplayManager displays;
    private final InputService input;
    private final PlatformScheduler scheduler;
    private final Logger logger;
    private final Consumer<Player> onJoined;

    public PlayerListener(SessionManager sessions, DisplayManager displays, InputService input,
                          PlatformScheduler scheduler, Logger logger, Consumer<Player> onJoined) {
        this.sessions = sessions;
        this.displays = displays;
        this.input = input;
        this.scheduler = scheduler;
        this.logger = logger;
        this.onJoined = onJoined;
    }

    /** Runs on a background thread: reading the database here never touches the TPS. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            sessions.preload(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        load(player);
        onJoined.accept(player);
    }

    /** Loads a player (also used for players already online when the plugin enables). */
    public void load(Player player) {
        sessions.attach(player).whenComplete((data, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not load waypoints of " + player.getName(), error);
                return;
            }
            if (data.hasVisible()) {
                displays.refresh(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        input.cancel(player.getUniqueId());
        displays.stop(player.getUniqueId());
        sessions.detach(player.getUniqueId());
    }

    /** Labels of the new world appear right away (labels of other worlds stay selected). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        scheduler.runAtEntityLater(player, () -> displays.refresh(player.getUniqueId()), 2L);
    }
}
