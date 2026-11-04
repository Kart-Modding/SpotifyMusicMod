package dev.karton.spotifywidget.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.compat.Painter;
import dev.karton.spotifywidget.compat.Screens;
import dev.karton.spotifywidget.hud.WidgetRenderer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Visual editor, in the spirit of PaperDoll's: the widget sits over the game with guide lines, and
 * is dragged into place. The result is stored as the nearest corner plus offsets, so it stays put
 * when the window is resized.
 */
public class MoveScreen extends Screen {
    private static final int GUIDE = 0xA6FFFFFF;
    private static final int GUIDE_SNAPPED = 0xFFFFFF55;
    private static final int MARGIN = 10;
    private static final int SNAP = 4;
    private static final int GRAB_MARGIN = 3;
    private static final int BAR_HEIGHT = 38;
    private static final String HINT = "Drag to move. Scroll to resize. Arrows nudge. Shift turns off snapping.";

    private final Screen parent;
    private final HudConfig config = HudConfig.get();

    private Button reset;
    private Button done;
    private boolean dragging;
    private double grabX;
    private double grabY;
    private int snappedX = -1;
    private int snappedY = -1;

    public MoveScreen(Screen parent) {
        super(Component.literal("Move widget"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WidgetRenderer.setHudHidden(true);
        addRenderableOnly(new Painter(this::paint));
        reset = addRenderableWidget(Button.builder(Component.literal("Reset position"), button -> resetPosition())
                .bounds(this.width / 2 - 102, 0, 100, 20).build());
        done = addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 + 2, 0, 100, 20).build());
    }

    private void resetPosition() {
        HudConfig defaults = new HudConfig();
        config.corner = defaults.corner;
        config.offsetX = defaults.offsetX;
        config.offsetY = defaults.offsetY;
        config.scale = defaults.scale;
        config.hotbarShift = defaults.hotbarShift;
    }

    private int[] box() {
        return WidgetRenderer.box(this.width, this.height);
    }

    private void paint(Canvas canvas) {
        int centerX = this.width / 2;
        int centerY = this.height / 2 - 1;
        for (int y : new int[]{MARGIN, centerY, this.height - MARGIN}) {
            canvas.rect(0, y, this.width, 1, snappedY >= 0 && Math.abs(snappedY - y) < 2 ? GUIDE_SNAPPED : GUIDE);
        }
        for (int x : new int[]{MARGIN, centerX, this.width - MARGIN}) {
            canvas.rect(x, 0, 1, this.height, snappedX >= 0 && Math.abs(snappedX - x) < 2 ? GUIDE_SNAPPED : GUIDE);
        }

        WidgetRenderer.drawInPlace(canvas);

        int[] box = box();
        int edge = dragging ? 0xFFFFFFFF : 0x73FFFFFF;
        int x = box[0] - 1;
        int y = box[1] - 1;
        int boxWidth = box[2] - box[0] + 2;
        int boxHeight = box[3] - box[1] + 2;
        canvas.rect(x, y, boxWidth, 1, edge);
        canvas.rect(x, y + boxHeight - 1, boxWidth, 1, edge);
        canvas.rect(x, y, 1, boxHeight, edge);
        canvas.rect(x + boxWidth - 1, y, 1, boxHeight, edge);

        String label = placement(config);
        int labelWidth = canvas.textWidth(label) + 8;
        int labelX = Math.max(2, Math.min(x, this.width - labelWidth - 2));
        int labelY = y > 16 ? y - 13 : y + boxHeight + 2;
        canvas.rect(labelX, labelY, labelWidth, 12, 0xB3000000);
        canvas.text(label, labelX + 4, labelY + 2, 0xFFFFFFFF, true);

        reset.visible = !dragging;
        done.visible = !dragging;
        if (dragging) return;

        int barWidth = Math.min(this.width - 8, canvas.textWidth(HINT) + 16);
        int barX = (this.width - barWidth) / 2;
        boolean covered = box[1] < 56 && box[2] > barX && box[0] < barX + barWidth;
        int barY = covered ? centerY + 14 : 16;
        canvas.rect(barX, barY, barWidth, BAR_HEIGHT, 0xB8000000);
        String hint = canvas.trim(HINT, barWidth - 8);
        canvas.text(hint, barX + (barWidth - canvas.textWidth(hint)) / 2, barY + 4, 0xFFFFFFFF, true);
        reset.setY(barY + 15);
        done.setY(barY + 15);
    }

    static String placement(HudConfig config) {
        String scale = String.format(Locale.ROOT, "%.2fx", config.scale);
        if (config.layout == WidgetLayout.HOTBAR) {
            return "Above hotbar, X " + config.hotbarShift + ", Y " + config.offsetY + ", " + scale;
        }
        return config.corner.label() + ", X " + config.offsetX + ", Y " + config.offsetY + ", " + scale;
    }

    private void place(double x0, double y0, int boxWidth, int boxHeight) {
        double left = Math.max(0, Math.min(x0, this.width - boxWidth));
        double top = Math.max(0, Math.min(y0, this.height - boxHeight));
        float scale = config.scale;
        if (config.layout == WidgetLayout.HOTBAR) {
            // Anchored to the middle of the hotbar, not to a corner
            int[] box = box();
            config.hotbarShift = (int) Math.round(config.hotbarShift + (left - box[0]) / scale);
            config.offsetY = (int) Math.round(config.offsetY + (box[1] - top) / scale);
            return;
        }
        boolean onLeft = left + boxWidth / 2.0 < this.width / 2.0;
        boolean onTop = top + boxHeight / 2.0 < this.height / 2.0;
        config.corner = onTop
                ? (onLeft ? HudCorner.TOP_LEFT : HudCorner.TOP_RIGHT)
                : (onLeft ? HudCorner.BOTTOM_LEFT : HudCorner.BOTTOM_RIGHT);
        config.offsetX = Math.max(0, (int) Math.round((onLeft ? left : this.width - left - boxWidth) / scale));
        config.offsetY = Math.max(0, (int) Math.round((onTop ? top : this.height - top - boxHeight) / scale));
    }

    /** Pulls a position onto a guide. Returns {position, guide}, the guide being -1 when free. */
    private static double[] snap(double start, int size, int screen) {
        double[] lines = {MARGIN, screen / 2.0, screen - MARGIN};
        double[] edges = {0, size / 2.0, size};
        for (double line : lines) {
            for (double edge : edges) {
                if (Math.abs(start + edge - line) < SNAP) return new double[]{line - edge, line};
            }
        }
        return new double[]{start, -1};
    }

    private boolean grab(double mouseX, double mouseY) {
        int[] box = box();
        if (mouseX < box[0] - GRAB_MARGIN || mouseX >= box[2] + GRAB_MARGIN
                || mouseY < box[1] - GRAB_MARGIN || mouseY >= box[3] + GRAB_MARGIN) return false;
        dragging = true;
        grabX = mouseX - box[0];
        grabY = mouseY - box[1];
        return true;
    }

    private void drag(double mouseX, double mouseY, boolean freeMove) {
        int[] box = box();
        int boxWidth = box[2] - box[0];
        int boxHeight = box[3] - box[1];
        double x0 = mouseX - grabX;
        double y0 = mouseY - grabY;
        snappedX = -1;
        snappedY = -1;
        if (!freeMove) {
            double[] snapX = snap(x0, boxWidth, this.width);
            double[] snapY = snap(y0, boxHeight, this.height);
            x0 = snapX[0];
            y0 = snapY[0];
            snappedX = (int) snapX[1];
            snappedY = (int) snapY[1];
        }
        place(x0, y0, boxWidth, boxHeight);
    }

    private void release() {
        dragging = false;
        snappedX = -1;
        snappedY = -1;
    }

    private boolean nudge(int key) {
        int dx = 0;
        int dy = 0;
        if (key == InputConstants.KEY_LEFT) dx = -1;
        else if (key == InputConstants.KEY_RIGHT) dx = 1;
        else if (key == InputConstants.KEY_UP) dy = -1;
        else if (key == InputConstants.KEY_DOWN) dy = 1;
        else return false;
        int[] box = box();
        place(box[0] + dx, box[1] + dy, box[2] - box[0], box[3] - box[1]);
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        return event.button() == 0 && grab(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(event, deltaX, deltaY);
        drag(event.x(), event.y(), event.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return nudge(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return button == 0 && grab(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        drag(mouseX, mouseY, hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return nudge(key) || super.keyPressed(key, scanCode, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        int[] before = box();
        config.scale = Math.round(Math.max(0.5f, Math.min(2f, config.scale + (scrollY > 0 ? 0.05f : -0.05f))) * 100f) / 100f;
        int[] after = box();
        int boxWidth = after[2] - after[0];
        int boxHeight = after[3] - after[1];
        place((before[0] + before[2] - boxWidth) / 2.0, (before[1] + before[3] - boxHeight) / 2.0, boxWidth, boxHeight);
        return true;
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft.level == null) super.extractBackground(graphics, mouseX, mouseY, delta);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
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
        Screens.open(new ConfigScreen(parent));
    }
}
