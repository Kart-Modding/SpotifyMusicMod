package dev.karton.spotifywidget.config;

import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.compat.Painter;
import dev.karton.spotifywidget.compat.Screens;
import dev.karton.spotifywidget.hud.Theme;
import dev.karton.spotifywidget.hud.WidgetRenderer;
import dev.karton.spotifywidget.media.Media;
import dev.karton.spotifywidget.media.MediaControl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Settings screen, reachable from Mod Menu: a live preview of the widget on the left, a searchable
 * scrolling list of settings on the right. Built out of plain vanilla widgets so the same code
 * works on every supported game version. The list is laid out by hand for the same reason, as the
 * vanilla list classes change between versions.
 */
public class ConfigScreen extends Screen {
    private static final int HEADER = 24;
    private static final int FOOTER = 30;
    private static final int ROW = 22;
    private static final int SECTION = 18;
    private static final int SCROLL_STEP = 22;
    private static final int LINE = 0xFF5A5A5A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int DIM = 0xFF707070;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int SELECTED = 0xFF55FF55;
    private static final String NO_COLOURS = "Above hotbar and Vanilla tooltip keep their own colours.";

    private final Screen parent;
    private final HudConfig config = HudConfig.get();

    private final List<Row> rows = new ArrayList<>();
    private String search = "";
    private boolean searchFocused;
    private boolean rebuild;
    private int scroll;
    private int contentHeight;
    private boolean draggingBar;
    private double grabOffset;
    /** Colour row being edited, identified by its label, and its value as hue, saturation, brightness. */
    private String openColour;
    private float[] hsb = new float[3];
    private String hexText = "";

    private EditBox searchBox;
    private Button playButton;
    private int listX;
    private int listWidth;
    private int listTop;
    private int listBottom;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Spoty Widget Settings"));
        this.parent = parent;
    }

    /** One entry of the list and the widgets it owns. */
    private static final class Row {
        final String label;
        final String search;
        final int height;
        final boolean section;
        boolean labelled = true;
        BooleanSupplier enabled = () -> true;
        String tip = "";
        String disabledTip = "";
        BooleanSupplier changed = () -> false;
        Runnable reset = () -> {
        };
        Runnable refresh = () -> {
        };
        Consumer<Canvas> overlay = canvas -> {
        };
        final List<AbstractWidget> widgets = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        Button resetButton;
        int y;

        Row(String label, String search, int height, boolean section) {
            this.label = label;
            this.search = search;
            this.height = height;
            this.section = section;
        }

        Row when(BooleanSupplier condition, String why) {
            enabled = condition;
            disabledTip = why;
            return this;
        }

        void add(AbstractWidget widget, int offset) {
            widgets.add(widget);
            offsets.add(offset);
        }
    }

    // --- layout ----------------------------------------------------------------

    @Override
    protected void init() {
        WidgetRenderer.setHudHidden(true);
        listWidth = Math.max(214, Math.min(320, this.width / 2));
        listX = this.width - listWidth;
        listTop = HEADER + 24;
        listBottom = this.height - FOOTER;

        addRenderableOnly(new Painter(this::paintBase));
        addLeftButtons();

        searchBox = new EditBox(this.font, listX + 5, HEADER + 4, listWidth - 10, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search settings...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(32);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            if (value.equals(search)) return;
            search = value;
            scroll = 0;
            rebuild = true;
        });
        addRenderableWidget(searchBox);
        if (searchFocused) {
            setFocused(searchBox);
            searchBox.setFocused(true);
        }

        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), button -> confirmReset())
                .bounds(this.width - 214, this.height - 25, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width - 106, this.height - 25, 100, 20).build());

        addRenderableOnly(new Painter(this::beginList));
        rows.clear();
        buildRows();
        placeRows();
        addRenderableOnly(new Painter(this::endList));
        refresh();
    }

    private void addLeftButtons() {
        int width = listX;
        int buttonWidth = Math.min(56, (width - 16) / 3);
        int moveY = this.height - FOOTER - 34;
        int buttonsY = moveY - 22;
        int x = (width - buttonWidth * 3 - 4) / 2;
        playButton = addRenderableWidget(Button.builder(Component.literal("Pause"), button -> MediaControl.playPause())
                .bounds(x, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), button -> MediaControl.next())
                .bounds(x + buttonWidth + 2, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Replay"), button -> WidgetRenderer.replay())
                .bounds(x + (buttonWidth + 2) * 2, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move on screen"), button -> openEditor())
                .bounds(x, moveY, buttonWidth * 3 + 4, 20).build());
    }

    private void openEditor() {
        config.save();
        Screens.open(new MoveScreen(parent));
    }

    private void confirmReset() {
        Screens.open(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                config.reset();
                config.save();
            }
            Screens.open(new ConfigScreen(parent));
        }, Component.literal("Reset all settings?"),
                Component.literal("Every Spoty Widget option goes back to its default.")));
    }

    // --- the settings ----------------------------------------------------------

    private void buildRows() {
        HudConfig defaults = new HudConfig();
        BooleanSupplier themed = () -> config.layout.themed();
        BooleanSupplier longText = () -> config.layout.card() || config.layout == WidgetLayout.COMPACT;

        List<Row> all = new ArrayList<>();
        all.add(section("General"));
        all.add(toggle("Widget", "Turns the whole widget on or off.", () -> config.enabled, v -> config.enabled = v, defaults.enabled));
        all.add(flip("Player", "Spotify only ignores browsers and other media apps.", () -> config.spotifyAppOnly,
                v -> config.spotifyAppOnly = v, "Spotify only", "Any player", defaults.spotifyAppOnly));
        all.add(slider("Refresh", "How often the mod reads the player.", 250, 3000, 250,
                () -> config.systemPollMillis, v -> config.systemPollMillis = (int) v, v -> (int) v + " ms", defaults.systemPollMillis));

        all.add(section("Design"));
        all.add(designGrid());
        all.add(toggle("Round corners", "On: smooth, truly round corners. Off: Minecraft pixel steps.",
                () -> config.roundCorners, v -> config.roundCorners = v, defaults.roundCorners));
        all.add(toggle("Album art", "Shows the album cover.", () -> config.showAlbumArt, v -> config.showAlbumArt = v, defaults.showAlbumArt));
        all.add(toggle("Timestamps", "Shows elapsed and total time.", () -> config.showTimestamps, v -> config.showTimestamps = v, defaults.showTimestamps));
        all.add(toggle("Equaliser bars", "Small animated bars.", () -> config.showWaveform, v -> config.showWaveform = v, defaults.showWaveform)
                .when(() -> !config.layout.card() && config.layout != WidgetLayout.COMPACT, "This design has no equaliser."));
        all.add(slider("Max width", "Longest the widget may get. Longer names are cut or scrolled.", 100, 480, 5,
                () -> config.maxWidth, v -> config.maxWidth = (int) v, v -> (int) v + " px", defaults.maxWidth));

        all.add(section("Colours"));
        all.add(choice("Theme", "Default is the standard look. From album picks colours from each cover.", ThemeMode.values(),
                () -> config.theme, v -> config.theme = v, defaults.theme).when(themed, NO_COLOURS));
        all.add(paletteRow().when(themed, NO_COLOURS));
        addColour(all, "Background", () -> config.colorPanel, v -> config.colorPanel = v, defaults.colorPanel, themed);
        addColour(all, "Song text", () -> config.colorTitle, v -> config.colorTitle = v, defaults.colorTitle, themed);
        addColour(all, "Artist text", () -> config.colorArtist, v -> config.colorArtist = v, defaults.colorArtist, themed);
        addColour(all, "Progress start", () -> config.colorBarStart, v -> config.colorBarStart = v, defaults.colorBarStart, themed);
        addColour(all, "Progress end", () -> config.colorBarEnd, v -> config.colorBarEnd = v, defaults.colorBarEnd, themed);
        addColour(all, "Details", () -> config.colorDetails, v -> config.colorDetails = v, defaults.colorDetails, themed);

        all.add(section("Position"));
        all.add(action("Visual editor", "Move on screen", "Drag the widget over the game. Scroll to resize.", this::openEditor));
        BooleanSupplier cornered = () -> config.layout != WidgetLayout.HOTBAR;
        all.add(choice("Corner", "Which screen corner the widget sits in.", HudCorner.values(),
                () -> config.corner, v -> config.corner = v, defaults.corner)
                .when(cornered, "Above hotbar sits over the hotbar, not in a corner."));
        all.add(slider("Scale", "Size of the widget.", 0.5, 2, 0.05,
                () -> config.scale, v -> config.scale = (float) v, v -> String.format(Locale.ROOT, "%.2fx", v), defaults.scale));
        all.add(slider("Offset X", "Distance from the screen edge.", 0, Math.max(100, this.width), 1,
                () -> config.offsetX, v -> config.offsetX = (int) v, v -> String.valueOf((int) v), defaults.offsetX)
                .when(cornered, "Above hotbar uses Hotbar shift."));
        all.add(slider("Hotbar shift", "Sideways distance from the middle of the screen.",
                -Math.max(100, this.width / 2), Math.max(100, this.width / 2), 1,
                () -> config.hotbarShift, v -> config.hotbarShift = (int) v, v -> String.valueOf((int) v), defaults.hotbarShift)
                .when(() -> config.layout == WidgetLayout.HOTBAR, "Only for Above hotbar."));
        all.add(slider("Offset Y", "Distance from the screen edge.", -12, Math.max(100, this.height), 1,
                () -> config.offsetY, v -> config.offsetY = (int) v, v -> String.valueOf((int) v), defaults.offsetY));
        all.add(slider("Background", "Opacity of the widget background.", 0, 100, 1,
                () -> config.backgroundOpacity, v -> config.backgroundOpacity = (int) v, v -> (int) v + "%", defaults.backgroundOpacity));

        all.add(section("When to show"));
        BooleanSupplier timedMode = () -> config.displayMode.timed();
        all.add(choice("Display", "How long the widget stays on screen.", DisplayMode.values(),
                () -> config.displayMode, v -> config.displayMode = v, defaults.displayMode));
        all.add(toggle("Song start", "Pop up when a song starts.", () -> config.showOnSongStart, v -> config.showOnSongStart = v, defaults.showOnSongStart)
                .when(timedMode, "Only when Display is timed."));
        all.add(toggle("Song end", "Pop up near the end of a song.", () -> config.showOnSongEnd, v -> config.showOnSongEnd = v, defaults.showOnSongEnd)
                .when(timedMode, "Only when Display is timed."));
        all.add(toggle("Hide when paused", "Hides the widget while paused.", () -> config.hidePaused, v -> config.hidePaused = v, defaults.hidePaused));
        all.add(toggle("Hide in menus", "Hides it in chat, inventory and menus.", () -> config.hideWithGui, v -> config.hideWithGui = v, defaults.hideWithGui));
        all.add(toggle("Dim when paused", "Fades the widget while paused.", () -> config.dimPaused, v -> config.dimPaused = v, defaults.dimPaused));
        all.add(toggle("Hide when idle", "Hides it after a long pause.", () -> config.hideIdle, v -> config.hideIdle = v, defaults.hideIdle));
        all.add(slider("Idle after", "Minutes paused before it hides.", 1, 15, 1,
                () -> config.idleMinutes, v -> config.idleMinutes = (int) v, v -> (int) v + " min", defaults.idleMinutes)
                .when(() -> config.hideIdle, "Needs Hide when idle."));

        all.add(section("Animation"));
        all.add(toggle("Animations", "Turns all widget animations on or off.", () -> config.animations, v -> config.animations = v, defaults.animations));
        all.add(choice("Pop-up style", "How the widget appears.", PopupStyle.values(),
                () -> config.popupStyle, v -> config.popupStyle = v, defaults.popupStyle)
                .when(() -> config.animations, "Needs Animations."));
        all.add(slider("Duration", "How long the pop-up takes.", 0.1, 1.5, 0.05,
                () -> config.animationTime, v -> config.animationTime = (float) v, v -> String.format(Locale.ROOT, "%.2f s", v), defaults.animationTime)
                .when(() -> config.animations, "Needs Animations."));

        all.add(section("Text"));
        BooleanSupplier scrolling = () -> longText.getAsBoolean() && config.longText == LongText.SCROLL;
        String scrollWhy = "Needs Long text: Scroll on a design that uses it.";
        all.add(choice("Case", "Text style for song and artist.", TextCase.values(),
                () -> config.textCase, v -> config.textCase = v, defaults.textCase));
        all.add(choice("Long text", "What happens when a name does not fit.", LongText.values(),
                () -> config.longText, v -> config.longText = v, defaults.longText).when(longText, "Only for Compact and the card designs."));
        all.add(slider("Scroll speed", "How fast long text moves.", 6, 40, 1,
                () -> config.scrollSpeed, v -> config.scrollSpeed = (int) v, v -> (int) v + " px/s", defaults.scrollSpeed).when(scrolling, scrollWhy));
        all.add(slider("Wait at ends", "Pause at each end of the scroll.", 0, 5, 0.5,
                () -> config.scrollHold, v -> config.scrollHold = (float) v, v -> String.format(Locale.ROOT, "%.1f s", v), defaults.scrollHold).when(scrolling, scrollWhy));

        all.add(section("Jukebox"));
        all.add(toggle("Show jukebox", "Shows a nearby record in the widget.", () -> config.jukeboxWidget, v -> config.jukeboxWidget = v, defaults.jukeboxWidget));
        all.add(toggle("Pause for jukebox", "Pauses the player while a record plays.", () -> config.pauseForJukebox, v -> config.pauseForJukebox = v, defaults.pauseForJukebox)
                .when(() -> config.jukeboxWidget, "Needs Show jukebox."));

        filter(all);
        for (Row row : rows) {
            for (AbstractWidget widget : row.widgets) addRenderableWidget(widget);
            if (row.resetButton != null) addRenderableWidget(row.resetButton);
        }
    }

    private void filter(List<Row> all) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        int y = 2;
        Row pendingSection = null;
        boolean sectionHit = false;
        for (Row row : all) {
            if (row.section) {
                pendingSection = row;
                sectionHit = !query.isEmpty() && row.search.contains(query);
                if (query.isEmpty()) {
                    row.y = y;
                    y += row.height;
                    rows.add(row);
                    pendingSection = null;
                }
                continue;
            }
            if (!query.isEmpty() && !sectionHit && !row.search.contains(query)) continue;
            if (pendingSection != null) {
                pendingSection.y = y;
                y += pendingSection.height;
                rows.add(pendingSection);
                pendingSection = null;
            }
            row.y = y;
            y += row.height;
            rows.add(row);
        }
        contentHeight = y + 4;
    }

    private int controlWidth() {
        return Math.min(106, rowWidth() / 2);
    }

    private int rowWidth() {
        return listWidth - 18;
    }

    private int controlX() {
        return listX + 5 + rowWidth() - controlWidth() - 17;
    }

    private Row section(String title) {
        return new Row(title, title.toLowerCase(Locale.ROOT), SECTION, true);
    }

    private Row row(String label, String tip, String extraSearch, int height) {
        Row row = new Row(label, (label + " " + extraSearch).toLowerCase(Locale.ROOT), height, false);
        row.tip = tip;
        return row;
    }

    private void withReset(Row row, BooleanSupplier changed, Runnable reset) {
        row.changed = changed;
        row.reset = reset;
        Button button = Button.builder(Component.empty(), pressed -> {
            reset.run();
            rebuild = true;
        }).bounds(listX + 5 + rowWidth() - 15, 0, 15, 20).tooltip(Tooltip.create(Component.literal("Reset to default"))).build();
        row.resetButton = button;
    }

    private Row toggle(String label, String tip, BooleanSupplier get, Consumer<Boolean> set, boolean fallback) {
        Row row = row(label, tip, "", ROW);
        Supplier<Component> text = () -> get.getAsBoolean()
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.RED);
        Button button = Button.builder(text.get(), pressed -> {
            set.accept(!get.getAsBoolean());
            refresh();
        }).bounds(controlX(), 0, controlWidth(), 20).build();
        row.add(button, 0);
        row.refresh = () -> button.setMessage(text.get());
        withReset(row, () -> get.getAsBoolean() != fallback, () -> set.accept(fallback));
        return row;
    }

    private Row flip(String label, String tip, BooleanSupplier get, Consumer<Boolean> set, String on, String off, boolean fallback) {
        Row row = row(label, tip, on + " " + off, ROW);
        Button button = Button.builder(Component.literal(get.getAsBoolean() ? on : off), pressed -> {
            set.accept(!get.getAsBoolean());
            refresh();
        }).bounds(controlX(), 0, controlWidth(), 20).build();
        row.add(button, 0);
        row.refresh = () -> button.setMessage(Component.literal(get.getAsBoolean() ? on : off));
        withReset(row, () -> get.getAsBoolean() != fallback, () -> set.accept(fallback));
        return row;
    }

    private <T extends Choice> Row choice(String label, String tip, T[] values, Supplier<T> get, Consumer<T> set, T fallback) {
        StringBuilder names = new StringBuilder();
        for (T value : values) names.append(value.label()).append(' ');
        Row row = row(label, tip, names.toString(), ROW);
        Button button = Button.builder(Component.literal(get.get().label()), pressed -> {
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == get.get()) index = i;
            }
            set.accept(values[(index + 1) % values.length]);
            refresh();
        }).bounds(controlX(), 0, controlWidth(), 20).build();
        row.add(button, 0);
        row.refresh = () -> button.setMessage(Component.literal(get.get().label()));
        withReset(row, () -> get.get() != fallback, () -> set.accept(fallback));
        return row;
    }

    private Row slider(String label, String tip, double min, double max, double step, DoubleSupplier get,
                       DoubleConsumer set, DoubleFunction<String> format, double fallback) {
        Row row = row(label, tip, "", ROW);
        ValueSlider slider = new ValueSlider(controlX(), controlWidth(), 20, min, max, step, get, set, format);
        row.add(slider, 0);
        row.refresh = slider::sync;
        withReset(row, () -> Math.abs(get.getAsDouble() - fallback) > 0.001, () -> set.accept(fallback));
        return row;
    }

    private Row action(String label, String text, String tip, Runnable run) {
        Row row = row(label, tip, text, ROW);
        row.add(Button.builder(Component.literal(text), pressed -> run.run())
                .bounds(controlX(), 0, controlWidth(), 20).build(), 0);
        return row;
    }

    private Row designGrid() {
        StringBuilder names = new StringBuilder();
        for (WidgetLayout layout : WidgetLayout.values()) names.append(layout.label()).append(' ');
        WidgetLayout[] layouts = WidgetLayout.values();
        int height = ROW * ((layouts.length + 1) / 2) + 2;
        Row row = row("Design", "Pick a design. The preview updates straight away.", names.toString(), height);
        row.labelled = false;
        int width = (rowWidth() - 2) / 2;
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < layouts.length; i++) {
            WidgetLayout layout = layouts[i];
            Button button = Button.builder(Component.literal(layout.label()), pressed -> {
                config.layout = layout;
                refresh();
            }).bounds(listX + 5 + (i % 2) * (width + 2), 0, width, 20).build();
            row.add(button, (i / 2) * ROW);
            buttons.add(button);
        }
        row.overlay = canvas -> {
            for (int i = 0; i < buttons.size(); i++) {
                if (layouts[i] != config.layout || !buttons.get(i).visible) continue;
                Button button = buttons.get(i);
                frame(canvas, button.getX(), button.getY(), button.getWidth(), button.getHeight(), SELECTED);
            }
        };
        return row;
    }

    private Row paletteRow() {
        String[] names = Theme.PALETTES.keySet().toArray(new String[0]);
        Row row = row("Start from", "Fills the colours below from a preset.", String.join(" ", names), ROW);
        Button button = Button.builder(Component.literal(config.palette), pressed -> {
            int index = 0;
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(config.palette)) index = i;
            }
            String next = names[(index + 1) % names.length];
            Theme theme = Theme.PALETTES.get(next);
            config.palette = next;
            config.theme = ThemeMode.CUSTOM;
            config.colorPanel = theme.panel;
            config.colorTitle = theme.title;
            config.colorArtist = theme.artist;
            config.colorBarStart = theme.barStart;
            config.colorBarEnd = theme.barEnd;
            config.colorDetails = theme.details;
            openColour = null;
            rebuild = true;
        }).bounds(controlX(), 0, controlWidth(), 20).build();
        row.add(button, 0);
        return row;
    }

    private void addColour(List<Row> all, String label, IntSupplier get, IntConsumer set, int fallback, BooleanSupplier themed) {
        boolean open = label.equals(openColour);
        Row row = row(label, "Click to edit this colour.", "colour color", open ? ROW + 66 : ROW).when(themed, NO_COLOURS);
        Button button = Button.builder(Component.literal("      " + hex(get.getAsInt())), pressed -> {
            if (open) {
                openColour = null;
            } else {
                openColour = label;
                int value = get.getAsInt();
                hsb = Color.RGBtoHSB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, null);
                hexText = hex(value);
            }
            rebuild = true;
        }).bounds(controlX(), 0, controlWidth(), 20).build();
        row.add(button, 0);
        row.refresh = () -> button.setMessage(Component.literal("      " + hex(get.getAsInt())));
        withReset(row, () -> get.getAsInt() != fallback, () -> set.accept(fallback));

        List<AbstractWidget> strips = new ArrayList<>();
        if (open) {
            int sliderX = listX + 5 + 36;
            int sliderWidth = rowWidth() - 38;
            String[] names = {"Hue", "Sat", "Bright"};
            for (int i = 0; i < 3; i++) {
                int part = i;
                ValueSlider slider = new ValueSlider(sliderX, sliderWidth, 14, 0, 1, 0.005,
                        () -> hsb[part], value -> {
                    hsb[part] = (float) value;
                    int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
                    config.theme = ThemeMode.CUSTOM;
                    set.accept(rgb);
                    hexText = hex(rgb);
                }, value -> "");
                row.add(slider, ROW + i * 16);
                strips.add(slider);
            }
            EditBox hexBox = new EditBox(this.font, sliderX, 0, 62, 16, Component.literal("Hex"));
            hexBox.setMaxLength(7);
            hexBox.setValue(hexText);
            hexBox.setResponder(value -> {
                hexText = value.toUpperCase(Locale.ROOT);
                if (!hexText.matches("#[0-9A-F]{6}")) return;
                int rgb = Integer.parseInt(hexText.substring(1), 16);
                config.theme = ThemeMode.CUSTOM;
                set.accept(rgb);
                hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            });
            row.add(hexBox, ROW + 48);
        }
        row.overlay = canvas -> {
            if (!button.visible) return;
            int swatchX = button.getX() + 4;
            int swatchY = button.getY() + 4;
            canvas.rect(swatchX, swatchY, 12, 12, 0xFF000000);
            canvas.rect(swatchX + 1, swatchY + 1, 10, 10, row.enabled.getAsBoolean() ? 0xFF000000 | get.getAsInt() : 0xFF555555);
            if (!open) return;
            String[] names = {"Hue", "Sat", "Bright"};
            for (int i = 0; i < 3; i++) {
                AbstractWidget slider = strips.get(i);
                canvas.text(names[i], listX + 7, slider.getY() + 3, GREY, true);
                gradient(canvas, slider.getX() + 1, slider.getY() + slider.getHeight() - 3, slider.getWidth() - 2, i);
            }
            canvas.text("Hex", listX + 7, button.getY() + ROW + 52, GREY, true);
        };
        all.add(row);
    }

    private void gradient(Canvas canvas, int x, int y, int width, int part) {
        for (int i = 0; i < width; i++) {
            float value = i / (float) Math.max(1, width - 1);
            int color = switch (part) {
                case 0 -> Color.HSBtoRGB(value, 1f, 1f);
                case 1 -> Color.HSBtoRGB(hsb[0], value, Math.max(hsb[2], 0.15f));
                default -> Color.HSBtoRGB(hsb[0], hsb[1], value);
            };
            canvas.rect(x + i, y, 1, 2, 0xFF000000 | color);
        }
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static void frame(Canvas canvas, int x, int y, int width, int height, int color) {
        canvas.rect(x, y, width, 1, color);
        canvas.rect(x, y + height - 1, width, 1, color);
        canvas.rect(x, y, 1, height, color);
        canvas.rect(x + width - 1, y, 1, height, color);
    }

    // --- scrolling -------------------------------------------------------------

    private int maxScroll() {
        return Math.max(0, contentHeight - (listBottom - listTop));
    }

    private void placeRows() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (Row row : rows) {
            int y = listTop + row.y - scroll;
            boolean inView = y + row.height > listTop && y < listBottom;
            for (int i = 0; i < row.widgets.size(); i++) {
                AbstractWidget widget = row.widgets.get(i);
                widget.setY(y + row.offsets.get(i));
                widget.visible = inView && widget.getY() + widget.getHeight() > listTop && widget.getY() < listBottom;
            }
            if (row.resetButton != null) {
                row.resetButton.setY(y);
                row.resetButton.visible = inView && row.enabled.getAsBoolean() && row.changed.getAsBoolean();
            }
        }
    }

    private void refresh() {
        for (Row row : rows) {
            row.refresh.run();
            boolean enabled = row.enabled.getAsBoolean();
            String tip = enabled ? row.tip : row.disabledTip;
            for (AbstractWidget widget : row.widgets) {
                widget.active = enabled;
                if (!tip.isEmpty()) widget.setTooltip(Tooltip.create(Component.literal(tip)));
            }
        }
        placeRows();
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuild) {
            rebuild = false;
            searchFocused = searchBox.isFocused();
            rebuildWidgets();
        }
        refresh();
        playButton.setMessage(Component.literal(paused() ? "Play" : "Pause"));
    }

    private static boolean paused() {
        return Media.track() != null && !Media.track().playing;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < listX || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll -= (int) Math.round(scrollY * SCROLL_STEP);
        placeRows();
        return true;
    }

    private int knobHeight() {
        int height = listBottom - listTop;
        return Math.max(16, height * height / Math.max(1, contentHeight));
    }

    private int knobY() {
        int travel = listBottom - listTop - knobHeight();
        return listTop + (maxScroll() == 0 ? 0 : travel * scroll / maxScroll());
    }

    private boolean grabBar(double mouseX, double mouseY) {
        if (maxScroll() == 0 || mouseX < this.width - 7 || mouseY < listTop || mouseY >= listBottom) return false;
        int knob = knobY();
        draggingBar = true;
        grabOffset = mouseY >= knob && mouseY < knob + knobHeight() ? mouseY - knob : knobHeight() / 2.0;
        dragBar(mouseY);
        return true;
    }

    private void dragBar(double mouseY) {
        double travel = listBottom - listTop - knobHeight();
        scroll = (int) Math.round((mouseY - listTop - grabOffset) / travel * maxScroll());
        placeRows();
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && grabBar(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!draggingBar) return super.mouseDragged(event, deltaX, deltaY);
        dragBar(event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && grabBar(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggingBar) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        dragBar(mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    // --- painting --------------------------------------------------------------

    private void paintBase(Canvas canvas) {
        canvas.rect(listX, HEADER, listWidth, listBottom - HEADER, 0xAD000000);
        canvas.rect(0, 0, this.width, HEADER, 0xC7000000);
        canvas.rect(0, this.height - FOOTER, this.width, FOOTER, 0xC7000000);
        canvas.rect(0, HEADER - 1, this.width, 1, LINE);
        canvas.rect(0, this.height - FOOTER, this.width, 1, LINE);
        canvas.text(this.title.getString(), (this.width - canvas.textWidth(this.title.getString())) / 2, 8, WHITE, true);
        canvas.text(canvas.trim("Source: " + Media.status(), this.width - 226), 8, this.height - 19, GREY, true);

        int buttonsY = this.height - FOOTER - 56;
        canvas.text("Preview", 8, HEADER + 5, WHITE, true);
        WidgetRenderer.preview(canvas, 4, HEADER + 15, listX - 8, buttonsY - HEADER - 19);
        String placement = canvas.trim(MoveScreen.placement(config), listX - 8);
        canvas.text(placement, (listX - canvas.textWidth(placement)) / 2, this.height - FOOTER - 11, GREY, true);
    }

    private void beginList(Canvas canvas) {
        canvas.clip(listX, listTop, this.width, listBottom);
        int rowX = listX + 5;
        for (Row row : rows) {
            int y = listTop + row.y - scroll;
            if (y + row.height <= listTop || y >= listBottom) continue;
            if (row.section) {
                canvas.text(row.label, listX + (rowWidth() - canvas.textWidth(row.label)) / 2, y + 7, YELLOW, true);
            } else if (row.labelled) {
                String label = canvas.trim(row.label, controlX() - rowX - 6);
                canvas.text(label, rowX + 2, y + 6, row.enabled.getAsBoolean() ? WHITE : DIM, true);
            }
        }
    }

    private void endList(Canvas canvas) {
        for (Row row : rows) {
            row.overlay.accept(canvas);
            if (row.resetButton != null && row.resetButton.visible) {
                resetIcon(canvas, row.resetButton.getX() + 3, row.resetButton.getY() + 6);
            }
        }
        canvas.endClip();
        if (rows.isEmpty()) {
            String none = "No settings found";
            canvas.text(none, listX + (listWidth - canvas.textWidth(none)) / 2, listTop + 20, GREY, true);
        }
        if (maxScroll() > 0) {
            int barX = this.width - 7;
            canvas.rect(barX, listTop, 6, listBottom - listTop, 0xFF000000);
            canvas.rect(barX, knobY(), 6, knobHeight(), 0xFF808080);
            canvas.rect(barX, knobY(), 5, knobHeight() - 1, 0xFFC0C0C0);
        }
    }

    private static void resetIcon(Canvas canvas, int x, int y) {
        canvas.rect(x + 2, y, 5, 1, WHITE);
        canvas.rect(x + 1, y + 1, 1, 1, WHITE);
        canvas.rect(x + 7, y + 1, 1, 1, WHITE);
        canvas.rect(x, y + 2, 1, 4, WHITE);
        canvas.rect(x + 8, y + 2, 1, 2, WHITE);
        canvas.rect(x + 7, y + 4, 3, 1, WHITE);
        canvas.rect(x + 8, y + 5, 1, 1, WHITE);
        canvas.rect(x + 1, y + 6, 1, 1, WHITE);
        canvas.rect(x + 2, y + 7, 5, 1, WHITE);
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the world visible behind the preview
        if (this.minecraft.level == null) super.extractBackground(graphics, mouseX, mouseY, delta);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Keep the world visible behind the preview
        if (this.minecraft.level == null) super.renderBackground(graphics, mouseX, mouseY, delta);
    }
    *///?}

    @Override
    public void removed() {
        WidgetRenderer.setHudHidden(false);
    }

    @Override
    public void onClose() {
        config.save();
        Screens.open(parent);
    }

    /** Slider that shows a formatted value and snaps to a step. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final DoubleFunction<String> format;

        ValueSlider(int x, int width, int height, double min, double max, double step, DoubleSupplier get,
                    DoubleConsumer set, DoubleFunction<String> format) {
            super(x, 0, width, height, Component.empty(), (Math.max(min, Math.min(max, get.getAsDouble())) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.format = format;
            updateMessage();
        }

        /** Follows changes made elsewhere, such as the reset button or the visual editor. */
        void sync() {
            this.value = (Math.max(min, Math.min(max, get.getAsDouble())) - min) / (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(format.apply(get.getAsDouble())));
        }

        @Override
        protected void applyValue() {
            double raw = min + this.value * (max - min);
            double snapped = Math.round(raw / step) * step;
            set.accept(Math.max(min, Math.min(max, Math.round(snapped * 1000.0) / 1000.0)));
        }
    }
}
