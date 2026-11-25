package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.config.Messages;
import io.github.obedsource.advancedwaypoints.config.SoundService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** Turns an {@link ActionResult} into a chat message and a sound. Shared by commands and menus. */
public final class Feedback {

    private final Supplier<Messages> messages;
    private final Supplier<SoundService> sounds;

    public Feedback(Supplier<Messages> messages, Supplier<SoundService> sounds) {
        this.messages = messages;
        this.sounds = sounds;
    }

    public void send(CommandSender to, ActionResult result) {
        messages.get().send(to, result.messageKey(), result.placeholders());
        if (to instanceof Player) {
            sounds.get().play((Player) to, result.success() && result.sound() != null ? result.sound() : SoundService.ERROR);
        }
    }

    public void noPermission(CommandSender to) {
        messages.get().send(to, "messages.no-permission");
        if (to instanceof Player) {
            sounds.get().play((Player) to, SoundService.NO_PERMISSION);
        }
    }

    public void error(CommandSender to, String key, java.util.Map<String, String> placeholders) {
        messages.get().send(to, key, placeholders);
        if (to instanceof Player) {
            sounds.get().play((Player) to, SoundService.ERROR);
        }
    }

    public void sound(CommandSender to, String sound) {
        if (to instanceof Player) {
            sounds.get().play((Player) to, sound);
        }
    }
}
