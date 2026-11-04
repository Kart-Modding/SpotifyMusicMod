package dev.karton.spotifywidget.config;

/** Where the widget colours come from. */
public enum ThemeMode implements Choice {
    DEFAULT("default", "Default"),
    ALBUM("album", "From album"),
    CUSTOM("custom", "Custom");

    private final String id;
    private final String label;

    ThemeMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }
}
