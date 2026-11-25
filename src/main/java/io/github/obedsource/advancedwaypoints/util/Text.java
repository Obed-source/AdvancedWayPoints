package io.github.obedsource.advancedwaypoints.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy text helpers: '&' color codes, '&#RRGGBB' hex colors and {placeholder} replacement.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern FORMAT_CODES = Pattern.compile("&#[A-Fa-f0-9]{6}|&[0-9a-fk-orA-FK-OR]");
    private static final Pattern COLOR_ONLY = Pattern.compile("^(&#[A-Fa-f0-9]{6}|&[0-9a-fk-orA-FK-OR])+$");

    private Text() {
    }

    /** Translates '&' codes and '&#RRGGBB' hex codes into section-sign codes. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    /** Removes every color/format code, both '&' style and section-sign style. */
    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(color(input));
        return stripped == null ? "" : stripped;
    }

    /**
     * Removes section-sign codes only, keeping '&amp;' codes typed by the player
     * (whether they may use them is decided later by permission).
     */
    public static String stripSectionCodes(String input) {
        return input == null ? "" : input.replaceAll("§[0-9a-fk-orxA-FK-ORX]?", "");
    }

    /** Removes '&' format codes only (keeps every other character). */
    public static String stripAmpersandCodes(String input) {
        return input == null ? "" : FORMAT_CODES.matcher(input).replaceAll("");
    }

    /** True when the value is one or more '&x' / '&#RRGGBB' codes and nothing else. */
    public static boolean isColorCode(String input) {
        return input != null && COLOR_ONLY.matcher(input).matches();
    }

    public static BaseComponent[] components(String legacy) {
        return TextComponent.fromLegacyText(color(legacy));
    }

    /** Replaces every {key} with its value. */
    public static String replace(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        if (placeholders == null || placeholders.isEmpty() || input.indexOf('{') < 0) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    /** Builds a placeholder map from alternating key/value pairs. */
    public static Map<String, String> ph(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    /** Merges two placeholder maps (the second one wins). */
    public static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> map = new LinkedHashMap<>(first);
        map.putAll(second);
        return map;
    }
}
