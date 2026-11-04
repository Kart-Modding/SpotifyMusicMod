package dev.karton.spotifywidget.config;

/** Shape of the widget. */
public enum WidgetLayout implements Choice {
    /** Album art, title, artist, elapsed/total and an equaliser. */
    CLASSIC("classic"),
    /** Album art, title, artist and a progress bar across the bottom. */
    BAR("bar"),
    /** One slim line: small album art, title, artist and a thin progress line. */
    COMPACT("compact"),
    /** No panel: a centred line of text above the hotbar, where item names appear. */
    HOTBAR("hotbar"),
    /** Drawn like a vanilla tooltip: dark box, purple gradient border, vanilla text colours. */
    VANILLA("vanilla"),
    /** Wide cover banner above the title, artist and progress bar. */
    CARD_LARGE("card_large"),
    /** Cover, one line of text and a progress bar underneath. */
    CARD_COMPACT("card_compact"),
    /** A slim pill: play state, one line of text and a small progress bar. */
    CARD_THIN("card_thin");

    private final String id;

    WidgetLayout(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return switch (this) {
            case CLASSIC -> "Classic";
            case BAR -> "Progress bar";
            case COMPACT -> "Compact";
            case HOTBAR -> "Above hotbar";
            case VANILLA -> "Vanilla tooltip";
            case CARD_LARGE -> "Card large";
            case CARD_COMPACT -> "Card compact";
            case CARD_THIN -> "Card thin";
        };
    }

    public boolean card() {
        return this == CARD_LARGE || this == CARD_COMPACT || this == CARD_THIN;
    }

    /** Above hotbar and Vanilla tooltip keep their own colours. */
    public boolean themed() {
        return this != HOTBAR && this != VANILLA;
    }

    public WidgetLayout next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static WidgetLayout byId(String id, WidgetLayout fallback) {
        for (WidgetLayout layout : values()) {
            if (layout.id.equals(id)) return layout;
        }
        return fallback;
    }
}
