package io.github.obedsource.advancedwaypoints.command;

import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.menu.MenuService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import io.github.obedsource.advancedwaypoints.service.Feedback;
import io.github.obedsource.advancedwaypoints.service.WaypointService;
import io.github.obedsource.advancedwaypoints.session.SessionManager;

import java.util.function.Supplier;

/** Everything the commands need, injected once. */
public record CommandServices(
        PlatformScheduler scheduler,
        Supplier<Settings> settings,
        Supplier<Messages> messages,
        Supplier<SoundService> sounds,
        SessionManager sessions,
        WaypointService waypoints,
        MenuService menus,
        Feedback feedback,
        ChatPages pages,
        Runnable restart) {

    public Settings config() {
        return settings.get();
    }

    public Messages lang() {
        return messages.get();
    }

    /** The current main command name, e.g. "waypoints". */
    public String command() {
        return settings.get().commandName;
    }
}
