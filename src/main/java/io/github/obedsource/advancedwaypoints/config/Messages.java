package io.github.obedsource.advancedwaypoints.config;

import io.github.obedsource.advancedwaypoints.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Texts of the active language file (messages, help, menus and color names).
 * Missing keys fall back to the bundled file of the same language, then to English.
 */
public final class Messages {

    private final YamlConfiguration lang;
    private final String prefix;
    private final Map<String, String> globals;

    public Messages(YamlConfiguration lang, String prefix, Map<String, String> globals) {
        this.lang = lang;
        this.prefix = prefix == null ? "" : prefix;
        this.globals = globals;
    }

    public String prefix() {
        return prefix;
    }

    public boolean has(String path) {
        return lang.contains(path);
    }

    /** Raw (uncolored) text; a list is joined with new lines. Missing keys return the path itself. */
    public String raw(String path) {
        if (lang.isList(path)) {
            return String.join("\n", lang.getStringList(path));
        }
        String value = lang.getString(path);
        return value == null ? path : value;
    }

    public List<String> rawList(String path) {
        if (lang.isList(path)) {
            return lang.getStringList(path);
        }
        String value = lang.getString(path);
        if (value == null) {
            return lang.contains(path) ? Collections.emptyList() : Collections.singletonList(path);
        }
        return Arrays.asList(value.split("\n"));
    }

    private Map<String, String> withGlobals(Map<String, String> placeholders) {
        return placeholders == null || placeholders.isEmpty() ? globals : Text.merge(globals, placeholders);
    }

    /** Colored text with placeholders applied. */
    public String get(String path, Map<String, String> placeholders) {
        return Text.color(Text.replace(raw(path), withGlobals(placeholders)));
    }

    public String get(String path) {
        return get(path, null);
    }

    /** Colored lines with placeholders applied. */
    public List<String> getList(String path, Map<String, String> placeholders) {
        Map<String, String> ph = withGlobals(placeholders);
        List<String> lines = new ArrayList<>();
        for (String line : rawList(path)) {
            lines.add(Text.color(Text.replace(line, ph)));
        }
        return lines;
    }

    /** Applies globals and placeholders to any template string (colored). */
    public String format(String template, Map<String, String> placeholders) {
        return Text.color(Text.replace(template, withGlobals(placeholders)));
    }

    /** Sends a message with the prefix. Empty messages are not sent. */
    public void send(CommandSender to, String path, Map<String, String> placeholders) {
        if (to == null) {
            return;
        }
        Map<String, String> ph = withGlobals(placeholders);
        for (String line : rawList(path)) {
            if (line.isEmpty()) {
                continue;
            }
            to.sendMessage(Text.color(prefix + Text.replace(line, ph)));
        }
    }

    public void send(CommandSender to, String path) {
        send(to, path, null);
    }

    /** Sends a message without the prefix. */
    public void sendPlain(CommandSender to, String path, Map<String, String> placeholders) {
        if (to == null) {
            return;
        }
        Map<String, String> ph = withGlobals(placeholders);
        for (String line : rawList(path)) {
            to.sendMessage(Text.color(Text.replace(line, ph)));
        }
    }
}
