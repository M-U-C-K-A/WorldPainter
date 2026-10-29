package org.pepsoft.worldpainter.help;

import org.pepsoft.worldpainter.brushes.Brush;

import java.awt.*;

/**
 * A three dimensional preview of the shape of a brush, for its card: the land the brush would raise, drawn on a block
 * of ground in an isometric view which slowly turns from side to side, so that the shape is easy to make out.
 */
public final class BrushPreview implements ToolCard.Preview {
    public BrushPreview(Brush brush) {
        final Brush copy = brush.clone();
        copy.setRadius(RADIUS);
        copy.setLevel(1.0f);
        heights = new float[GRID * GRID];
        float max = 0;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                final int dx = Math.round((x + 0.5f) * (2 * RADIUS + 1) / GRID - RADIUS - 0.5f), dy = Math.round((y + 0.5f) * (2 * RADIUS + 1) / GRID - RADIUS - 0.5f);
                final float strength = copy.getFullStrength(dx, dy);
                heights[y * GRID + x] = strength;
                max = Math.max(max, strength);
            }
        }
        if (max > 0) {
            for (int i = 0; i < heights.length; i++) {
                heights[i] /= max;
            }
        }
    }

    @Override
    public void paint(Graphics2D g2, int width, int height, double time) {
        g2.setColor(BACKGROUND);
        g2.fillRect(0, 0, width, height);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Turn gently from side to side around a three quarter view
        final double yaw = Math.toRadians(45 + 30 * Math.sin(time * Math.PI * 2));
        final double cos = Math.cos(yaw), sin = Math.sin(yaw);
        final double scale = Math.min(width / 2.9, height / 1.75), cx = width / 2.0, cy = height * 0.5;
        final double tilt = 0.5, relief = 0.42, base = 0.16;
        // The cells from the back to the front, so that nearer ones are drawn over farther ones
        final Integer[] order = new Integer[GRID * GRID];
        final double[] depth = new double[GRID * GRID];
        for (int i = 0; i < order.length; i++) {
            final double u = (i % GRID + 0.5) / GRID * 2 - 1, v = (i / GRID + 0.5) / GRID * 2 - 1;
            depth[i] = u * sin + v * cos;
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(depth[a], depth[b]));
        final int[] xs = new int[4], ys = new int[4];
        for (int i: order) {
            final int gx = i % GRID, gy = i / GRID;
            final double u0 = (double) gx / GRID * 2 - 1, v0 = (double) gy / GRID * 2 - 1, step = 2.0 / GRID;
            final double h = heights[i] * relief;
            // The top of the cell
            project(u0, v0, h, cos, sin, scale, cx, cy, tilt, xs, ys, 0);
            project(u0 + step, v0, h, cos, sin, scale, cx, cy, tilt, xs, ys, 1);
            project(u0 + step, v0 + step, h, cos, sin, scale, cx, cy, tilt, xs, ys, 2);
            project(u0, v0 + step, h, cos, sin, scale, cx, cy, tilt, xs, ys, 3);
            // Light from the upper left of the view, turning with the block
            final float east = heights[gy * GRID + Math.min(GRID - 1, gx + 1)] - heights[gy * GRID + Math.max(0, gx - 1)];
            final float south = heights[Math.min(GRID - 1, gy + 1) * GRID + gx] - heights[Math.max(0, gy - 1) * GRID + gx];
            final double nx = -east * relief * GRID / 4, ny = -south * relief * GRID / 4;
            final double lx = -0.55 * cos - 0.45 * sin, ly = 0.55 * sin - 0.45 * cos;
            final double light = Math.max(0.25, Math.min(1.15, 0.72 + (nx * lx + ny * ly) * 0.9 + heights[i] * 0.12));
            g2.setColor(shade(SURFACE, light));
            g2.fillPolygon(xs, ys, 4);
            g2.drawPolygon(xs, ys, 4);
            // Steps down to lower neighbours in front, so that steep slopes have no gaps
            drawSteps(g2, gx, gy, u0, v0, step, h, relief, cos, sin, scale, cx, cy, tilt);
            // The sides of the block of ground, where the cell is on an edge which faces the viewer
            drawSide(g2, gx, gy, u0, v0, step, h, cos, sin, scale, cx, cy, tilt, base);
        }
    }

    private void drawSteps(Graphics2D g2, int gx, int gy, double u0, double v0, double step, double h, double relief, double cos, double sin, double scale, double cx, double cy, double tilt) {
        final int[] xs = new int[4], ys = new int[4];
        final double[][] edges = {
                {0, 1, u0, v0 + step, u0 + step, v0 + step},
                {0, -1, u0, v0, u0 + step, v0},
                {1, 0, u0 + step, v0, u0 + step, v0 + step},
                {-1, 0, u0, v0, u0, v0 + step}};
        for (double[] edge: edges) {
            final int nx = gx + (int) edge[0], ny = gy + (int) edge[1];
            if ((nx < 0) || (ny < 0) || (nx >= GRID) || (ny >= GRID) || (edge[0] * sin + edge[1] * cos <= 0)) {
                continue;
            }
            final double lower = heights[ny * GRID + nx] * relief;
            if (h - lower < 0.002) {
                continue;
            }
            project(edge[2], edge[3], h, cos, sin, scale, cx, cy, tilt, xs, ys, 0);
            project(edge[4], edge[5], h, cos, sin, scale, cx, cy, tilt, xs, ys, 1);
            project(edge[4], edge[5], lower, cos, sin, scale, cx, cy, tilt, xs, ys, 2);
            project(edge[2], edge[3], lower, cos, sin, scale, cx, cy, tilt, xs, ys, 3);
            final double light = 0.5 + 0.3 * Math.abs(edge[0] * cos - edge[1] * sin);
            g2.setColor(shade(SURFACE, light));
            g2.fillPolygon(xs, ys, 4);
            g2.drawPolygon(xs, ys, 4);
        }
    }

    private void drawSide(Graphics2D g2, int gx, int gy, double u0, double v0, double step, double h, double cos, double sin, double scale, double cx, double cy, double tilt, double base) {
        final int[] xs = new int[4], ys = new int[4];
        // Each edge of the grid, with its outward direction; it is drawn if that direction points towards the viewer
        final double[][] edges = {
                {gy == GRID - 1 ? 1 : 0, u0, v0 + step, u0 + step, v0 + step, 0, 1},
                {gy == 0 ? 1 : 0, u0, v0, u0 + step, v0, 0, -1},
                {gx == GRID - 1 ? 1 : 0, u0 + step, v0, u0 + step, v0 + step, 1, 0},
                {gx == 0 ? 1 : 0, u0, v0, u0, v0 + step, -1, 0}};
        for (double[] edge: edges) {
            if (edge[0] == 0) {
                continue;
            }
            final double facing = edge[5] * sin + edge[6] * cos;
            if (facing <= 0) {
                continue;
            }
            project(edge[1], edge[2], h, cos, sin, scale, cx, cy, tilt, xs, ys, 0);
            project(edge[3], edge[4], h, cos, sin, scale, cx, cy, tilt, xs, ys, 1);
            project(edge[3], edge[4], -base, cos, sin, scale, cx, cy, tilt, xs, ys, 2);
            project(edge[1], edge[2], -base, cos, sin, scale, cx, cy, tilt, xs, ys, 3);
            final double light = 0.45 + 0.3 * Math.abs(edge[5] * cos - edge[6] * sin);
            g2.setColor(shade(SIDE, light));
            g2.fillPolygon(xs, ys, 4);
            g2.drawPolygon(xs, ys, 4);
        }
    }

    private static void project(double u, double v, double h, double cos, double sin, double scale, double cx, double cy, double tilt, int[] xs, int[] ys, int i) {
        final double x = u * cos - v * sin, depth = u * sin + v * cos;
        xs[i] = (int) Math.round(cx + x * scale);
        ys[i] = (int) Math.round(cy + depth * scale * tilt - h * scale);
    }

    private static Color shade(Color colour, double amount) {
        return new Color((int) Math.min(255, colour.getRed() * amount), (int) Math.min(255, colour.getGreen() * amount), (int) Math.min(255, colour.getBlue() * amount));
    }

    private final float[] heights;

    private static final int GRID = 52, RADIUS = 60;
    private static final Color BACKGROUND = new Color(0x18181b), SURFACE = new Color(0xc8c8c8), SIDE = new Color(0xb0b0b0);
}
