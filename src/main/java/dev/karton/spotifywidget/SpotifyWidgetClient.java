package dev.karton.spotifywidget;

import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.game.JukeboxWatcher;
import dev.karton.spotifywidget.hud.CoverArt;
import dev.karton.spotifywidget.hud.WidgetRenderer;
import dev.karton.spotifywidget.input.MediaKeys;
import dev.karton.spotifywidget.media.Media;
import dev.karton.spotifywidget.media.MediaControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader independent core. Each entrypoint under {@code loader/} calls {@link #init()} once, then
 * wires {@link #tick()}, {@link #render(Canvas)} and {@link #shutdown()} into its own events.
 */
public final class SpotifyWidgetClient {
    public static final String MOD_ID = "spotifywidget";
    public static final Logger LOGGER = LoggerFactory.getLogger("Spotify Widget");

    private SpotifyWidgetClient() {
    }

    /** Loads the config, creates the key mappings and starts reading the media session. */
    public static void init() {
        HudConfig.get();
        MediaKeys.register();
        Media.start();
    }

    /** End of every client tick. */
    public static void tick() {
        CoverArt.tick();
        MediaKeys.tick();
        JukeboxWatcher.tick();
    }

    /** Called from the loader's HUD layer. */
    public static void render(Canvas canvas) {
        WidgetRenderer.render(canvas);
    }

    /** Client is closing. */
    public static void shutdown() {
        MediaControl.shutdown();
        Media.stop();
    }
}
