package dev.karton.spotifywidget.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Thin wrapper over the vanilla draw context. Everything that Mojang renamed or reshaped between
 * 1.21.1 and 26.2 is handled here, so the widget code itself stays version independent.
 */
public final class Canvas {
    //? if >=26.1 {
    private final GuiGraphicsExtractor ctx;

    public Canvas(GuiGraphicsExtractor ctx) {
        this.ctx = ctx;
        this.measuredWidth = 0;
        this.measuredHeight = 0;
    }
    //?} else {
    /*private final GuiGraphics ctx;

    public Canvas(GuiGraphics ctx) {
        this.ctx = ctx;
        this.measuredWidth = 0;
        this.measuredHeight = 0;
    }
    *///?}

    private final int measuredWidth;
    private final int measuredHeight;
    private final float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    private final Deque<float[]> stack = new ArrayDeque<>();
    private float originX;
    private float originY;
    private float scale = 1f;

    private Canvas(int width, int height) {
        this.ctx = null;
        this.measuredWidth = width;
        this.measuredHeight = height;
    }

    /** A canvas that draws nothing and only records the area touched, see {@link #bounds()}. */
    public static Canvas measure(int width, int height) {
        return new Canvas(width, height);
    }

    public boolean measuring() {
        return ctx == null;
    }

    /** Touched area in GUI pixels: x0, y0, x1, y1. */
    public int[] bounds() {
        return new int[]{Math.round(box[0]), Math.round(box[1]), Math.round(box[2]), Math.round(box[3])};
    }

    private void mark(float x, float y, float width, float height) {
        box[0] = Math.min(box[0], originX + x * scale);
        box[1] = Math.min(box[1], originY + y * scale);
        box[2] = Math.max(box[2], originX + (x + width) * scale);
        box[3] = Math.max(box[3], originY + (y + height) * scale);
    }

    private void save() {
        stack.push(new float[]{originX, originY, scale});
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    public int screenWidth() {
        return ctx == null ? measuredWidth : ctx.guiWidth();
    }

    public int screenHeight() {
        return ctx == null ? measuredHeight : ctx.guiHeight();
    }

    /** Real screen pixels per drawing unit at the current scale. */
    public float pixelScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale() * scale;
    }

    public void rect(int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0) return;
        if (ctx == null) {
            mark(x, y, width, height);
            return;
        }
        ctx.fill(x, y, x + width, y + height, argb);
    }

    public void text(String value, int x, int y, int argb, boolean shadow) {
        if (ctx == null) {
            mark(x, y, font().width(value), 8);
            return;
        }
        //? if >=26.1 {
        ctx.text(font(), value, x, y, argb, shadow);
        //?} else
        /*ctx.drawString(font(), value, x, y, argb, shadow);*/
    }

    /** Draws a square texture scaled into {@code width} x {@code height}. */
    public void texture(Identifier texture, int x, int y, int width, int height, int textureSize, float alpha) {
        texture(texture, x, y, width, height, textureSize, textureSize, alpha);
    }

    /** Draws a whole texture of any shape scaled into {@code width} x {@code height}. */
    public void texture(Identifier texture, int x, int y, int width, int height,
                        int textureWidth, int textureHeight, float alpha) {
        if (ctx == null) {
            mark(x, y, width, height);
            return;
        }
        int tint = tint(alpha);
        //? if >=1.21.6 {
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight, tint);
        //?} elif >=1.21.2 {
        /*ctx.blit(RenderType::guiTextured, texture, x, y, 0.0F, 0.0F,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight, tint);
        *///?} else {
        /*ctx.setColor(1.0F, 1.0F, 1.0F, alpha);
        ctx.blit(texture, x, y, width, height, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
        ctx.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        *///?}
    }

    public void push(float factor) {
        transform(0f, 0f, factor);
    }

    /** Scales so one drawing unit is one real screen pixel, for {@link Shapes}-style fine shapes. */
    public void pushPixels() {
        push(1f / pixelScale());
    }

    /** Moves by {@code x}, {@code y}, then scales. Undo with {@link #pop()}. */
    public void transform(float x, float y, float factor) {
        save();
        originX += x * scale;
        originY += y * scale;
        scale *= factor;
        if (ctx == null) return;
        //? if >=1.21.6 {
        ctx.pose().pushMatrix();
        if (x != 0f || y != 0f) ctx.pose().translate(x, y);
        if (factor != 1.0F) ctx.pose().scale(factor, factor);
        //?} else {
        /*ctx.pose().pushPose();
        if (x != 0f || y != 0f) ctx.pose().translate(x, y, 0f);
        if (factor != 1.0F) ctx.pose().scale(factor, factor, 1.0F);
        *///?}
    }

    /** Clips drawing to an area given in current drawing units. Undo with {@link #endClip()}. */
    public void clip(int x0, int y0, int x1, int y1) {
        if (ctx == null) return;
        //? if >=1.21.6 {
        ctx.enableScissor(x0, y0, x1, y1);
        //?} else {
        /*// Older versions ignore the pose here, so the area is moved by hand
        ctx.enableScissor(Math.round(originX + x0 * scale), Math.round(originY + y0 * scale),
                Math.round(originX + x1 * scale), Math.round(originY + y1 * scale));
        *///?}
    }

    public void endClip() {
        if (ctx == null) return;
        ctx.disableScissor();
    }

    public void pop() {
        float[] previous = stack.pop();
        originX = previous[0];
        originY = previous[1];
        scale = previous[2];
        if (ctx == null) return;
        //? if >=1.21.6 {
        ctx.pose().popMatrix();
        //?} else
        /*ctx.pose().popPose();*/
    }

    public int textWidth(String value) {
        return font().width(value);
    }

    /** Shortens a string with an ellipsis so it fits into {@code maxWidth}. */
    public String trim(String value, int maxWidth) {
        Font font = font();
        if (font.width(value) <= maxWidth) return value;
        int ellipsis = font.width("...");
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            int charWidth = font.width(String.valueOf(value.charAt(i)));
            if (width + charWidth + ellipsis > maxWidth) break;
            width += charWidth;
            builder.append(value.charAt(i));
        }
        return builder.append("...").toString();
    }

    /**
     * False in creative and spectator, where the game hides the health, hunger and experience
     * rows and slides the held item name 14 pixels down.
     */
    public static boolean statusBarsVisible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    /** True while any screen (inventory, chat, menus) is open. */
    public static boolean guiOpen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen() != null;
        //?} else
        /*return Minecraft.getInstance().screen != null;*/
    }

    private static int tint(float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (value << 24) | 0xFFFFFF;
    }
}
