package dev.karton.spotifywidget.hud;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.compat.Textures;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads album art off-thread, rounds its corners and hands finished textures to the renderer.
 * Texture creation itself has to happen on the render thread, so decoded pixels wait in a queue.
 */
public final class CoverArt {
    /** Album art is drawn at 32 GUI pixels; the texture is kept at twice that for sharper scaling. */
    public static final int TEXTURE_SIZE = 64;
    private static final int CORNER_RADIUS = 8;
    /** Top of the large card: three texture pixels per GUI pixel, 132 x 46. */
    public static final int BANNER_WIDTH = 396;
    public static final int BANNER_HEIGHT = 138;
    private static final int BANNER_RADIUS = 36;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService DOWNLOADER = Executors.newFixedThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "spotify-widget-art");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<String, int[]> DECODED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> BANNERS = new ConcurrentHashMap<>();
    private static final Map<String, int[]> DECODED_BANNERS = new ConcurrentHashMap<>();
    private static final Map<String, Theme> THEMES = new ConcurrentHashMap<>();

    private CoverArt() {
    }

    /** Texture for this cache key, or null while the art is still on its way. */
    public static Identifier get(String key) {
        if (key == null || key.isBlank()) return null;
        return READY.get(key);
    }

    /** Wide crop of the cover for the large card, or null while it is on its way. */
    public static Identifier banner(String key) {
        if (key == null || key.isBlank()) return null;
        return BANNERS.get(key);
    }

    /** Colours picked from this cover, or null while it is on its way. */
    public static Theme theme(String key) {
        if (key == null || key.isBlank()) return null;
        return THEMES.get(key);
    }

    /** Registers artwork to download for a cache key. Ignored when that key is already known. */
    public static void offerRemote(String key, String url) {
        if (key == null || url == null || url.isBlank()) return;
        if (READY.containsKey(key) || DECODED.containsKey(key)) return;
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;
        DOWNLOADER.submit(() -> download(key, url));
    }

    /**
     * Uploads finished downloads to the GPU. Called from the client tick so texture creation never
     * happens in the middle of HUD rendering.
     */
    public static void tick() {
        if (DECODED.isEmpty()) return;
        for (String key : DECODED.keySet().toArray(new String[0])) {
            int[] pixels = DECODED.remove(key);
            if (pixels == null) continue;
            String name = Integer.toHexString(key.hashCode());
            Identifier id = Textures.upload("cover_" + name, pixels, TEXTURE_SIZE);
            if (id == null) continue;
            Identifier previous = READY.put(key, id);
            if (previous != null && !previous.equals(id)) Textures.release(previous);
            int[] banner = DECODED_BANNERS.remove(key);
            if (banner != null) {
                Identifier bannerId = Textures.upload("banner_" + name, banner, BANNER_WIDTH, BANNER_HEIGHT);
                Identifier previousBanner = bannerId == null ? null : BANNERS.put(key, bannerId);
                if (previousBanner != null && !previousBanner.equals(bannerId)) Textures.release(previousBanner);
            }
            trim(key);
        }
    }

    /** Keeps the texture list short - only the newest few covers stay on the GPU. */
    private static void trim(String keep) {
        if (READY.size() <= 4) return;
        for (Map.Entry<String, Identifier> entry : READY.entrySet()) {
            if (entry.getKey().equals(keep)) continue;
            READY.remove(entry.getKey());
            Textures.release(entry.getValue());
            THEMES.remove(entry.getKey());
            Identifier banner = BANNERS.remove(entry.getKey());
            if (banner != null) Textures.release(banner);
            if (READY.size() <= 4) return;
        }
    }

    private static void download(String key, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                SpotifyWidgetClient.LOGGER.warn("Album art request returned {}", response.statusCode());
                return;
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (source == null) return;
            THEMES.put(key, Theme.fromPixels(square(source, 24)));
            DECODED_BANNERS.put(key, toBannerArgb(source));
            DECODED.put(key, toRoundedArgb(source));
        } catch (Exception e) {
            SpotifyWidgetClient.LOGGER.warn("Album art download failed", e);
        } finally {
            IN_FLIGHT.remove(key);
        }
    }

    private static int[] square(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        return scaled.getRGB(0, 0, size, size, null, 0, size);
    }

    /** Wide band out of the cover, with the top corners rounded to fit the card. */
    private static int[] toBannerArgb(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(BANNER_WIDTH, BANNER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int cropHeight = Math.min(source.getHeight(), Math.round(source.getWidth() * BANNER_HEIGHT / (float) BANNER_WIDTH));
        int cropTop = Math.round((source.getHeight() - cropHeight) * 0.45f);
        graphics.drawImage(source, 0, 0, BANNER_WIDTH, BANNER_HEIGHT,
                0, cropTop, source.getWidth(), cropTop + cropHeight, null);
        graphics.dispose();

        int[] pixels = scaled.getRGB(0, 0, BANNER_WIDTH, BANNER_HEIGHT, null, 0, BANNER_WIDTH);
        for (int y = 0; y < BANNER_RADIUS; y++) {
            for (int x = 0; x < BANNER_WIDTH; x++) {
                float dx = Math.max(Math.max(BANNER_RADIUS - x - 0.5f, x + 0.5f - (BANNER_WIDTH - BANNER_RADIUS)), 0f);
                if (dx == 0f) continue;
                float dy = BANNER_RADIUS - y - 0.5f;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float coverage = Math.max(0f, Math.min(1f, BANNER_RADIUS + 0.5f - distance));
                int index = y * BANNER_WIDTH + x;
                int alpha = Math.round((pixels[index] >>> 24) * coverage);
                pixels[index] = (alpha << 24) | (pixels[index] & 0x00FFFFFF);
            }
        }
        return pixels;
    }

    /** Scales to {@link #TEXTURE_SIZE}, then feathers the corners into a rounded square. */
    private static int[] toRoundedArgb(BufferedImage source) {
        int[] pixels = square(source, TEXTURE_SIZE);
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                float coverage = cornerCoverage(x, y);
                if (coverage >= 1.0f) continue;
                int index = y * TEXTURE_SIZE + x;
                int argb = pixels[index];
                int alpha = Math.round(((argb >>> 24) & 0xFF) * coverage);
                pixels[index] = (alpha << 24) | (argb & 0x00FFFFFF);
            }
        }
        return pixels;
    }

    /** 1 inside the rounded square, 0 outside, fractional on the corner edge. */
    private static float cornerCoverage(int x, int y) {
        float cx = x + 0.5f;
        float cy = y + 0.5f;
        float dx = 0;
        float dy = 0;
        if (cx < CORNER_RADIUS) dx = CORNER_RADIUS - cx;
        else if (cx > TEXTURE_SIZE - CORNER_RADIUS) dx = cx - (TEXTURE_SIZE - CORNER_RADIUS);
        if (cy < CORNER_RADIUS) dy = CORNER_RADIUS - cy;
        else if (cy > TEXTURE_SIZE - CORNER_RADIUS) dy = cy - (TEXTURE_SIZE - CORNER_RADIUS);
        if (dx == 0 || dy == 0) return 1.0f;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= CORNER_RADIUS - 0.5f) return 1.0f;
        if (distance >= CORNER_RADIUS + 0.5f) return 0.0f;
        return CORNER_RADIUS + 0.5f - distance;
    }
}
