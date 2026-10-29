package org.pepsoft.worldpainter.brushes;

import java.awt.geom.GeneralPath;

/**
 * The outline of the shape of a brush, for drawing on the map where it will paint: the edges between the blocks it
 * reaches (with some strength) and those it does not. The last outline is cached, since it is drawn on every move of
 * the mouse.
 */
public final class BrushOutline {
    private BrushOutline() {
        // Prevent instantiation
    }

    /**
     * The outline of a brush at a radius, relative to the centre of the brush, in blocks.
     */
    public static synchronized GeneralPath of(Brush brush, int radius) {
        if ((brush == lastBrush) && (radius == lastRadius) && (lastOutline != null)) {
            return lastOutline;
        }
        final Brush copy = brush.clone();
        copy.setRadius(radius);
        // A rotated brush can reach the corners of its square
        final int reach = (int) Math.ceil(radius * 1.42) + 1, size = 2 * reach + 1;
        final boolean[] inside = new boolean[size * size];
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                inside[(dy + reach) * size + dx + reach] = strength(copy, dx, dy) > THRESHOLD;
            }
        }
        final GeneralPath outline = new GeneralPath();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (! inside[y * size + x]) {
                    continue;
                }
                final float left = x - reach, top = y - reach;
                if ((x == 0) || (! inside[y * size + x - 1])) {
                    outline.moveTo(left, top);
                    outline.lineTo(left, top + 1);
                }
                if ((x == size - 1) || (! inside[y * size + x + 1])) {
                    outline.moveTo(left + 1, top);
                    outline.lineTo(left + 1, top + 1);
                }
                if ((y == 0) || (! inside[(y - 1) * size + x])) {
                    outline.moveTo(left, top);
                    outline.lineTo(left + 1, top);
                }
                if ((y == size - 1) || (! inside[(y + 1) * size + x])) {
                    outline.moveTo(left, top + 1);
                    outline.lineTo(left + 1, top + 1);
                }
            }
        }
        lastBrush = brush;
        lastRadius = radius;
        lastOutline = outline;
        return outline;
    }

    /**
     * The strength of a brush at an offset, or zero outside its square (where not all brushes check their bounds).
     */
    private static float strength(Brush brush, int dx, int dy) {
        try {
            return brush.getFullStrength(dx, dy);
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0.0f;
        }
    }

    private static Brush lastBrush;
    private static int lastRadius;
    private static GeneralPath lastOutline;

    /**
     * The strength above which a block counts as reached by the brush.
     */
    private static final float THRESHOLD = 0.1f;
}
