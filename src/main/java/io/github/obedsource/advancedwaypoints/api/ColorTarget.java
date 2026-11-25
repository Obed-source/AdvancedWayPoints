package io.github.obedsource.advancedwaypoints.api;

import java.util.Locale;
import java.util.Optional;

/** The three colorable parts of a waypoint label. */
public enum ColorTarget {
    TEXT("text"),
    BACKGROUND("background"),
    ICON("icon");

    private final String key;

    ColorTarget(String key) {
        this.key = key;
    }

    /** Lower case key used in permissions, commands and language files. */
    public String key() {
        return key;
    }

    public static Optional<ColorTarget> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String value = input.toLowerCase(Locale.ROOT);
        for (ColorTarget target : values()) {
            if (target.key.equals(value)) {
                return Optional.of(target);
            }
        }
        switch (value) {
            case "texto":
                return Optional.of(TEXT);
            case "fondo":
            case "bg":
                return Optional.of(BACKGROUND);
            case "icono":
                return Optional.of(ICON);
            default:
                return Optional.empty();
        }
    }
}
