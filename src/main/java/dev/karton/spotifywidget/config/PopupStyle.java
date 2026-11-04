package dev.karton.spotifywidget.config;

/** How the widget appears and leaves. */
public enum PopupStyle implements Choice {
    SLIDE("slide", "Slide"),
    RISE("rise", "Rise"),
    SCALE("scale", "Scale"),
    FADE("fade", "Fade");

    private final String id;
    private final String label;

    PopupStyle(String id, String label) {
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
