package io.github.obedsource.advancedwaypoints.service;

import io.github.obedsource.advancedwaypoints.config.Settings;
import io.github.obedsource.advancedwaypoints.util.Text;

import java.util.function.Supplier;

/** Validates and normalizes waypoint names. */
public final class NameValidator {

    /** Normalized name, or the language key of the error. */
    public static final class Result {
        private final String name;
        private final String errorKey;

        private Result(String name, String errorKey) {
            this.name = name;
            this.errorKey = errorKey;
        }

        public boolean ok() {
            return errorKey == null;
        }

        public String name() {
            return name;
        }

        public String errorKey() {
            return errorKey;
        }
    }

    private final Supplier<Settings> settings;

    public NameValidator(Supplier<Settings> settings) {
        this.settings = settings;
    }

    /**
     * @param allowFormat when true, '&amp;' color codes are kept and not counted in the length
     */
    public Result validate(String input, boolean allowFormat) {
        Settings s = settings.get();
        String name = input == null ? "" : input.trim().replaceAll("\\s+", " ");
        String plain = allowFormat ? Text.stripAmpersandCodes(name).trim() : name;
        if (plain.isEmpty()) {
            return new Result(null, "messages.name-empty");
        }
        if (plain.length() > s.nameMaxLength) {
            return new Result(null, "messages.name-too-long");
        }
        if (!s.nameCharacters.matcher(plain).matches()) {
            return new Result(null, "messages.name-invalid");
        }
        return new Result(name, null);
    }
}
