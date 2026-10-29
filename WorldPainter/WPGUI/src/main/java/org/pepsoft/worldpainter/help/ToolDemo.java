package org.pepsoft.worldpainter.help;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * Small looping animations which show what a tool does, on a little patch of land drawn like the map: a brush raising
 * a hill, a bucket filling an area, water rising in a basin, and so on. Each frame is computed from scratch from the
 * time in the loop, so the animations need no state.
 */
public enum ToolDemo {
    RAISE, FLATTEN, SMOOTH, MOUNTAIN, PYRAMID, ROTATED_PYRAMID, PATH, PENCIL, SPRAY, FILL, EYEDROPPER, GLOBAL, TEXT,
    FLOOD_WATER, FLOOD_LAVA, SPONGE, SELECT, COPY, CLEAR_SELECTION, SPAWN;

    /**
     * Draw a frame of the animation.
     *
     * @param g2     Where to draw it.
     * @param width  The width of the frame in pixels.
     * @param height The height of the frame in pixels.
     * @param time   The time in the loop, from zero to one.
     */
    public void paint(Graphics2D g2, int width, int height, double time) {
        final Scene scene = new Scene();
        // The action happens in the first three quarters of the loop; then the result is held for a moment
        final double progress = Math.min(1, time / ACTION);
        final double[] cursor = act(scene, progress);
        final BufferedImage image = scene.render();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, 0, 0, width, height, null);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final double sx = (double) width / W, sy = (double) height / H;
        decorate(g2, scene, progress, sx, sy);
        if ((cursor != null) && (time < ACTION + 0.08)) {
            drawCursor(g2, cursor[0] * sx, cursor[1] * sy, cursor[2] * sx, (cursor.length > 3) && (cursor[3] > 0));
        }
        // Fade in at the start of the loop and out at its end
        final double fade = Math.max(smoothStep(0.06, 0.0, time), smoothStep(0.94, 1.0, time));
        if (fade > 0) {
            g2.setColor(new Color(0x0f, 0x11, 0x15, (int) Math.round(fade * 255)));
            g2.fillRect(0, 0, width, height);
        }
    }

    /**
     * Apply the effect of the tool up to a point in its action.
     *
     * @return The position and radius of the cursor (in cells), with a fourth element which is positive while it
     * clicks, or {@code null} if no cursor is shown.
     */
    private double[] act(Scene scene, double progress) {
        switch (this) {
            case RAISE: {
                final int steps = (int) Math.round(progress * SAMPLES);
                for (int i = 0; i < steps; i++) {
                    final double[] p = sweep((double) i / SAMPLES);
                    scene.bump(p[0], p[1], 9, 2.4);
                }
                final double[] p = sweep(progress);
                return new double[] {p[0], p[1], 9};
            }
            case FLATTEN: {
                scene.addHills(9);
                final float[] weight = new float[W * H];
                final int steps = (int) Math.round(progress * SAMPLES);
                for (int i = 0; i < steps; i++) {
                    final double[] p = sweep((double) i / SAMPLES);
                    scene.plateau(weight, p[0], p[1], 10);
                }
                for (int i = 0; i < weight.length; i++) {
                    scene.heights[i] += (66 - scene.heights[i]) * weight[i];
                }
                final double[] p = sweep(progress);
                return new double[] {p[0], p[1], 10};
            }
            case SMOOTH: {
                scene.addRoughness(4);
                final float[] weight = new float[W * H];
                final int steps = (int) Math.round(progress * SAMPLES);
                for (int i = 0; i < steps; i++) {
                    final double[] p = sweep((double) i / SAMPLES);
                    scene.plateau(weight, p[0], p[1], 11);
                }
                final float[] blurred = scene.blurred(3);
                for (int i = 0; i < weight.length; i++) {
                    scene.heights[i] += (blurred[i] - scene.heights[i]) * weight[i];
                }
                final double[] p = sweep(progress);
                return new double[] {p[0], p[1], 11};
            }
            case MOUNTAIN: {
                final double grow = smoothStep(0.0, 1.0, progress);
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        final double d = Math.hypot(x - W / 2.0, (y - H / 2.0) * 1.2) / 24;
                        final double ridges = 0.75 + 0.25 * Math.abs(Math.sin(Math.atan2(y - H / 2.0, x - W / 2.0) * 5 + d * 6));
                        scene.heights[y * W + x] += (float) (grow * 30 * Math.pow(Math.max(0, 1 - d), 1.4) * ridges);
                        if (scene.heights[y * W + x] > 86) {
                            scene.terrain[y * W + x] = Scene.STONE;
                        }
                        if (scene.heights[y * W + x] > 92) {
                            scene.terrain[y * W + x] = Scene.SNOW;
                        }
                    }
                }
                return new double[] {W / 2.0, H / 2.0, 24, (progress < 0.95) ? 1 : 0};
            }
            case PYRAMID:
            case ROTATED_PYRAMID: {
                final double grow = smoothStep(0.0, 1.0, progress);
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        final double dx = Math.abs(x - W / 2.0) / 20, dy = Math.abs(y - H / 2.0) / 20;
                        final double d = (this == PYRAMID) ? Math.max(dx, dy) : (dx + dy);
                        scene.heights[y * W + x] += (float) (grow * 22 * Math.max(0, 1 - d));
                    }
                }
                return new double[] {W / 2.0, H / 2.0, 20, (progress < 0.95) ? 1 : 0};
            }
            case PATH: {
                scene.addHills(6);
                final double[][] points = {{14, 40}, {48, 14}, {82, 36}, {112, 18}};
                final int clicked = (int) Math.min(points.length, Math.floor(progress / 0.12) + 1);
                final double draw = smoothStep(0.5, 0.95, progress);
                if (draw > 0) {
                    scene.road(points, draw);
                }
                scene.markers = new double[clicked][];
                System.arraycopy(points, 0, scene.markers, 0, clicked);
                final double[] last = points[Math.min(points.length - 1, clicked - 1)];
                return (progress < 0.5) ? new double[] {last[0], last[1], 2, ((progress / 0.12) % 1 < 0.3) ? 1 : 0} : null;
            }
            case PENCIL:
            case SPRAY: {
                final int steps = (int) Math.round(progress * SAMPLES);
                for (int i = 0; i < steps; i++) {
                    final double[] p = sweep((double) i / SAMPLES);
                    scene.paint(p[0], p[1], 7, Scene.SAND, this == SPRAY);
                }
                final double[] p = sweep(progress);
                return new double[] {p[0], p[1], 7};
            }
            case FILL: {
                scene.addHills(5);
                scene.fillBasin(64, 46, progress);
                return (progress < 0.35) ? new double[] {64, 46, 2, 1} : null;
            }
            case EYEDROPPER: {
                for (int y = 18; y < 42; y++) {
                    for (int x = 70; x < 104; x++) {
                        if (Math.hypot((x - 87) / 17.0, (y - 30) / 12.0) < 1) {
                            scene.terrain[y * W + x] = Scene.SAND;
                        }
                    }
                }
                scene.swatch = (progress > 0.6) ? Scene.SAND : Scene.GRASS;
                final double move = smoothStep(0.0, 0.55, progress);
                return new double[] {20 + (87 - 20) * move, 44 + (30 - 44) * move, 2, ((progress > 0.55) && (progress < 0.7)) ? 1 : 0};
            }
            case GLOBAL: {
                final double edge = progress * (W + 20) - 10;
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        if (x + Math.sin(y * 0.4) * 3 < edge) {
                            scene.terrain[y * W + x] = Scene.SAND;
                        }
                    }
                }
                return null;
            }
            case TEXT: {
                scene.text = "Village";
                scene.textProgress = progress;
                return new double[] {28, 30, 2, (progress < 0.15) ? 1 : 0};
            }
            case FLOOD_WATER:
            case FLOOD_LAVA: {
                scene.addBasin();
                scene.waterLevel = (float) (54 + 12 * smoothStep(0.15, 1.0, progress));
                scene.lava = this == FLOOD_LAVA;
                return (progress < 0.3) ? new double[] {64, 30, 2, 1} : null;
            }
            case SPONGE: {
                scene.waterLevel = 67;
                scene.dry = new boolean[W * H];
                final int steps = (int) Math.round(progress * SAMPLES);
                for (int i = 0; i < steps; i++) {
                    final double[] p = sweep((double) i / SAMPLES);
                    scene.sponge(p[0], p[1], 9);
                }
                final double[] p = sweep(progress);
                return new double[] {p[0], p[1], 9};
            }
            case SELECT:
            case COPY:
            case CLEAR_SELECTION: {
                scene.addHills(5);
                final double drag = smoothStep(0.0, 0.45, progress);
                scene.selection = new double[] {18, 12, 18 + 46 * drag, 12 + 30 * drag};
                if (this == COPY) {
                    final double move = smoothStep(0.5, 0.95, progress);
                    if (move > 0) {
                        scene.copyOffset = new double[] {52 * move, 6 * move};
                    }
                } else if (this == CLEAR_SELECTION) {
                    scene.selectionFade = smoothStep(0.6, 0.9, progress);
                }
                return (progress < 0.5) ? new double[] {18 + 46 * drag, 12 + 30 * drag, 2, 1} : null;
            }
            case SPAWN: {
                scene.addHills(5);
                scene.flag = smoothStep(0.2, 0.6, progress);
                return (progress < 0.25) ? new double[] {64, 32, 2, 1} : null;
            }
            default:
                throw new InternalError();
        }
    }

    private static void decorate(Graphics2D g2, Scene scene, double progress, double sx, double sy) {
        if (scene.markers != null) {
            g2.setStroke(new BasicStroke(1.5f));
            for (int i = 0; i < scene.markers.length; i++) {
                final double x = scene.markers[i][0] * sx, y = scene.markers[i][1] * sy;
                g2.setColor(ACCENT);
                g2.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
                g2.setColor(Color.WHITE);
                g2.draw(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
            }
        }
        if (scene.selection != null) {
            final double[] s = scene.selection;
            final int x1 = (int) Math.round(s[0] * sx), y1 = (int) Math.round(s[1] * sy), x2 = (int) Math.round(s[2] * sx), y2 = (int) Math.round(s[3] * sy);
            final int alpha = (int) Math.round(255 * (1 - scene.selectionFade));
            if (scene.copyOffset != null) {
                final int dx = (int) Math.round(scene.copyOffset[0] * sx), dy = (int) Math.round(scene.copyOffset[1] * sy);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillRect(x1 + dx, y1 + dy, x2 - x1, y2 - y1);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {4, 3}, 0));
                g2.drawRect(x1 + dx, y1 + dy, x2 - x1, y2 - y1);
            }
            g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), alpha / 6));
            g2.fillRect(x1, y1, x2 - x1, y2 - y1);
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {4, 3}, (float) (progress * 40)));
            g2.drawRect(x1, y1, x2 - x1, y2 - y1);
        }
        if (scene.text != null) {
            final String shown = scene.text.substring(0, (int) Math.round(scene.text.length() * Math.min(1, scene.textProgress * 1.4)));
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, (float) (11 * sy)));
            g2.setColor(new Color(0xfacc15));
            g2.drawString(shown, (float) (28 * sx), (float) (30 * sy));
        }
        if (scene.swatch >= 0) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect((int) (W * sx) - 34, 8, 26, 26, 8, 8);
            g2.setColor(new Color(Scene.COLOURS[scene.swatch]));
            g2.fillRoundRect((int) (W * sx) - 31, 11, 20, 20, 6, 6);
        }
        if (scene.flag > 0) {
            final double x = 64 * sx, y = 34 * sy, rise = scene.flag * 34;
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(Color.WHITE);
            g2.drawLine((int) x, (int) y, (int) x, (int) (y - rise));
            g2.setColor(ACCENT);
            g2.fillPolygon(new int[] {(int) x, (int) (x + 20 * scene.flag), (int) x}, new int[] {(int) (y - rise), (int) (y - rise + 6), (int) (y - rise + 12)}, 3);
        }
    }

    private static void drawCursor(Graphics2D g2, double x, double y, double radius, boolean clicking) {
        g2.setStroke(new BasicStroke(1.5f));
        if (radius > 3) {
            g2.setColor(new Color(255, 255, 255, 210));
            g2.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
            g2.setColor(new Color(0, 0, 0, 90));
            g2.draw(new Ellipse2D.Double(x - radius - 1.5, y - radius - 1.5, radius * 2 + 3, radius * 2 + 3));
        }
        if (clicking) {
            g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 160));
            g2.fill(new Ellipse2D.Double(x - 7, y - 7, 14, 14));
        }
        // An arrow pointer
        final Polygon arrow = new Polygon(new int[] {0, 0, 4, 7, 9, 6, 11}, new int[] {0, 15, 11, 17, 16, 10, 10}, 7);
        arrow.translate((int) Math.round(x), (int) Math.round(y));
        g2.setColor(Color.WHITE);
        g2.fill(arrow);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(arrow);
    }

    /**
     * The path of a brush stroke: a gentle wave across the scene.
     */
    private static double[] sweep(double s) {
        return new double[] {16 + (W - 32) * s, H / 2.0 + Math.sin(s * Math.PI * 2.2) * 11};
    }

    static double smoothStep(double edge0, double edge1, double x) {
        final double t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * The land of an animation: a height and a terrain type per cell, and a water level.
     */
    private static final class Scene {
        Scene() {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    heights[y * W + x] = (float) (64 + 1.5 * Math.sin(x * 0.11 + 1) * Math.cos(y * 0.15) + Math.sin(x * 0.05 + y * 0.07) * 1.5);
                }
            }
        }

        void bump(double cx, double cy, double radius, double amount) {
            forEachNear(cx, cy, radius * 1.6, (i, d) -> heights[i] += (float) (amount * Math.exp(-d * d / (radius * radius * 0.5))));
        }

        void plateau(float[] weight, double cx, double cy, double radius) {
            forEachNear(cx, cy, radius, (i, d) -> weight[i] = (float) Math.max(weight[i], smoothStep(radius, radius * 0.6, d)));
        }

        void paint(double cx, double cy, double radius, int type, boolean spray) {
            forEachNear(cx, cy, radius, (i, d) -> {
                if ((! spray) || (hash(i) < 0.35 * (1 - d / radius) + 0.05)) {
                    terrain[i] = type;
                }
            });
        }

        void sponge(double cx, double cy, double radius) {
            forEachNear(cx, cy, radius, (i, d) -> dry[i] = true);
        }

        void addHills(double amplitude) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    heights[y * W + x] += (float) (amplitude * (0.5 + 0.5 * Math.sin(x * 0.13 + 0.7) * Math.sin(y * 0.19 + 0.3)) + amplitude * 0.4 * Math.sin(x * 0.31 + y * 0.23));
                }
            }
        }

        void addRoughness(double amplitude) {
            for (int i = 0; i < heights.length; i++) {
                heights[i] += (float) ((hash(i) - 0.5) * amplitude + (hash(i / 3 + 7919) - 0.5) * amplitude);
            }
        }

        void addBasin() {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    final double d = Math.hypot((x - W / 2.0) / 46, (y - H / 2.0) / 22);
                    heights[y * W + x] = (float) (54 + 16 * smoothStep(0.2, 1.1, d) + Math.sin(x * 0.3 + y * 0.2));
                }
            }
        }

        /**
         * A fill which spreads from a point, over the low ground of a valley only.
         */
        void fillBasin(double cx, double cy, double progress) {
            final double reach = smoothStep(0.15, 1.0, progress) * 90;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    final double valley = Math.abs(y - (H / 2.0 + Math.sin(x * 0.06) * 10)) / 13;
                    if ((valley < 1) && (Math.hypot(x - cx, (y - cy) * 2) < reach)) {
                        terrain[y * W + x] = STONE;
                    }
                }
            }
        }

        /**
         * A road of gravel along a polyline, drawn up to a fraction of its length, and levelled under it.
         */
        void road(double[][] points, double fraction) {
            double total = 0;
            for (int i = 1; i < points.length; i++) {
                total += Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
            }
            double remaining = total * fraction;
            for (int i = 1; (i < points.length) && (remaining > 0); i++) {
                final double length = Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
                final double part = Math.min(1, remaining / length);
                for (double s = 0; s <= part; s += 0.5 / length) {
                    final double x = points[i - 1][0] + (points[i][0] - points[i - 1][0]) * s, y = points[i - 1][1] + (points[i][1] - points[i - 1][1]) * s;
                    forEachNear(x, y, 2.6, (index, d) -> {
                        terrain[index] = GRAVEL;
                        heights[index] += (66 - heights[index]) * 0.5f;
                    });
                }
                remaining -= length;
            }
        }

        float[] blurred(int radius) {
            final float[] result = new float[heights.length];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float sum = 0;
                    int count = 0;
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            final int nx = x + dx, ny = y + dy;
                            if ((nx >= 0) && (ny >= 0) && (nx < W) && (ny < H)) {
                                sum += heights[ny * W + nx];
                                count++;
                            }
                        }
                    }
                    result[y * W + x] = sum / count;
                }
            }
            return result;
        }

        BufferedImage render() {
            final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    final int i = y * W + x;
                    final float h = heights[i];
                    final float west = heights[y * W + Math.max(0, x - 1)], east = heights[y * W + Math.min(W - 1, x + 1)];
                    final float north = heights[Math.max(0, y - 1) * W + x], south = heights[Math.min(H - 1, y + 1) * W + x];
                    final double shade = Math.max(0.45, Math.min(1.5, 1 + (east - west + south - north) * 0.09));
                    int colour = COLOURS[terrain[i]];
                    final boolean flooded = (waterLevel > h) && ((dry == null) || (! dry[i]));
                    if (flooded) {
                        final double depth = Math.min(1, (waterLevel - h) / 10);
                        colour = mix(colour, lava ? 0xe8601c : 0x2f6fd8, lava ? 0.95 : (0.55 + 0.4 * depth));
                    }
                    int r = (int) Math.min(255, ((colour >> 16) & 0xff) * (flooded ? 1 : shade));
                    int g = (int) Math.min(255, ((colour >> 8) & 0xff) * (flooded ? 1 : shade));
                    int b = (int) Math.min(255, (colour & 0xff) * (flooded ? 1 : shade));
                    // Contour lines, as on the map
                    if ((! flooded) && (Math.floor(h / 4) != Math.floor(Math.min(Math.min(west, east), Math.min(north, south)) / 4)) && (Math.floor(h / 4) > Math.floor(Math.min(Math.min(west, east), Math.min(north, south)) / 4))) {
                        r = r * 3 / 4;
                        g = g * 3 / 4;
                        b = b * 3 / 4;
                    }
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return image;
        }

        private void forEachNear(double cx, double cy, double radius, CellAction action) {
            final int x1 = Math.max(0, (int) Math.floor(cx - radius)), x2 = Math.min(W - 1, (int) Math.ceil(cx + radius));
            final int y1 = Math.max(0, (int) Math.floor(cy - radius)), y2 = Math.min(H - 1, (int) Math.ceil(cy + radius));
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    final double d = Math.hypot(x - cx, y - cy);
                    if (d <= radius) {
                        action.apply(y * W + x, d);
                    }
                }
            }
        }

        private static double hash(int i) {
            long h = i * 0x9E3779B97F4A7C15L;
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h ^= h >>> 27;
            return (h >>> 40) / (double) (1L << 24);
        }

        private static int mix(int a, int b, double t) {
            final int r = (int) Math.round(((a >> 16) & 0xff) * (1 - t) + ((b >> 16) & 0xff) * t);
            final int g = (int) Math.round(((a >> 8) & 0xff) * (1 - t) + ((b >> 8) & 0xff) * t);
            final int bl = (int) Math.round((a & 0xff) * (1 - t) + (b & 0xff) * t);
            return (r << 16) | (g << 8) | bl;
        }

        final float[] heights = new float[W * H];
        final int[] terrain = new int[W * H];
        float waterLevel = Float.NEGATIVE_INFINITY;
        boolean lava;
        boolean[] dry;
        double[][] markers;
        double[] selection, copyOffset;
        double selectionFade, textProgress, flag;
        String text;
        int swatch = -1;

        static final int GRASS = 0, SAND = 1, STONE = 2, SNOW = 3, GRAVEL = 4;
        static final int[] COLOURS = {0x5f9e3a, 0xe2d39a, 0x8a8a8a, 0xf2f4f6, 0x9a8f80};
    }

    private interface CellAction {
        void apply(int index, double distance);
    }

    /**
     * The size of the scene in cells.
     */
    private static final int W = 128, H = 64;
    private static final int SAMPLES = 48;
    private static final double ACTION = 0.78;
    private static final Color ACCENT = new Color(0xf97316);
}
