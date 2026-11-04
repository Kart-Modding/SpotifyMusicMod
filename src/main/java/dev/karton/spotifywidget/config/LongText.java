package dev.karton.spotifywidget.config;

/** What happens to a name that does not fit. */
public enum LongText implements Choice {
    SCROLL("scroll", "Scroll"),
    ELLIPSIS("ellipsis", "Cut with ...");

    private final String id;
    private final String label;

    LongText(String id, String label) {
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
