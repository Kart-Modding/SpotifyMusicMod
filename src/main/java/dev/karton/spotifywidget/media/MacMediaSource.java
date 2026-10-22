package dev.karton.spotifywidget.media;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.hud.CoverArt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the now-playing track on macOS by asking Spotify (or Apple Music) through {@code osascript}.
 * The scripts check "is running" first so polling never launches the player. Spotify hands out
 * the real cover URL; Apple Music covers are looked up by artist and title.
 */
public final class MacMediaSource implements MediaSource {
    /** Unit separator, kept out of any track title. */
    private static final String SEPARATOR = "\u001F";

    private static final String SPOTIFY_SCRIPT = String.join("\n",
            "if application \"Spotify\" is not running then return \"none\"",
            "tell application \"Spotify\"",
            "  set playerState to player state as string",
            "  if playerState is \"stopped\" then return \"none\"",
            "  set t to current track",
            "  set sep to ASCII character 31",
            "  return \"Spotify\" & sep & playerState & sep & (name of t) & sep & (artist of t) & sep & (duration of t) & sep & (round ((player position) * 1000)) & sep & (artwork url of t)",
            "end tell");

    private static final String MUSIC_SCRIPT = String.join("\n",
            "if application \"Music\" is not running then return \"none\"",
            "tell application \"Music\"",
            "  set playerState to player state as string",
            "  if playerState is \"stopped\" then return \"none\"",
            "  set t to current track",
            "  set sep to ASCII character 31",
            "  return \"Music\" & sep & playerState & sep & (name of t) & sep & (artist of t) & sep & (round ((duration of t) * 1000)) & sep & (round ((player position) * 1000)) & sep & \"\"",
            "end tell");

    private final AtomicReference<Track> track = new AtomicReference<>();
    private final AtomicReference<String> status = new AtomicReference<>("Starting");

    private volatile boolean running;
    private Thread thread;

    private String lastKey = "";
    private long lastReportedPosition = -1;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    @Override
    public void start() {
        if (running) return;
        if (!supported()) {
            status.set("macOS only");
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "spotify-widget-osascript");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        track.set(null);
    }

    @Override
    public Track track() {
        return track.get();
    }

    @Override
    public String status() {
        return status.get();
    }

    private void runLoop() {
        while (running) {
            try {
                poll();
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                status.set("Could not read the media player");
                SpotifyWidgetClient.LOGGER.warn("osascript poll failed: {}", e.toString());
            }
            try {
                Thread.sleep(Math.max(250, HudConfig.get().systemPollMillis));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void poll() throws Exception {
        Result result = osascript(SPOTIFY_SCRIPT);
        if (result.ok() && result.out().equals("none") && !HudConfig.get().spotifyAppOnly) {
            result = osascript(MUSIC_SCRIPT);
        }
        if (!result.ok()) {
            String err = result.err();
            if (err.equals("timeout")) {
                // The first call blocks while macOS shows the Automation permission prompt
                status.set("Waiting for macOS permission to control Spotify");
            } else if (err.contains("-1743") || err.toLowerCase(Locale.ROOT).contains("not authorized")) {
                status.set("Allow Minecraft to control Spotify in System Settings > Privacy & Security > Automation");
            } else if (err.contains("-1728") || err.contains("-2741")) {
                status.set("Spotify is not installed");
            } else {
                status.set("Could not read the media player");
            }
            clear();
            return;
        }
        handle(result.out());
    }

    private void clear() {
        track.set(null);
        lastKey = "";
        lastReportedPosition = -1;
    }

    private void handle(String line) {
        String[] parts = line.split(SEPARATOR, -1);
        if (parts.length < 7) {
            status.set(HudConfig.get().spotifyAppOnly ? "Spotify is not playing" : "Nothing playing");
            clear();
            return;
        }
        String app = parts[0];
        boolean playing = parts[1].equalsIgnoreCase("playing");
        String title = parts[2];
        String artist = parts[3];
        long duration = number(parts[4]);
        long position = number(parts[5]);
        String artUrl = parts[6];
        if (title.isEmpty() && artist.isEmpty()) {
            track.set(null);
            return;
        }

        String key = "sys_" + Integer.toHexString((artist + "|" + title).hashCode());
        boolean newTrack = !key.equals(lastKey);
        if (newTrack) {
            lastKey = key;
            lastReportedPosition = -1;
            if (!artUrl.isBlank()) CoverArt.offerRemote(key, artUrl);
            else CoverLookup.find(key, artist, title, url -> CoverArt.offerRemote(key, url));
        }

        // Only publish a new snapshot when something moved, so interpolation stays smooth
        Track existing = track.get();
        boolean positionMoved = position != lastReportedPosition;
        if (!newTrack && existing != null && !positionMoved && existing.playing == playing) return;
        lastReportedPosition = position;
        track.set(new Track(key, title, artist, key, duration, position, playing, System.nanoTime()));
        status.set(playing ? "Playing on " + app : "Paused");
    }

    private static long number(String text) {
        try {
            // AppleScript prints decimals with the system locale's separator
            return Math.round(Double.parseDouble(text.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    record Result(boolean ok, String out, String err) {
    }

    static Result osascript(String script) throws Exception {
        Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script).start();
        process.getOutputStream().close();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new Result(false, "", "timeout");
        }
        String out;
        String err;
        try (InputStream o = process.getInputStream(); InputStream e = process.getErrorStream()) {
            out = new String(o.readAllBytes(), StandardCharsets.UTF_8).strip();
            err = new String(e.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        return new Result(process.exitValue() == 0, out, err);
    }
}
