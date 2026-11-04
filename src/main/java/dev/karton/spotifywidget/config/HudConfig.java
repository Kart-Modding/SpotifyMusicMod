package dev.karton.spotifywidget.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.karton.spotifywidget.SpotifyWidgetClient;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//?} elif neoforge {
/*import net.neoforged.fml.loading.FMLPaths;
*///?} else {
/*import net.minecraftforge.fml.loading.FMLPaths;
*///?}

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings, stored in {@code config/spotifywidget.json}.
 * Read and written by hand so an older config file never throws away unrelated keys.
 */
public final class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HudConfig instance;

    public boolean enabled = true;
    /** Only read the Spotify player, ignore browsers and other media apps. */
    public boolean spotifyAppOnly = true;
    /** How often the player is read, in milliseconds. */
    public int systemPollMillis = 1000;
    /** Show a playing jukebox record in the widget. */
    public boolean jukeboxWidget = true;
    /** Pause the desktop player while a jukebox record plays, and resume it afterwards. */
    public boolean pauseForJukebox = true;

    /** Which of the widget designs to draw. */
    public WidgetLayout layout = WidgetLayout.CLASSIC;
    public DisplayMode displayMode = DisplayMode.ALWAYS;
    /** Timed modes only: pop up when a track starts. */
    public boolean showOnSongStart = true;
    /** Timed modes only: pop up again for the last seconds of a track. */
    public boolean showOnSongEnd = true;

    public HudCorner corner = HudCorner.TOP_LEFT;
    public int offsetX = 6;
    public int offsetY = 6;
    public float scale = 1.0f;
    public int backgroundOpacity = 88; // percent
    public boolean showAlbumArt = true;
    public boolean showTimestamps = true;
    public boolean showWaveform = true;
    /** Keep the widget off screen while any GUI (inventory, chat, menus) is open. */
    public boolean hideWithGui = false;
    /** Hide the widget while the player is paused. */
    public boolean hidePaused = false;

    /** Smooth corners drawn at screen pixels. Off gives Minecraft pixel steps. */
    public boolean roundCorners = true;
    /** Longest the widget may get in GUI pixels. Longer names are cut or scrolled. */
    public int maxWidth = 240;

    public ThemeMode theme = ThemeMode.ALBUM;
    /** Preset last used as the start for custom colours. */
    public String palette = "Lilac";
    public int colorPanel = 0x262048;
    public int colorTitle = 0xE6BDFF;
    public int colorArtist = 0xD9D6EE;
    public int colorBarStart = 0x8F7BFF;
    public int colorBarEnd = 0xFFB8F0;
    public int colorDetails = 0xFFB8F0;

    public boolean animations = true;
    public PopupStyle popupStyle = PopupStyle.SLIDE;
    /** Seconds the pop-up animation takes. */
    public float animationTime = 0.5f;
    public boolean dimPaused = true;
    /** Hide the widget after a long pause. */
    public boolean hideIdle = true;
    public int idleMinutes = 3;

    public TextCase textCase = TextCase.NONE;
    public LongText longText = LongText.SCROLL;
    /** GUI pixels per second. */
    public int scrollSpeed = 18;
    /** Seconds the text rests at each end of the scroll. */
    public float scrollHold = 1.5f;

    public static HudConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Puts every setting back to its default, in place so existing references stay valid. */
    public void reset() {
        HudConfig defaults = new HudConfig();
        try {
            for (java.lang.reflect.Field field : HudConfig.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(this, field.get(defaults));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path path() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("spotifywidget.json");
        //?} else
        /*return FMLPaths.CONFIGDIR.get().resolve("spotifywidget.json");*/
    }

    public static HudConfig load() {
        HudConfig config = new HudConfig();
        Path path = path();
        if (!Files.exists(path)) {
            config.save();
            return config;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            config.enabled = bool(json, "enabled", config.enabled);
            config.spotifyAppOnly = bool(json, "spotifyAppOnly", config.spotifyAppOnly);
            config.systemPollMillis = Math.max(250, integer(json, "systemPollMillis", config.systemPollMillis));
            config.jukeboxWidget = bool(json, "jukeboxWidget", config.jukeboxWidget);
            config.pauseForJukebox = bool(json, "pauseForJukebox", config.pauseForJukebox);
            config.layout = WidgetLayout.byId(string(json, "layout", config.layout.id()), config.layout);
            config.displayMode = DisplayMode.byId(string(json, "displayMode", config.displayMode.id()), config.displayMode);
            config.showOnSongStart = bool(json, "showOnSongStart", config.showOnSongStart);
            config.showOnSongEnd = bool(json, "showOnSongEnd", config.showOnSongEnd);
            config.corner = HudCorner.byId(string(json, "corner", config.corner.id()), config.corner);
            config.offsetX = integer(json, "offsetX", config.offsetX);
            config.offsetY = integer(json, "offsetY", config.offsetY);
            config.scale = (float) number(json, "scale", config.scale);
            config.backgroundOpacity = integer(json, "backgroundOpacity", config.backgroundOpacity);
            config.showAlbumArt = bool(json, "showAlbumArt", config.showAlbumArt);
            config.showTimestamps = bool(json, "showTimestamps", config.showTimestamps);
            config.showWaveform = bool(json, "showWaveform", config.showWaveform);
            config.hideWithGui = bool(json, "hideWithGui", config.hideWithGui);
            config.hidePaused = bool(json, "hidePaused", config.hidePaused);
            config.roundCorners = bool(json, "roundCorners", config.roundCorners);
            config.maxWidth = integer(json, "maxWidth", config.maxWidth);
            config.theme = Choice.byId(ThemeMode.values(), string(json, "theme", config.theme.id()), config.theme);
            config.palette = string(json, "palette", config.palette);
            config.colorPanel = integer(json, "colorPanel", config.colorPanel);
            config.colorTitle = integer(json, "colorTitle", config.colorTitle);
            config.colorArtist = integer(json, "colorArtist", config.colorArtist);
            config.colorBarStart = integer(json, "colorBarStart", config.colorBarStart);
            config.colorBarEnd = integer(json, "colorBarEnd", config.colorBarEnd);
            config.colorDetails = integer(json, "colorDetails", config.colorDetails);
            config.animations = bool(json, "animations", config.animations);
            config.popupStyle = Choice.byId(PopupStyle.values(), string(json, "popupStyle", config.popupStyle.id()), config.popupStyle);
            config.animationTime = (float) number(json, "animationTime", config.animationTime);
            config.dimPaused = bool(json, "dimPaused", config.dimPaused);
            config.hideIdle = bool(json, "hideIdle", config.hideIdle);
            config.idleMinutes = integer(json, "idleMinutes", config.idleMinutes);
            config.textCase = Choice.byId(TextCase.values(), string(json, "textCase", config.textCase.id()), config.textCase);
            config.longText = Choice.byId(LongText.values(), string(json, "longText", config.longText.id()), config.longText);
            config.scrollSpeed = integer(json, "scrollSpeed", config.scrollSpeed);
            config.scrollHold = (float) number(json, "scrollHold", config.scrollHold);
        } catch (Exception e) {
            SpotifyWidgetClient.LOGGER.warn("Could not read spotifywidget.json, using defaults", e);
        }
        return config;
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("spotifyAppOnly", spotifyAppOnly);
        json.addProperty("systemPollMillis", systemPollMillis);
        json.addProperty("jukeboxWidget", jukeboxWidget);
        json.addProperty("pauseForJukebox", pauseForJukebox);
        json.addProperty("layout", layout.id());
        json.addProperty("displayMode", displayMode.id());
        json.addProperty("showOnSongStart", showOnSongStart);
        json.addProperty("showOnSongEnd", showOnSongEnd);
        json.addProperty("corner", corner.id());
        json.addProperty("offsetX", offsetX);
        json.addProperty("offsetY", offsetY);
        json.addProperty("scale", scale);
        json.addProperty("backgroundOpacity", backgroundOpacity);
        json.addProperty("showAlbumArt", showAlbumArt);
        json.addProperty("showTimestamps", showTimestamps);
        json.addProperty("showWaveform", showWaveform);
        json.addProperty("hideWithGui", hideWithGui);
        json.addProperty("hidePaused", hidePaused);
        json.addProperty("roundCorners", roundCorners);
        json.addProperty("maxWidth", maxWidth);
        json.addProperty("theme", theme.id());
        json.addProperty("palette", palette);
        json.addProperty("colorPanel", colorPanel);
        json.addProperty("colorTitle", colorTitle);
        json.addProperty("colorArtist", colorArtist);
        json.addProperty("colorBarStart", colorBarStart);
        json.addProperty("colorBarEnd", colorBarEnd);
        json.addProperty("colorDetails", colorDetails);
        json.addProperty("animations", animations);
        json.addProperty("popupStyle", popupStyle.id());
        json.addProperty("animationTime", animationTime);
        json.addProperty("dimPaused", dimPaused);
        json.addProperty("hideIdle", hideIdle);
        json.addProperty("idleMinutes", idleMinutes);
        json.addProperty("textCase", textCase.id());
        json.addProperty("longText", longText.id());
        json.addProperty("scrollSpeed", scrollSpeed);
        json.addProperty("scrollHold", scrollHold);
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpotifyWidgetClient.LOGGER.error("Could not write spotifywidget.json", e);
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }
}
