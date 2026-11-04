package dev.karton.spotifywidget.hud;

import dev.karton.spotifywidget.compat.Canvas;
import dev.karton.spotifywidget.config.HudConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rounded shapes built from plain fills, so they work on every game version.
 * With round corners on, shapes are drawn at real screen pixels and the corner edge pixels get
 * partial alpha. With them off, shapes stay on the GUI grid and show Minecraft pixel steps.
 */
public final class Shapes {
    private static final Map<String, int[]> MASKS = new HashMap<>();
    private static final int MASK_LIMIT = 96;

    private Shapes() {
    }

    /** Round mode uses a slightly bigger radius, and slim shapes become full pills. */
    private static float radius(int radius, int height) {
        if (radius == 0 && height > 6) return 0;
        if (height <= 6) return height / 2f;
        return Math.min(radius + 2f, height / 2f);
    }

    public static void fill(Canvas canvas, int x, int y, int width, int height, int radius, int argb) {
        fill(canvas, x, y, width, height, radius, argb, argb);
    }

    /** Filled shape with a left to right colour blend. */
    public static void fill(Canvas canvas, int x, int y, int width, int height, int radius, int start, int end) {
        part(canvas, x, y, width, height, radius, x, y, x + width, y + height, start, end, x, width);
    }

    /**
     * Only the part of a rounded shape inside the given window, so a bar can follow the widget's
     * own corners. The colour blend runs across {@code blendWidth} from {@code blendX}.
     */
    public static void part(Canvas canvas, int x, int y, int width, int height, int radius,
                            int windowX0, int windowY0, int windowX1, int windowY1,
                            int start, int end, int blendX, int blendWidth) {
        if (width <= 0 || height <= 0) return;
        if (canvas.measuring()) {
            canvas.rect(Math.max(x, windowX0), Math.max(y, windowY0),
                    Math.min(x + width, windowX1) - Math.max(x, windowX0),
                    Math.min(y + height, windowY1) - Math.max(y, windowY0), start);
            return;
        }
        if (!HudConfig.get().roundCorners) {
            for (int row = Math.max(0, windowY0 - y); row < height && y + row < windowY1; row++) {
                int inset = pixelInset(row, height, radius);
                int from = Math.max(x + inset, windowX0);
                int to = Math.min(x + width - inset, windowX1);
                span(canvas, from, y + row, to - from, 1, start, end, blendX, blendWidth, 1);
            }
            return;
        }
        float scale = canvas.pixelScale();
        int px = Math.round(x * scale);
        int py = Math.round(y * scale);
        int pw = Math.round(width * scale);
        int ph = Math.round(height * scale);
        int[] mask = mask(pw, ph, Math.round(radius(radius, height) * scale), 0);
        replay(canvas, mask, px, py, Math.round(windowX0 * scale), Math.round(windowY0 * scale),
                Math.round(windowX1 * scale), Math.round(windowY1 * scale),
                start, end, Math.round(blendX * scale), Math.round(blendWidth * scale), scale);
    }

    /** A one GUI pixel line around the shape. */
    public static void outline(Canvas canvas, int x, int y, int width, int height, int radius, int argb) {
        if (canvas.measuring()) return;
        if (!HudConfig.get().roundCorners) {
            int previous = -1;
            for (int row = 0; row < height; row++) {
                int inset = pixelInset(row, height, radius);
                if (row == 0 || row == height - 1) {
                    canvas.rect(x + inset, y + row, width - inset * 2, 1, argb);
                } else {
                    canvas.rect(x + inset, y + row, 1, 1, argb);
                    canvas.rect(x + width - inset - 1, y + row, 1, 1, argb);
                    if (previous >= 0 && previous != inset) {
                        canvas.rect(x + inset, y + row, previous - inset, 1, argb);
                        canvas.rect(x + width - previous, y + row, previous - inset, 1, argb);
                    }
                }
                previous = inset;
            }
            return;
        }
        float scale = canvas.pixelScale();
        int px = Math.round(x * scale);
        int py = Math.round(y * scale);
        int pw = Math.round(width * scale);
        int ph = Math.round(height * scale);
        int[] mask = mask(pw, ph, Math.round(radius(radius, height) * scale), Math.max(1, Math.round(scale)));
        replay(canvas, mask, px, py, px, py, px + pw, py + ph, argb, argb, px, pw, scale);
    }

    private static void replay(Canvas canvas, int[] mask, int originX, int originY,
                               int windowX0, int windowY0, int windowX1, int windowY1,
                               int start, int end, int blendX, int blendWidth, float scale) {
        canvas.pushPixels();
        for (int i = 0; i < mask.length; i += 5) {
            int runX = originX + mask[i];
            int runY = originY + mask[i + 1];
            int from = Math.max(runX, windowX0);
            int to = Math.min(runX + mask[i + 2], windowX1);
            int top = Math.max(runY, windowY0);
            int bottom = Math.min(runY + mask[i + 3], windowY1);
            if (to <= from || bottom <= top) continue;
            int coverage = mask[i + 4];
            span(canvas, from, top, to - from, bottom - top, withCoverage(start, coverage), withCoverage(end, coverage),
                    blendX, blendWidth, Math.max(1, Math.round(scale)));
        }
        canvas.pop();
    }

    private static void span(Canvas canvas, int x, int y, int width, int height,
                             int start, int end, int blendX, int blendWidth, int step) {
        if (width <= 0 || height <= 0) return;
        if (start == end) {
            canvas.rect(x, y, width, height, start);
            return;
        }
        for (int offset = 0; offset < width; offset += step) {
            int column = x + offset;
            float amount = blendWidth <= 0 ? 0f : Math.max(0f, Math.min(1f, (column - blendX) / (float) blendWidth));
            int color = (start & 0xFF000000) | Theme.mix(start & 0xFFFFFF, end & 0xFFFFFF, amount);
            canvas.rect(column, y, Math.min(step, width - offset), height, color);
        }
    }

    private static int withCoverage(int argb, int coverage) {
        int alpha = Math.round((argb >>> 24) * coverage / 255f);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Runs of equal coverage for a shape: x, y, width, height, coverage. Cached by size. */
    private static int[] mask(int width, int height, int radius, int ring) {
        String key = width + ":" + height + ":" + radius + ":" + ring;
        int[] cached = MASKS.get(key);
        if (cached != null) return cached;
        if (MASKS.size() >= MASK_LIMIT) MASKS.clear();

        List<int[]> runs = new ArrayList<>();
        int[] previous = null;
        List<Integer> open = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            int[] line = rowRuns(row, width, height, radius, ring);
            if (previous != null && java.util.Arrays.equals(previous, line)) {
                for (int index : open) runs.get(index)[3]++;
                continue;
            }
            open.clear();
            for (int i = 0; i < line.length; i += 3) {
                open.add(runs.size());
                runs.add(new int[]{line[i], row, line[i + 1], 1, line[i + 2]});
            }
            previous = line;
        }
        int[] flat = new int[runs.size() * 5];
        for (int i = 0; i < runs.size(); i++) System.arraycopy(runs.get(i), 0, flat, i * 5, 5);
        MASKS.put(key, flat);
        return flat;
    }

    private static int[] rowRuns(int row, int width, int height, int radius, int ring) {
        int middle = Math.max(radius, ring);
        int[] coverage = new int[width];
        int filled = 0;
        for (int x = 0; x < width; x++) {
            if (x == middle && width - middle * 2 > 0) {
                int value = coverage(x, row, width, height, radius, ring);
                java.util.Arrays.fill(coverage, x, width - middle, value);
                x = width - middle - 1;
                continue;
            }
            coverage[x] = coverage(x, row, width, height, radius, ring);
        }
        int[] runs = new int[width * 3];
        for (int x = 0; x < width; ) {
            int value = coverage[x];
            int end = x + 1;
            while (end < width && coverage[end] == value) end++;
            if (value > 0) {
                runs[filled++] = x;
                runs[filled++] = end - x;
                runs[filled++] = value;
            }
            x = end;
        }
        return java.util.Arrays.copyOf(runs, filled);
    }

    private static int coverage(int x, int y, int width, int height, int radius, int ring) {
        float value = shape(x, y, 0, 0, width, height, radius);
        if (ring > 0) {
            value *= 1f - shape(x, y, ring, ring, width - ring * 2, height - ring * 2, Math.max(0, radius - ring));
        }
        return Math.round(value * 15f) * 17;
    }

    /** 1 inside a rounded rectangle, 0 outside, fractional on the curve. */
    private static float shape(int px, int py, int x, int y, int width, int height, int radius) {
        if (width <= 0 || height <= 0) return 0f;
        if (px < x || py < y || px >= x + width || py >= y + height) return 0f;
        if (radius <= 0) return 1f;
        float cx = px + 0.5f - x;
        float cy = py + 0.5f - y;
        float dx = Math.max(Math.max(radius - cx, cx - (width - radius)), 0f);
        float dy = Math.max(Math.max(radius - cy, cy - (height - radius)), 0f);
        if (dx == 0f || dy == 0f) return 1f;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return Math.max(0f, Math.min(1f, radius + 0.5f - distance));
    }

    private static int pixelInset(int row, int height, int radius) {
        if (radius <= 0) return 0;
        double distance = 0;
        if (row < radius) distance = (radius - 0.5) - row;
        else if (row >= height - radius) distance = row - (height - radius - 0.5);
        if (distance <= 0) return 0;
        double inner = radius * radius - distance * distance;
        if (inner <= 0) return radius;
        return (int) Math.round(radius - Math.sqrt(inner));
    }
}
