package dev.karton.spotifywidget.hud;

import dev.karton.spotifywidget.config.HudConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** The six colours a themed design is drawn with. All values are 0xRRGGBB. */
public final class Theme {
    /** The standard look: dark panel, white text, green progress. */
    public static final Theme DEFAULT = new Theme(0x14141A, 0xFFFFFF, 0xB3B3B3, 0x1DB954, 0x1DB954, 0xDCDCDC);

    /** Presets for the "Start from" option, in display order. */
    public static final Map<String, Theme> PALETTES = new LinkedHashMap<>();

    static {
        PALETTES.put("Lilac", new Theme(0x262048, 0xE6BDFF, 0xD9D6EE, 0x8F7BFF, 0xFFB8F0, 0xFFB8F0));
        PALETTES.put("Mint", new Theme(0x1C3030, 0x8FF0C0, 0xC8E0D6, 0x3FB98A, 0xA0FFD0, 0x8FF0C0));
        PALETTES.put("Ember", new Theme(0x35151A, 0xFF9A7A, 0xECD0C8, 0xD02F4A, 0xFF9A5A, 0xFF6A5A));
        PALETTES.put("Mono", new Theme(0x141417, 0xFFFFFF, 0xA8A8B0, 0xD8D8DC, 0xFFFFFF, 0xFFFFFF));
    }

    public final int panel;
    public final int title;
    public final int artist;
    public final int barStart;
    public final int barEnd;
    public final int details;

    public Theme(int panel, int title, int artist, int barStart, int barEnd, int details) {
        this.panel = panel;
        this.title = title;
        this.artist = artist;
        this.barStart = barStart;
        this.barEnd = barEnd;
        this.details = details;
    }

    public static Theme custom(HudConfig config) {
        return new Theme(config.colorPanel, config.colorTitle, config.colorArtist,
                config.colorBarStart, config.colorBarEnd, config.colorDetails);
    }

    public Theme towards(Theme other, float amount) {
        return new Theme(mix(panel, other.panel, amount), mix(title, other.title, amount),
                mix(artist, other.artist, amount), mix(barStart, other.barStart, amount),
                mix(barEnd, other.barEnd, amount), mix(details, other.details, amount));
    }

    /** Picks the strongest hue of an image and builds a readable theme around it. */
    public static Theme fromPixels(int[] argb) {
        float[] weights = new float[24];
        float[] saturations = new float[24];
        for (int pixel : argb) {
            if ((pixel >>> 24) < 128) continue;
            float r = ((pixel >> 16) & 0xFF) / 255f;
            float g = ((pixel >> 8) & 0xFF) / 255f;
            float b = (pixel & 0xFF) / 255f;
            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            float delta = max - min;
            if (delta < 0.06f) continue;
            float lightness = (max + min) / 2f;
            float spread = 1f - Math.abs(2f * lightness - 1f);
            float saturation = delta / (spread + 0.000001f);
            float hue;
            if (max == r) hue = ((g - b) / delta + 6f) % 6f;
            else if (max == g) hue = (b - r) / delta + 2f;
            else hue = (r - g) / delta + 4f;
            int bucket = (int) (hue * 60f / 15f) % 24;
            float weight = saturation * spread;
            weights[bucket] += weight;
            saturations[bucket] += saturation * weight;
        }
        int best = 0;
        for (int i = 1; i < 24; i++) {
            if (weights[i] > weights[best]) best = i;
        }
        if (weights[best] <= 0) return DEFAULT;
        float hue = best * 15f + 7.5f;
        float strength = Math.max(0.25f, Math.min(0.85f, saturations[best] / weights[best]));
        return new Theme(hsl(hue, strength * 0.42f, 0.13f), hsl(hue, 0.8f, 0.77f), hsl(hue, 0.22f, 0.86f),
                hsl(hue, 0.75f, 0.5f), hsl((hue + 18f) % 360f, 0.85f, 0.7f), hsl(hue, 0.7f, 0.72f));
    }

    public static int mix(int from, int to, float amount) {
        int color = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            color |= (Math.round(a + (b - a) * amount) & 0xFF) << shift;
        }
        return color;
    }

    private static int hsl(float hue, float saturation, float lightness) {
        int r = channel(0, hue, saturation, lightness);
        int g = channel(8, hue, saturation, lightness);
        int b = channel(4, hue, saturation, lightness);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int n, float hue, float saturation, float lightness) {
        float k = (n + hue / 30f) % 12f;
        float amount = saturation * Math.min(lightness, 1f - lightness);
        float value = lightness - amount * Math.max(-1f, Math.min(k - 3f, Math.min(9f - k, 1f)));
        return Math.round(value * 255f);
    }
}
