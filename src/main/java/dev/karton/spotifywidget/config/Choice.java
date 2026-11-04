package dev.karton.spotifywidget.config;

/** An option with a stable id for the config file and a label for the settings screen. */
public interface Choice {
    String id();

    String label();

    static <T extends Choice> T byId(T[] values, String id, T fallback) {
        for (T value : values) {
            if (value.id().equals(id)) return value;
        }
        return fallback;
    }
}
