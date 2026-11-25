package io.github.obedsource.advancedwaypoints.input;

import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import io.github.obedsource.advancedwaypoints.scheduler.PlatformScheduler;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** What every input provider needs. */
public record InputContext(Plugin plugin, PlatformScheduler scheduler, Supplier<Settings> settings,
                           Supplier<Messages> messages, Supplier<SoundService> sounds) {
}
