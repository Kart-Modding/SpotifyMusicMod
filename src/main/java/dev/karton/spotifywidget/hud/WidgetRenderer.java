package dev.karton.spotifywidget.hud;

import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.config.DisplayMode;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.config.HudCorner;
import dev.karton.spotifywidget.config.LongText;
import dev.karton.spotifywidget.config.PopupStyle;
import dev.karton.spotifywidget.config.TextCase;
import dev.karton.spotifywidget.config.ThemeMode;
import dev.karton.spotifywidget.game.JukeboxWatcher;
import dev.karton.spotifywidget.media.Media;
import dev.karton.spotifywidget.media.Track;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Draws the now-playing widget in whichever layout the config asks for. */
public final class WidgetRenderer {
    private static final int PADDING = 5;
    private static final int COVER = 32;
    private static final int GAP = 7;
    private static final int LINE = 11;
    private static final int CORNER = 8;
    /** How far the "above hotbar" line and the vanilla item name are both pushed down. */
    public static final int HOTBAR_DROP = 8;

    private static final int BAR_COUNT = 5;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_GAP = 2;
    private static final int BARS_WIDTH = BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP;

    private static int colorPanel;
    private static int colorEdge;
    private static int colorTitle;
    private static int colorPaused;
    private static int colorArtist;
    private static int colorTime;
    private static int colorBars;
    private static int colorTrack;
    private static int colorProgressStart;
    private static int colorProgressEnd;
    private static int colorDot;

    // Vanilla tooltip palette, straight out of TooltipRenderUtil
    private static final int VANILLA_BACKGROUND = 0xF0100010;
    private static final int VANILLA_BORDER_TOP = 0x505000FF;
    private static final int VANILLA_BORDER_BOTTOM = 0x5028007F;
    private static final int VANILLA_WHITE = 0xFFFFFF;
    private static final int VANILLA_GRAY = 0xAAAAAA;
    private static final int VANILLA_DARK_GRAY = 0x555555;
    private static final int VANILLA_LINE = 10;
    private static final float PAUSED_ALPHA = 0.55f;
    private static final int TEXT_HEIGHT = 8;
    /** Smallest room left for text however narrow the width setting is. */
    private static final int MIN_TEXT_WIDTH = 40;

    /** Finished fraction of the pop-up animation, 0 hidden and 1 fully shown. */
    private static float shown;
    private static float dim = 1f;
    private static float popup = 1f;
    private static long lastFrameNanos;
    private static String lastTrackId;
    private static long trackChangedAt;
    private static long pausedSince;
    private static boolean hudHidden;
    private static Theme theme = Theme.DEFAULT;
    private static float replay = 1f;
    private static Track sample;

    private WidgetRenderer() {
    }

    /** The settings screens draw the widget themselves while they are open. */
    public static void setHudHidden(boolean hidden) {
        hudHidden = hidden;
    }

    public static void render(Canvas canvas) {
        if (hudHidden) return;
        HudConfig config = HudConfig.get();
        long now = System.nanoTime();
        float delta = frameDelta(now);

        Track track = currentTrack(config, now);
        boolean wanted = config.enabled && shouldShow(config, track, now);
        if (!config.animations) shown = wanted ? 1f : 0f;
        else {
            float step = delta / Math.max(0.05f, config.animationTime);
            shown = wanted ? Math.min(1f, shown + step) : Math.max(0f, shown - step);
        }
        float wantedDim = track != null && !track.playing && config.dimPaused ? PAUSED_ALPHA : 1f;
        dim = config.animations ? dim + (wantedDim - dim) * Math.min(1f, delta * 5f) : wantedDim;
        if (shown <= 0f || track == null) return;

        updateTheme(config, track, delta);
        popup = ease(shown);
        draw(canvas, config, track, config.popupStyle == PopupStyle.SLIDE ? dim : dim * popup, now);
    }

    private static float frameDelta(long now) {
        float delta = lastFrameNanos == 0 ? 0 : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
    }

    private static float ease(float value) {
        float rest = 1f - value;
        return 1f - rest * rest * rest;
    }

    private static Track currentTrack(HudConfig config, long now) {
        Track jukebox = config.jukeboxWidget ? JukeboxWatcher.track() : null;
        Track track = jukebox != null ? jukebox : Media.track();
        if (track != null && !track.id.equals(lastTrackId)) {
            lastTrackId = track.id;
            trackChangedAt = now;
        }
        if (track == null || track.playing) pausedSince = 0;
        else if (pausedSince == 0) pausedSince = now;
        return track;
    }

    private static void updateTheme(HudConfig config, Track track, float delta) {
        Theme target = theme;
        if (config.theme == ThemeMode.DEFAULT) target = Theme.DEFAULT;
        else if (config.theme == ThemeMode.CUSTOM) target = Theme.custom(config);
        else {
            Theme album = CoverArt.theme(track.artKey);
            if (album != null) target = album;
            else if (track.artKey == null) target = Theme.DEFAULT;
        }
        theme = config.animations ? theme.towards(target, Math.min(1f, delta * 5f)) : target;
    }

    private static void draw(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        applyColors(config.layout.themed() ? theme : Theme.DEFAULT, config.layout.themed());
        switch (config.layout) {
            case BAR -> drawBar(canvas, config, track, alpha, now);
            case COMPACT -> drawCompact(canvas, config, track, alpha, now);
            case HOTBAR -> drawHotbarLine(canvas, config, track, alpha, now);
            case VANILLA -> drawVanilla(canvas, config, track, alpha, now);
            case CARD_LARGE -> drawCardLarge(canvas, config, track, alpha, now);
            case CARD_COMPACT -> drawCardCompact(canvas, config, track, alpha, now);
            case CARD_THIN -> drawCardThin(canvas, config, track, alpha, now);
            default -> drawClassic(canvas, config, track, alpha, now);
        }
    }

    private static void applyColors(Theme colors, boolean themed) {
        colorPanel = colors.panel;
        colorTitle = colors.title;
        colorArtist = colors.artist;
        colorProgressStart = colors.barStart;
        colorProgressEnd = colors.barEnd;
        colorBars = colors.details;
        if (themed) {
            colorEdge = Theme.mix(colors.panel, 0xFFFFFF, 0.16f);
            colorPaused = Theme.mix(colors.title, colors.panel, 0.25f);
            colorTime = Theme.mix(colors.artist, colors.panel, 0.25f);
            colorTrack = Theme.mix(colors.panel, 0xFFFFFF, 0.14f);
            colorDot = Theme.mix(colors.details, colors.panel, 0.5f);
        } else {
            colorEdge = 0x33343A;
            colorPaused = 0xC6C6CC;
            colorTime = 0x8C8C8C;
            colorTrack = 0x3A3A44;
            colorDot = 0x63636F;
        }
    }

    // --- used by the settings screens ------------------------------------------

    /** The playing track, or a made-up one so the settings always have something to show. */
    private static Track previewTrack(HudConfig config, long now) {
        Track track = currentTrack(config, now);
        if (track != null) return track;
        if (sample == null || sample.remainingMs() <= 0) {
            sample = new Track("sample", "Midnight Drive", "Neon Harbor", null, 214000, 84000, true, now);
        }
        return sample;
    }

    /** Replays the pop-up animation in the settings preview. */
    public static void replay() {
        replay = HudConfig.get().animations ? 0f : 1f;
    }

    /** Where the widget sits on a screen of this size, in GUI pixels: x0, y0, x1, y1. */
    public static int[] box(int screenWidth, int screenHeight) {
        HudConfig config = HudConfig.get();
        long now = System.nanoTime();
        float previous = popup;
        popup = 1f;
        Canvas measure = Canvas.measure(screenWidth, screenHeight);
        draw(measure, config, previewTrack(config, now), 1f, now);
        popup = previous;
        return measure.bounds();
    }

    /** Draws the widget centred in an area of the settings screen, shrunk if it does not fit. */
    public static void preview(Canvas canvas, int x, int y, int width, int height) {
        HudConfig config = HudConfig.get();
        long now = System.nanoTime();
        float delta = frameDelta(now);
        Track track = previewTrack(config, now);
        updateTheme(config, track, delta);

        int[] box = box(canvas.screenWidth(), canvas.screenHeight());
        int boxWidth = box[2] - box[0];
        int boxHeight = box[3] - box[1];
        float fit = Math.min(1f, Math.min((width - 8f) / boxWidth, (height - 8f) / boxHeight));

        replay = Math.min(1f, replay + delta / Math.max(0.05f, config.animationTime));
        float progress = ease(replay);
        float alpha = 1f;
        if (!config.enabled || (config.hidePaused && !track.playing)) alpha = 0.3f;
        else if (config.dimPaused && !track.playing) alpha = PAUSED_ALPHA;
        if (config.popupStyle != PopupStyle.SLIDE) alpha *= progress;

        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        if (config.popupStyle == PopupStyle.SLIDE) centerX -= (1f - progress) * (x + width);
        else if (config.popupStyle == PopupStyle.RISE) centerY += (1f - progress) * 10f;
        else if (config.popupStyle == PopupStyle.SCALE) fit *= 0.8f + 0.2f * progress;

        popup = 1f;
        canvas.transform(centerX - (box[0] + boxWidth / 2f) * fit, centerY - (box[1] + boxHeight / 2f) * fit, fit);
        draw(canvas, config, track, alpha, now);
        canvas.pop();
    }

    /** Draws the widget exactly where it sits in game, for the move-on-screen editor. */
    public static void drawInPlace(Canvas canvas) {
        HudConfig config = HudConfig.get();
        long now = System.nanoTime();
        float delta = frameDelta(now);
        Track track = previewTrack(config, now);
        updateTheme(config, track, delta);
        popup = 1f;
        draw(canvas, config, track, 1f, now);
    }

    private static boolean shouldShow(HudConfig config, Track track, long now) {
        if (track == null) return false;
        if (config.hideWithGui && Canvas.guiOpen()) return false;
        if (config.hidePaused && !track.playing) return false;
        if (config.hideIdle && pausedSince != 0 && now - pausedSince > config.idleMinutes * 60_000_000_000L) return false;
        DisplayMode mode = config.displayMode;
        if (!mode.timed()) return true;

        long window = mode.seconds() * 1000L;
        if (config.showOnSongStart) {
            if (track.currentProgressMs() <= window) return true;
            // Also cover the case where playback jumps into a track that is already past its intro
            if ((now - trackChangedAt) / 1_000_000L <= window) return true;
        }
        if (config.showOnSongEnd && track.durationMs > 0 && track.remainingMs() <= window) return true;
        return false;
    }

    // --- layouts ---------------------------------------------------------------

    /** Album art, title, artist, elapsed/total and the equaliser. */
    private static void drawClassic(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        int budget = budget(config, PADDING * 2 + (config.showAlbumArt ? COVER + GAP : 0)
                + (config.showWaveform ? BARS_WIDTH + GAP : 0));
        String title = canvas.trim(text(track.title), budget);
        String artist = canvas.trim(text(track.artist), budget);
        String time = timeLabel(track);

        int textWidth = Math.max(canvas.textWidth(title), canvas.textWidth(artist));
        if (config.showTimestamps) textWidth = Math.max(textWidth, canvas.textWidth(time));
        textWidth = Math.min(textWidth, budget);

        int width = PADDING * 2 + textWidth;
        if (config.showAlbumArt) width += COVER + GAP;
        if (config.showWaveform) width += BARS_WIDTH + GAP;
        int height = PADDING * 2 + COVER;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, alpha);

        int contentX = x + PADDING;
        if (config.showAlbumArt) contentX = cover(canvas, track, contentX, y + PADDING, COVER, alpha);

        int textAlpha = Math.round(255 * alpha);
        int textY = y + PADDING + 1;
        canvas.text(title, contentX, textY, argb(colorTitle, textAlpha), true);
        canvas.text(artist, contentX, textY + LINE, argb(colorArtist, textAlpha), true);
        if (config.showTimestamps) {
            canvas.text(time, contentX, textY + LINE * 2, argb(colorTime, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, x + width - PADDING - BARS_WIDTH, y + height / 2, track, alpha, now);
        }
        canvas.pop();
    }

    /** Album art and two lines of text, with a progress bar along the bottom edge. */
    private static void drawBar(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        int budget = budget(config, PADDING * 2 + (config.showAlbumArt ? COVER + GAP : 0)
                + (config.showWaveform ? BARS_WIDTH + GAP : 0));
        String title = canvas.trim(text(track.title), budget);
        String time = timeLabel(track);
        int timeWidth = config.showTimestamps ? canvas.textWidth(time) + GAP : 0;
        String artist = canvas.trim(text(track.artist), Math.max(MIN_TEXT_WIDTH, budget - timeWidth));

        int secondLine = canvas.textWidth(artist) + timeWidth;
        int textWidth = Math.min(budget, Math.max(canvas.textWidth(title), Math.max(secondLine, 80)));

        int width = PADDING * 2 + textWidth;
        if (config.showAlbumArt) width += COVER + GAP;
        if (config.showWaveform) width += BARS_WIDTH + GAP;
        int height = PADDING * 2 + COVER;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, alpha);

        int contentX = x + PADDING;
        if (config.showAlbumArt) contentX = cover(canvas, track, contentX, y + PADDING, COVER, alpha);

        int textAlpha = Math.round(255 * alpha);
        canvas.text(title, contentX, y + PADDING + 2, argb(colorTitle, textAlpha), true);
        canvas.text(artist, contentX, y + PADDING + 2 + LINE, argb(colorArtist, textAlpha), true);
        if (config.showTimestamps) {
            int timeX = x + width - PADDING - canvas.textWidth(time);
            if (config.showWaveform) timeX -= BARS_WIDTH + GAP;
            canvas.text(time, timeX, y + PADDING + 2 + LINE, argb(colorTime, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, x + width - PADDING - BARS_WIDTH, y + PADDING + 8, track, alpha, now);
        }

        progress(canvas, contentX, y + height - PADDING - 4,
                x + width - PADDING - contentX, 4, track, alpha);
        canvas.pop();
    }

    /** One slim row: the whole cover, title, artist, and a progress bar along the bottom edge. */
    private static void drawCompact(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int side = 6;
        final int pad = 3;
        final int height = 26;
        final int barHeight = 2;
        final int radius = 6;
        final int art = height - barHeight - pad * 2;
        final String dot = "  ·  ";

        String title = text(track.title);
        String artist = text(track.artist);
        String remaining = "-" + Track.formatTime(track.remainingMs());
        String separator = artist.isEmpty() ? "" : dot;
        int timeWidth = config.showTimestamps ? 8 + canvas.textWidth(remaining) : 0;
        int chrome = side * 2 + timeWidth + (config.showAlbumArt ? pad + art : 0);

        int natural = canvas.textWidth(title) + canvas.textWidth(separator) + canvas.textWidth(artist);
        int textWidth = Math.min(natural, budget(config, chrome));
        int width = chrome + textWidth;

        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];

        panel(canvas, config, x, y, width, height, radius, alpha);

        int contentX = x + side;
        if (config.showAlbumArt) {
            Shapes.fill(canvas, x + pad + 1, y + pad + 1, art, art, 1, argb(0x000000, Math.round(140 * alpha)));
            artwork(canvas, track, x + pad, y + pad, art, alpha);
            contentX = x + pad + art + side;
        }

        int textAlpha = Math.round(255 * alpha);
        int textY = y + 8;
        int titleColor = track.playing ? colorTitle : colorPaused;
        line(canvas, config, new String[]{title, separator, artist}, new int[]{titleColor, colorDot, colorArtist},
                contentX, textY, textWidth, false, textAlpha, now);
        if (config.showTimestamps) {
            canvas.text(remaining, x + width - side - canvas.textWidth(remaining), textY,
                    argb(colorTime, textAlpha), true);
        }

        edgeBar(canvas, x, y, width, height, barHeight, radius, track, alpha);
        canvas.pop();
    }

    /** Progress bar along the whole bottom edge, cut to the widget shape so no gap is left. */
    private static void edgeBar(Canvas canvas, int x, int y, int width, int height, int barHeight, int radius,
                                Track track, float alpha) {
        int fullAlpha = Math.round(255 * alpha);
        int barY = y + height - barHeight;
        int track_ = argb(colorTrack, fullAlpha);
        Shapes.part(canvas, x, y, width, height, radius, x, barY, x + width, y + height, track_, track_, x, width);
        int filled = Math.round(width * fraction(track));
        Shapes.part(canvas, x, y, width, height, radius, x, barY, x + filled, y + height,
                argb(colorProgressStart, fullAlpha), argb(colorProgressEnd, fullAlpha), x, Math.max(1, filled));
    }

    // --- card designs ----------------------------------------------------------

    /** Wide cover on top, then title, artist and a progress row. */
    private static void drawCardLarge(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int band = 44;
        final int radius = 10;
        int width = Math.max(100, Math.min(132, config.maxWidth));
        int height = config.showAlbumArt ? 86 : 42;
        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];
        int fullAlpha = Math.round(255 * alpha);
        int panelAlpha = Math.round(255 * (config.backgroundOpacity / 100f) * alpha);

        Shapes.fill(canvas, x, y, width, height, radius, argb(colorPanel, panelAlpha));
        int bodyY = y;
        if (config.showAlbumArt) {
            bodyY += band;
            Identifier banner = CoverArt.banner(track.artKey);
            if (banner != null) {
                canvas.texture(banner, x, y, width, band + 2, CoverArt.BANNER_WIDTH, CoverArt.BANNER_HEIGHT, alpha);
            } else {
                int blank = argb(Theme.mix(colorPanel, colorProgressStart, 0.5f), fullAlpha);
                Shapes.part(canvas, x, y, width, height, radius, x, y, x + width, y + band + 2, blank, blank, x, width);
            }
            float fade = Math.max(config.backgroundOpacity / 100f, 0.6f);
            for (int i = 0; i < 12; i++) {
                int rowAlpha = Math.round(255 * alpha * fade * (float) Math.pow((i + 1) / 12f, 1.5));
                canvas.rect(x, y + band - 10 + i, width, 1, argb(colorPanel, rowAlpha));
            }
        }

        int textY = bodyY + 5;
        line(canvas, config, new String[]{text(track.title)}, new int[]{colorTitle},
                x + 10, textY, width - 20, true, fullAlpha, now);
        line(canvas, config, new String[]{text(track.artist)}, new int[]{colorArtist},
                x + 10, textY + 11, width - 20, true, fullAlpha, now);
        int barY = textY + 24;
        if (config.showTimestamps) {
            String elapsed = Track.formatTime(track.currentProgressMs());
            String total = Track.formatTime(track.durationMs);
            int left = canvas.textWidth(elapsed);
            int right = canvas.textWidth(total);
            canvas.text(elapsed, x + 8, barY - 3, argb(colorTitle, fullAlpha), true);
            canvas.text(total, x + width - 8 - right, barY - 3, argb(colorTitle, fullAlpha), true);
            progress(canvas, x + 12 + left, barY, width - 24 - left - right, 3, track, alpha);
        } else {
            progress(canvas, x + 10, barY, width - 20, 3, track, alpha);
        }
        canvas.pop();
    }

    /** Cover, one line of text, and a progress bar under the text. */
    private static void drawCardCompact(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int height = 30;
        final int radius = 8;
        final String dot = "  ·  ";
        String title = text(track.title);
        String artist = text(track.artist);
        String separator = artist.isEmpty() ? "" : dot;
        int chrome = 12 + (config.showAlbumArt ? 25 : 0) + 8;
        int natural = canvas.textWidth(title) + canvas.textWidth(separator) + canvas.textWidth(artist);
        int width = chrome + Math.max(100 - (config.showAlbumArt ? 0 : 25), Math.min(natural, budget(config, chrome)));
        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];
        int fullAlpha = Math.round(255 * alpha);

        Shapes.fill(canvas, x, y, width, height, radius,
                argb(colorPanel, Math.round(255 * (config.backgroundOpacity / 100f) * alpha)));
        grip(canvas, x + 6, y + 11, argb(colorBars, fullAlpha));
        int textX = x + 12;
        if (config.showAlbumArt) {
            artwork(canvas, track, textX, y + 5, 20, alpha);
            textX += 25;
        }
        int textWidth = x + width - 8 - textX;
        line(canvas, config, new String[]{title, separator, artist}, new int[]{colorTitle, colorBars, colorArtist},
                textX, y + 6, textWidth, false, fullAlpha, now);
        if (config.showTimestamps) {
            String elapsed = Track.formatTime(track.currentProgressMs());
            int timeWidth = canvas.textWidth(elapsed);
            canvas.text(elapsed, x + width - 8 - timeWidth, y + 17, argb(colorArtist, fullAlpha), true);
            progress(canvas, textX, y + 19, textWidth - timeWidth - 4, 4, track, alpha);
        } else {
            progress(canvas, textX, y + 19, textWidth, 4, track, alpha);
        }
        canvas.pop();
    }

    /** A slim pill: play state, one line of text and a small progress bar. */
    private static void drawCardThin(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int height = 14;
        final String dot = "  ·  ";
        String title = text(track.title);
        String artist = text(track.artist);
        String separator = artist.isEmpty() ? "" : dot;
        int chrome = 15 + 49;
        int natural = canvas.textWidth(title) + canvas.textWidth(separator) + canvas.textWidth(artist);
        int width = chrome + Math.max(94, Math.min(natural, budget(config, chrome)));
        int[] origin = begin(canvas, config, width, height, alpha);
        int x = origin[0];
        int y = origin[1];
        int fullAlpha = Math.round(255 * alpha);

        Shapes.fill(canvas, x, y, width, height, 7,
                argb(colorPanel, Math.round(255 * (config.backgroundOpacity / 100f) * alpha)));
        playState(canvas, x + 7, y + 4, track.playing, argb(colorTitle, fullAlpha));
        line(canvas, config, new String[]{title, separator, artist}, new int[]{colorTitle, colorBars, colorArtist},
                x + 15, y + 3, width - chrome, false, fullAlpha, now);
        grip(canvas, x + width - 45, y + 4, argb(colorBars, fullAlpha));
        progress(canvas, x + width - 40, y + 6, 32, 2, track, alpha);
        canvas.pop();
    }

    private static void grip(Canvas canvas, int x, int y, int color) {
        for (int i = 0; i < 3; i++) canvas.rect(x, y + i * 3, 1, 1, color);
    }

    private static void playState(Canvas canvas, int x, int y, boolean playing, int color) {
        if (!playing) {
            canvas.rect(x, y, 2, 7, color);
            canvas.rect(x + 3, y, 2, 7, color);
            return;
        }
        for (int i = 0; i < 4; i++) canvas.rect(x + i, y + i, 1, 7 - i * 2, color);
    }

    /** Coloured pieces of text on one line, scrolled or cut when they do not fit. */
    private static void line(Canvas canvas, HudConfig config, String[] pieces, int[] colors,
                             int x, int y, int maxWidth, boolean centered, int alpha, long now) {
        int total = 0;
        for (String piece : pieces) total += canvas.textWidth(piece);
        boolean fits = total <= maxWidth;
        boolean scroll = !fits && config.longText == LongText.SCROLL;
        int textX = centered && fits ? x + (maxWidth - total) / 2 : x;
        int left = maxWidth;

        if (scroll) {
            canvas.clip(x, y - 2, x + maxWidth, y + TEXT_HEIGHT + 2);
            float seconds = (now - trackChangedAt) / 1_000_000_000f;
            canvas.transform(-scrollOffset(total - maxWidth, seconds, config), 0f, 1f);
        }
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (!fits && !scroll) {
                if (left <= canvas.textWidth("...")) break;
                piece = canvas.trim(piece, left);
                left -= canvas.textWidth(piece);
            }
            canvas.text(piece, textX, y, argb(colors[i], alpha), true);
            textX += canvas.textWidth(piece);
            if (!fits && !scroll && piece.endsWith("...")) break;
        }
        if (scroll) {
            canvas.pop();
            canvas.endClip();
        }
    }

    private static float scrollOffset(int span, float seconds, HudConfig config) {
        float hold = config.scrollHold;
        float travel = span / (float) Math.max(1, config.scrollSpeed);
        float position = seconds % (2f * (travel + hold));
        if (position < hold) return 0f;
        position -= hold;
        if (position < travel) return span * position / travel;
        position -= travel;
        if (position < hold) return span;
        return span * (1f - (position - hold) / travel);
    }

    private static int budget(HudConfig config, int chrome) {
        return Math.max(MIN_TEXT_WIDTH, config.maxWidth - chrome);
    }

    /**
     * No panel at all: a centred line of text where vanilla shows the held item name, which sits
     * above the experience bar and status bars. Corner is ignored, the offset lifts it further up
     * and the hotbar shift moves it sideways.
     */
    private static void drawHotbarLine(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final String dot = "·";
        int budget = budget(config, (config.showTimestamps ? 8 + canvas.textWidth(timeLabel(track)) : 0)
                + (config.showWaveform ? BARS_WIDTH + 6 : 0) + 10);
        String title = canvas.trim(text(track.title), budget * 2 / 3);
        String artist = canvas.trim(text(track.artist), budget - canvas.textWidth(title));
        String time = timeLabel(track);

        int titleWidth = canvas.textWidth(title);
        int dotWidth = canvas.textWidth(dot);
        int artistWidth = artist.isEmpty() ? 0 : 5 + dotWidth + 5 + canvas.textWidth(artist);
        int timeWidth = config.showTimestamps ? 8 + canvas.textWidth(time) : 0;
        int width = titleWidth + artistWidth + timeWidth;
        int barsWidth = config.showWaveform ? BARS_WIDTH + 6 : 0;

        float scale = Math.max(0.5f, Math.min(2.0f, config.scale));
        canvas.push(scale);
        int screenWidth = Math.round(canvas.screenWidth() / scale);
        int screenHeight = Math.round(canvas.screenHeight() / scale);

        int x = (screenWidth - (width + barsWidth)) / 2 + config.hotbarShift;
        // Vanilla draws the held item name at guiHeight - 59, or 14 lower in creative and spectator
        // where the health and experience rows are gone. Sit one line above whichever it is, so the
        // two never print on top of each other when you switch items.
        int base = (Canvas.statusBarsVisible() ? 69 : 55) - HOTBAR_DROP;
        int y = screenHeight - base - config.offsetY + Math.round((1f - alpha) * 4f);

        int textAlpha = Math.round(255 * alpha);
        if (config.showWaveform) {
            equaliser(canvas, x, y + 4, track, alpha, now);
            x += barsWidth;
        }
        int titleColor = track.playing ? colorTitle : colorPaused;
        canvas.text(title, x, y, argb(titleColor, textAlpha), true);
        int after = x + titleWidth;
        if (!artist.isEmpty()) {
            canvas.text(dot, after + 5, y, argb(0x8A8A96, textAlpha), true);
            canvas.text(artist, after + 5 + dotWidth + 5, y, argb(colorArtist, textAlpha), true);
        }
        if (config.showTimestamps) {
            canvas.text(time, x + width - canvas.textWidth(time), y, argb(colorTime, textAlpha), true);
        }
        canvas.pop();
    }

    /**
     * Vanilla tooltip look: the same dark box, purple gradient border and text colours the game
     * uses for item tooltips, with the album art sitting where an item icon would.
     */
    private static void drawVanilla(Canvas canvas, HudConfig config, Track track, float alpha, long now) {
        final int icon = 16;
        int budget = budget(config, 8 + (config.showAlbumArt ? icon + 4 : 0) + (config.showWaveform ? BARS_WIDTH + 6 : 0));
        String title = canvas.trim(text(track.title), budget);
        String artist = canvas.trim(text(track.artist), budget);
        String time = timeLabel(track);

        int lines = 2 + (config.showTimestamps ? 1 : 0);
        int textWidth = Math.max(canvas.textWidth(title), canvas.textWidth(artist));
        if (config.showTimestamps) textWidth = Math.max(textWidth, canvas.textWidth(time));

        int contentWidth = textWidth;
        if (config.showAlbumArt) contentWidth += icon + 4;
        if (config.showWaveform) contentWidth += BARS_WIDTH + 6;
        int contentHeight = Math.max(lines * VANILLA_LINE - 2, config.showAlbumArt ? icon : 0);

        int width = contentWidth + 8;
        int height = contentHeight + 8;

        int[] origin = begin(canvas, config, width, height, alpha);
        int contentX = origin[0] + 4;
        int contentY = origin[1] + 4;

        tooltipFrame(canvas, contentX, contentY, contentWidth, contentHeight, alpha);

        int textX = contentX;
        if (config.showAlbumArt) {
            artwork(canvas, track, contentX, contentY, icon, alpha);
            textX += icon + 4;
        }

        int textAlpha = Math.round(255 * alpha);
        canvas.text(title, textX, contentY, argb(VANILLA_WHITE, textAlpha), true);
        canvas.text(artist, textX, contentY + VANILLA_LINE, argb(VANILLA_GRAY, textAlpha), true);
        if (config.showTimestamps) {
            canvas.text(time, textX, contentY + VANILLA_LINE * 2, argb(VANILLA_DARK_GRAY, textAlpha), true);
        }
        if (config.showWaveform) {
            equaliser(canvas, contentX + contentWidth - BARS_WIDTH, contentY + contentHeight / 2, track, alpha, now);
        }
        canvas.pop();
    }

    /** Same geometry as {@code TooltipRenderUtil}, drawn with flat rows so it works on every version. */
    private static void tooltipFrame(Canvas canvas, int x, int y, int width, int height, float alpha) {
        int background = scaleAlpha(VANILLA_BACKGROUND, alpha);
        canvas.rect(x - 3, y - 4, width + 6, 1, background);
        canvas.rect(x - 3, y + height + 3, width + 6, 1, background);
        canvas.rect(x - 3, y - 3, width + 6, height + 6, background);
        canvas.rect(x - 4, y - 3, 1, height + 6, background);
        canvas.rect(x + width + 3, y - 3, 1, height + 6, background);

        int top = scaleAlpha(VANILLA_BORDER_TOP, alpha);
        int bottom = scaleAlpha(VANILLA_BORDER_BOTTOM, alpha);
        int borderHeight = height + 4;
        for (int row = 0; row < borderHeight; row++) {
            int color = lerpColor(top, bottom, borderHeight <= 1 ? 0f : row / (float) (borderHeight - 1));
            canvas.rect(x - 3, y - 2 + row, 1, 1, color);
            canvas.rect(x + width + 2, y - 2 + row, 1, 1, color);
        }
        canvas.rect(x - 3, y - 3, width + 6, 1, top);
        canvas.rect(x - 3, y + height + 2, width + 6, 1, bottom);
    }

    private static int scaleAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        int color = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            color |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return color;
    }

    // --- shared pieces ---------------------------------------------------------

    /** Works out the anchored position, applies the scale and returns the panel origin. */
    private static int[] begin(Canvas canvas, HudConfig config, int width, int height, float alpha) {
        float scale = Math.max(0.5f, Math.min(2.0f, config.scale));
        HudCorner corner = config.corner;
        int screenWidth = Math.round(canvas.screenWidth() / scale);
        int screenHeight = Math.round(canvas.screenHeight() / scale);
        int x = corner.left() ? config.offsetX : screenWidth - width - config.offsetX;
        int y = corner.top() ? config.offsetY : screenHeight - height - config.offsetY;

        float rest = 1f - popup;
        if (config.popupStyle == PopupStyle.SLIDE) {
            x += Math.round(rest * (corner.left() ? -(x + width + 4) : screenWidth - x + 4));
        } else if (config.popupStyle == PopupStyle.RISE) {
            y += Math.round(rest * 10f);
        } else if (config.popupStyle == PopupStyle.SCALE) {
            float zoom = 0.8f + 0.2f * popup;
            x = Math.round((x + width / 2f) / zoom - width / 2f);
            y = Math.round((y + height / 2f) / zoom - height / 2f);
            scale *= zoom;
        }

        canvas.push(scale);
        return new int[]{x, y};
    }

    private static void panel(Canvas canvas, HudConfig config, int x, int y, int width, int height, float alpha) {
        panel(canvas, config, x, y, width, height, Math.min(CORNER, height / 3), alpha);
    }

    private static void panel(Canvas canvas, HudConfig config, int x, int y, int width, int height, int radius,
                              float alpha) {
        int panelAlpha = Math.round(255 * (config.backgroundOpacity / 100f) * alpha);
        Shapes.fill(canvas, x, y, width, height, radius, argb(colorPanel, panelAlpha));
        Shapes.outline(canvas, x, y, width, height, radius, argb(colorEdge, Math.round(panelAlpha * 0.8f)));
    }

    /** Draws the album art (or a placeholder) and returns the x after it. */
    private static int cover(Canvas canvas, Track track, int x, int y, int size, float alpha) {
        artwork(canvas, track, x, y, size, alpha);
        return x + size + GAP;
    }

    /** Album art when there is one, a record for jukebox songs, a flat block otherwise. */
    private static void artwork(Canvas canvas, Track track, int x, int y, int size, float alpha) {
        Identifier art = CoverArt.get(track.artKey);
        if (art != null) {
            canvas.texture(art, x, y, size, size, CoverArt.TEXTURE_SIZE, alpha);
        } else if (track.artKey == null) {
            record(canvas, x, y, size, track.id.hashCode(), alpha);
        } else {
            Shapes.fill(canvas, x, y, size, size, Math.max(3, size / 8), argb(0x2A2A30, Math.round(255 * alpha)));
        }
    }

    /** A little vinyl: black disc, coloured label ring, centre hole. */
    private static void record(Canvas canvas, int x, int y, int size, int seed, float alpha) {
        int fullAlpha = Math.round(255 * alpha);
        int radius = size / 2;
        int[] palette = {0xD34F4F, 0x4F8FD3, 0x63C74D, 0xD3B24F, 0xA05FD3, 0x4FD3C0, 0xD37A4F};
        int label = palette[Math.floorMod(seed, palette.length)];
        for (int row = 0; row < size; row++) {
            double dy = row + 0.5 - radius;
            double half = Math.sqrt(Math.max(0, radius * radius - dy * dy));
            int span = (int) Math.round(half * 2);
            if (span <= 0) continue;
            int startX = x + radius - span / 2;
            canvas.rect(startX, y + row, span, 1, argb(0x121216, fullAlpha));

            double labelRadius = radius * 0.42;
            if (Math.abs(dy) < labelRadius) {
                double labelHalf = Math.sqrt(labelRadius * labelRadius - dy * dy);
                int labelSpan = (int) Math.round(labelHalf * 2);
                if (labelSpan > 0) {
                    canvas.rect(x + radius - labelSpan / 2, y + row, labelSpan, 1, argb(label, fullAlpha));
                }
            }
            if (Math.abs(dy) < 1.2) {
                canvas.rect(x + radius - 1, y + row, 2, 1, argb(0x101014, fullAlpha));
            }
        }
    }

    private static void progress(Canvas canvas, int x, int y, int width, int height, Track track, float alpha) {
        if (width <= 2) return;
        int filled = Math.round(width * fraction(track));
        int backing = argb(colorTrack, Math.round(200 * alpha));
        int fullAlpha = Math.round(255 * alpha);
        int radius = height / 2;
        Shapes.fill(canvas, x, y, width, height, radius, backing);
        if (filled > 1) {
            Shapes.fill(canvas, x, y, filled, height, radius,
                    argb(colorProgressStart, fullAlpha), argb(colorProgressEnd, fullAlpha));
        }
    }

    private static float fraction(Track track) {
        return track.durationMs <= 0
                ? 0f
                : Math.max(0f, Math.min(1f, track.currentProgressMs() / (float) track.durationMs));
    }

    private static void equaliser(Canvas canvas, int x, int centerY, Track track, float alpha, long now) {
        double seconds = now / 1_000_000_000.0;
        for (int i = 0; i < BAR_COUNT; i++) {
            double phase = seconds * 6.0 + i * 1.3;
            double wave = track.playing ? (Math.sin(phase) * 0.5 + Math.sin(phase * 0.6 + 1.1) * 0.5) : 0.0;
            // Measured at full height so the editor box and centred preview do not move with the bars
            int barHeight = canvas.measuring() ? 16 : track.playing
                    ? (int) Math.round(5 + Math.abs(wave) * 11)
                    : 4 + (i % 2) * 2;
            int barX = x + i * (BAR_WIDTH + BAR_GAP);
            int barY = centerY - barHeight / 2;
            canvas.rect(barX, barY, BAR_WIDTH, barHeight, argb(colorBars, Math.round(230 * alpha)));
        }
    }

    private static String text(String value) {
        if (value == null) return "";
        TextCase mode = HudConfig.get().textCase;
        if (mode == TextCase.UPPER) return value.toUpperCase(Locale.ROOT);
        if (mode == TextCase.LOWER) return value.toLowerCase(Locale.ROOT);
        if (mode != TextCase.CAPITALIZE) return value;
        StringBuilder builder = new StringBuilder(value.length());
        boolean start = true;
        for (char letter : value.toLowerCase(Locale.ROOT).toCharArray()) {
            builder.append(start ? Character.toUpperCase(letter) : letter);
            start = Character.isWhitespace(letter);
        }
        return builder.toString();
    }

    private static String timeLabel(Track track) {
        return Track.formatTime(track.currentProgressMs()) + " / " + Track.formatTime(track.durationMs);
    }

    private static int argb(int rgb, int alpha) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
