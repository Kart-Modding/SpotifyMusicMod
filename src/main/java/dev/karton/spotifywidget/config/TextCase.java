package dev.karton.spotifywidget.config;

/** Letter case for song and artist names. */
public enum TextCase implements Choice {
    NONE("none", "As released"),
    UPPER("upper", "UPPERCASE"),
    LOWER("lower", "lowercase"),
    CAPITALIZE("capitalize", "Capitalise");

    private final String id;
    private final String label;

    TextCase(String id, String label) {
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
