package io.github.obedsource.advancedwaypoints.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Builds clickable chat lines (works on Spigot, Paper, Purpur and Folia). */
public final class Chat {

    private Chat() {
    }

    /**
     * @param legacy     the colored text ('&amp;' codes allowed)
     * @param hoverLines tooltip lines, may be null
     * @param action     click action, may be null
     * @param value      click value
     * @param insertion  text inserted into the chat box on shift-click, may be null
     */
    public static TextComponent line(String legacy, List<String> hoverLines, ClickEvent.Action action, String value, String insertion) {
        TextComponent component = new TextComponent(Text.components(legacy));
        if (hoverLines != null && !hoverLines.isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(Text.components(String.join("\n", hoverLines)))));
        }
        if (action != null && value != null) {
            component.setClickEvent(new ClickEvent(action, value));
        }
        if (insertion != null) {
            component.setInsertion(insertion);
        }
        return component;
    }

    /** Sends components to a player, or plain text to the console. */
    public static void send(CommandSender to, BaseComponent... components) {
        if (to instanceof Player) {
            ((Player) to).spigot().sendMessage(components);
        } else {
            to.sendMessage(new TextComponent(components).toLegacyText());
        }
    }
}
