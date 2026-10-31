package org.pepsoft.worldpainter.help;

import org.pepsoft.worldpainter.brushes.Brush;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A three dimensional preview of the shape of a brush, for its card: the land the brush would raise, drawn on a block
 * of ground in an isometric view which slowly turns from side to side, so that the shape is easy to make out.
 *
 * <p>The land is a smoothly shaded mesh, rasterised in software and supersampled, on a thread of its own, so that the
 * user interface never waits for it. Call {@link #prepare()} when a card is about to be shown (when the mouse enters
 * the button) to have the first frame ready by the time it appears.
 */
public final class BrushPreview implements ToolCard.Preview {
    public BrushPreview(Brush brush) {
        // A copy, since the brush itself is changed by the user interface while this is being worked out
        this.brush = brush.clone();
    }

    /**
     * Work out the land and the first frame in the background, if that has not been done yet.
     */
    public void prepare() {
        request(0.0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public void paint(Graphics2D g2, int width, int height, double time) {
        final double scale = g2.getTransform().getScaleX();
        final int frameWidth = (int) Math.round(width * scale), frameHeight = (int) Math.round(height * scale);
        Frame frame = Renderer.current;
        if (! frame.isFor(this, frameWidth, frameHeight)) {
            // Nothing to show yet, so wait for it for a moment; a blank frame is better than a frozen interface though
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FIRST_FRAME_TIMEOUT);
            try {
                // Twice, since the renderer may still have been busy with something else the first time
                for (int attempt = 0; (attempt < 2) && (! frame.isFor(this, frameWidth, frameHeight)); attempt++) {
                    request(time, frameWidth, frameHeight).get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                    frame = Renderer.current;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                // Show what there is
                frame = Renderer.current;
            }
        }
        if (frame.isFor(this, frameWidth, frameHeight)) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(frame.image, 0, 0, width, height, null);
        } else {
            g2.setColor(new Color(BACKGROUND));
            g2.fillRect(0, 0, width, height);
        }
        // Only after drawing the frame, since the next one is rendered into the image the previous one was in
        request(time, frameWidth, frameHeight);
    }

    private Future<?> request(double time, int width, int height) {
        // At most one frame at a time: if the renderer can't keep up the animation just has fewer frames
        if (Renderer.busy.compareAndSet(false, true)) {
            Renderer.pending = Renderer.EXECUTOR.submit(() -> {
                try {
                    prepareLand();
                    Renderer.render(this, time, width, height);
                } finally {
                    Renderer.busy.set(false);
                }
            });
        }
        return Renderer.pending;
    }

    /**
     * Sample the brush and work out how much light each point of the land gets. Only called on the renderer thread.
     */
    private void prepareLand() {
        if (heights != null) {
            return;
        }
        brush.setRadius(GRID / 2);
        brush.setLevel(1.0f);
        final float[] land = new float[VERTICES * VERTICES];
        float max = 0;
        for (int y = 0; y < VERTICES; y++) {
            for (int x = 0; x < VERTICES; x++) {
                final float strength = brush.getFullStrength(x - GRID / 2, y - GRID / 2);
                land[y * VERTICES + x] = strength;
                max = Math.max(max, strength);
            }
        }
        if (max > 0) {
            for (int i = 0; i < land.length; i++) {
                land[i] /= max;
            }
        }
        // Bitmap brushes only have 256 levels, which would show as terraces in the shading
        final float[] smooth = blur(land, 2);
        // How far a point lies below or above its surroundings, for darker hollows and brighter ridges
        final float[] surroundings = blur(smooth, 10);
        final byte[] light = new byte[VERTICES * VERTICES];
        final double length = Math.sqrt(LIGHT_U * LIGHT_U + LIGHT_V * LIGHT_V + LIGHT_UP * LIGHT_UP);
        final double cellSize = 2.0 / GRID;
        for (int y = 0; y < VERTICES; y++) {
            for (int x = 0; x < VERTICES; x++) {
                final int i = y * VERTICES + x;
                final double slopeU = (smooth[y * VERTICES + Math.min(GRID, x + 1)] - smooth[y * VERTICES + Math.max(0, x - 1)]) * RELIEF / (2 * cellSize);
                final double slopeV = (smooth[Math.min(GRID, y + 1) * VERTICES + x] - smooth[Math.max(0, y - 1) * VERTICES + x]) * RELIEF / (2 * cellSize);
                final double normalLength = Math.sqrt(slopeU * slopeU + slopeV * slopeV + 1);
                final double diffuse = Math.max(0, (-slopeU * LIGHT_U - slopeV * LIGHT_V + LIGHT_UP) / (normalLength * length));
                final double hollow = Math.max(-0.3, Math.min(0.15, (smooth[i] - surroundings[i]) * 2.5));
                final double brightness = (0.3 + 0.7 * diffuse + 0.06 * smooth[i]) * (1 + hollow);
                light[i] = (byte) Math.max(0, Math.min(255, Math.round(brightness * 255)));
            }
        }
        // How far the land reaches above and below the centre of the view as it turns, to keep all of it in the frame
        float top = 0, bottom = 0;
        for (double yaw = 15; yaw <= 75; yaw += 15) {
            final float cos = (float) Math.cos(Math.toRadians(yaw)), sin = (float) Math.sin(Math.toRadians(yaw));
            for (int i = 0; i < smooth.length; i++) {
                final float u = (float) ((i % VERTICES) * cellSize - 1), v = (float) ((i / VERTICES) * cellSize - 1), depth = (u * sin + v * cos) * TILT;
                top = Math.max(top, smooth[i] * RELIEF - depth);
                bottom = Math.max(bottom, depth + BASE);
            }
        }
        reachUp = top;
        reachDown = bottom;
        lights = light;
        heights = smooth;
    }

    private static float[] blur(float[] values, int passes) {
        float[] from = values.clone();
        float[] to = new float[from.length];
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < VERTICES; y++) {
                for (int x = 0; x < VERTICES; x++) {
                    float sum = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        final int row = Math.max(0, Math.min(GRID, y + dy)) * VERTICES;
                        for (int dx = -1; dx <= 1; dx++) {
                            sum += from[row + Math.max(0, Math.min(GRID, x + dx))];
                        }
                    }
                    to[y * VERTICES + x] = sum / 9;
                }
            }
            final float[] swap = from;
            from = to;
            to = swap;
        }
        return from;
    }

    private final Brush brush;
    private float[] heights;
    private byte[] lights;
    private float reachUp, reachDown;

    private static final int GRID = 128, VERTICES = GRID + 1;
    private static final int DEFAULT_WIDTH = 520, DEFAULT_HEIGHT = 280;
    private static final int BACKGROUND = 0x18181b;
    private static final long FIRST_FRAME_TIMEOUT = 40;
    private static final float RELIEF = 0.42f, BASE = 0.16f, TILT = 0.5f;
    private static final double LIGHT_U = -0.25, LIGHT_V = 0.55, LIGHT_UP = 0.8;

    /**
     * An image of a frame, and what it is a frame of.
     */
    private static final class Frame {
        Frame(BrushPreview owner, BufferedImage image) {
            this.owner = owner;
            this.image = image;
        }

        boolean isFor(BrushPreview preview, int width, int height) {
            return (owner == preview) && (image.getWidth() == width) && (image.getHeight() == height);
        }

        final BrushPreview owner;
        final BufferedImage image;
    }

    /**
     * Renders frames, one at a time, on a thread of its own. All its buffers are shared, since only one card can be
     * showing at a time.
     */
    private static final class Renderer {
        private Renderer() {
            // Prevent instantiation
        }

        static void render(BrushPreview preview, double time, int width, int height) {
            // Fewer samples on high density screens, where the individual pixels are harder to see anyway
            final int samples = (height > DEFAULT_HEIGHT / 2 + 1) ? 2 : 3;
            final int w = width * samples, h = height * samples;
            if (colours.length != w * h) {
                colours = new int[w * h];
                depths = new float[w * h];
            }
            bufferWidth = w;
            bufferHeight = h;
            Arrays.fill(colours, BACKGROUND);
            Arrays.fill(depths, Float.NEGATIVE_INFINITY);

            // Turn gently from side to side around a three quarter view
            final double yaw = Math.toRadians(45 + 30 * Math.sin(time * Math.PI * 2));
            final float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
            final float reach = preview.reachUp + preview.reachDown;
            final float scale = Math.min(w / 2.9f, h * 0.92f / reach), cx = w / 2f, cy = (h - reach * scale) / 2 + preview.reachUp * scale;
            final float[] heights = preview.heights;
            final byte[] lights = preview.lights;
            final float step = 2f / GRID;
            for (int y = 0, i = 0; y < VERTICES; y++) {
                final float v = y * step - 1;
                for (int x = 0; x < VERTICES; x++, i++) {
                    final float u = x * step - 1, depth = u * sin + v * cos, land = heights[i] * RELIEF;
                    screenX[i] = cx + (u * cos - v * sin) * scale;
                    screenY[i] = cy + (depth * TILT - land) * scale;
                    screenZ[i] = depth * DEPTH_PER_GROUND + land * DEPTH_PER_HEIGHT;
                }
            }

            // The surface. Triangles which face away are hidden by the land in front of them
            for (int y = 0; y < GRID; y++) {
                for (int x = 0; x < GRID; x++) {
                    final int a = y * VERTICES + x, b = a + 1, c = a + VERTICES, d = c + 1;
                    triangle(a, b, d, lights, heights);
                    triangle(a, d, c, lights, heights);
                }
            }

            // The sides of the block of ground, on the edges which face the viewer
            final int nearV = (cos > 0) ? GRID : 0, nearU = (sin > 0) ? GRID : 0;
            side(true, nearV, 0.55f + 0.3f * Math.abs(sin), cos, sin, scale, cx, cy, step);
            side(false, nearU, 0.55f + 0.3f * Math.abs(cos), cos, sin, scale, cx, cy, step);

            // Average the samples into the frame, in the image which is not being shown
            BufferedImage image = spare;
            if ((image == null) || (image.getWidth() != width) || (image.getHeight() != height)) {
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            final int count = samples * samples;
            for (int y = 0, o = 0; y < height; y++) {
                for (int x = 0; x < width; x++, o++) {
                    int red = 0, green = 0, blue = 0;
                    for (int sy = 0; sy < samples; sy++) {
                        int s = (y * samples + sy) * w + x * samples;
                        for (int sx = 0; sx < samples; sx++, s++) {
                            final int colour = colours[s];
                            red += (colour >> 16) & 0xff;
                            green += (colour >> 8) & 0xff;
                            blue += colour & 0xff;
                        }
                    }
                    pixels[o] = ((red / count) << 16) | ((green / count) << 8) | (blue / count);
                }
            }
            spare = current.image;
            current = new Frame(preview, image);
        }

        /**
         * One side of the block of ground, from the top of the land at that edge down to its foot.
         */
        private static void side(boolean alongU, int position, float brightness, float cos, float sin, float scale, float cx, float cy, float step) {
            final int light = Math.max(0, Math.min(255, Math.round(brightness * 255)));
            for (int n = 0; n <= GRID; n++) {
                final int x = alongU ? n : position, y = alongU ? position : n;
                final int i = y * VERTICES + x, foot = SIDE_FEET + n;
                final float u = x * step - 1, v = y * step - 1, depth = u * sin + v * cos;
                screenX[foot] = cx + (u * cos - v * sin) * scale;
                screenY[foot] = cy + (depth * TILT + BASE) * scale;
                screenZ[foot] = depth * DEPTH_PER_GROUND - BASE * DEPTH_PER_HEIGHT;
                sideTops[n] = i;
            }
            for (int n = 0; n < GRID; n++) {
                final int a = sideTops[n], b = sideTops[n + 1], c = SIDE_FEET + n, d = c + 1;
                flatTriangle(a, b, d, light);
                flatTriangle(a, d, c, light);
            }
        }

        private static void flatTriangle(int a, int b, int c, int light) {
            final float ax = screenX[a], ay = screenY[a], bx = screenX[b], by = screenY[b], cx = screenX[c], cy = screenY[c];
            final float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (area == 0) {
                return;
            }
            final float inverse = 1 / area;
            final int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)))), maxX = Math.min(bufferWidth - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            final int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)))), maxY = Math.min(bufferHeight - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
            final float az = screenZ[a], bz = screenZ[b], cz = screenZ[c];
            final int colour = SIDE[light];
            for (int y = minY; y <= maxY; y++) {
                final float py = y + 0.5f;
                for (int x = minX; x <= maxX; x++) {
                    final float px = x + 0.5f;
                    final float wa = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * inverse;
                    final float wb = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * inverse;
                    final float wc = 1 - wa - wb;
                    if ((wa < 0) || (wb < 0) || (wc < 0)) {
                        continue;
                    }
                    final int o = y * bufferWidth + x;
                    final float z = wa * az + wb * bz + wc * cz;
                    if (z > depths[o]) {
                        depths[o] = z;
                        colours[o] = colour;
                    }
                }
            }
        }

        /**
         * A triangle of the surface, with its light interpolated between the corners, and contour lines to make gentle
         * shapes easier to read.
         */
        private static void triangle(int a, int b, int c, byte[] lights, float[] heights) {
            final float ax = screenX[a], ay = screenY[a], bx = screenX[b], by = screenY[b], cx = screenX[c], cy = screenY[c];
            final float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (area <= 0) {
                return;
            }
            final float inverse = 1 / area;
            final int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)))), maxX = Math.min(bufferWidth - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            final int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)))), maxY = Math.min(bufferHeight - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
            final float az = screenZ[a], bz = screenZ[b], cz = screenZ[c];
            final float al = lights[a] & 0xff, bl = lights[b] & 0xff, cl = lights[c] & 0xff;
            final float ah = heights[a] * CONTOURS, bh = heights[b] * CONTOURS, ch = heights[c] * CONTOURS;
            // How much the height changes per sample, which is how wide a contour line needs to be to look even
            final float gradientX = ((ah - bh) * (cy - ay) - (ah - ch) * (by - ay)) * inverse;
            final float gradientY = ((ah - ch) * (bx - ax) - (ah - bh) * (cx - ax)) * inverse;
            final float lineWidth = (Math.abs(gradientX) + Math.abs(gradientY)) * LINE_WIDTH;
            for (int y = minY; y <= maxY; y++) {
                final float py = y + 0.5f;
                for (int x = minX; x <= maxX; x++) {
                    final float px = x + 0.5f;
                    final float wa = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * inverse;
                    final float wb = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * inverse;
                    final float wc = 1 - wa - wb;
                    if ((wa < 0) || (wb < 0) || (wc < 0)) {
                        continue;
                    }
                    final int o = y * bufferWidth + x;
                    final float z = wa * az + wb * bz + wc * cz;
                    if (z > depths[o]) {
                        depths[o] = z;
                        int light = (int) (wa * al + wb * bl + wc * cl);
                        final float level = wa * ah + wb * bh + wc * ch;
                        final float fraction = level - (float) Math.floor(level);
                        if ((level > 0.02f) && (level < CONTOURS - 0.1f) && ((fraction < lineWidth) || (fraction > 1 - lineWidth))) {
                            light = light * 7 / 8;
                        }
                        colours[o] = SURFACE[light];
                    }
                }
            }
        }

        private static int[] palette(int colour) {
            final int[] palette = new int[256];
            for (int i = 0; i < 256; i++) {
                palette[i] = (Math.min(255, ((colour >> 16) & 0xff) * i / 255) << 16) | (Math.min(255, ((colour >> 8) & 0xff) * i / 255) << 8) | Math.min(255, (colour & 0xff) * i / 255);
            }
            return palette;
        }

        static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "Brush preview renderer");
            thread.setDaemon(true);
            return thread;
        });
        static final AtomicBoolean busy = new AtomicBoolean();
        static volatile Future<?> pending = CompletableFuture.completedFuture(null);
        static volatile Frame current = new Frame(null, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        private static final int SIDE_FEET = VERTICES * VERTICES;
        private static final float[] screenX = new float[SIDE_FEET + VERTICES], screenY = new float[SIDE_FEET + VERTICES], screenZ = new float[SIDE_FEET + VERTICES];
        private static final int[] sideTops = new int[VERTICES];
        private static final int[] SURFACE = palette(0xf0efea), SIDE = palette(0x8f8a80);
        private static final float DEPTH_PER_GROUND = 0.866f, DEPTH_PER_HEIGHT = 0.577f, CONTOURS = 9f, LINE_WIDTH = 0.9f;
        private static int[] colours = new int[0];
        private static float[] depths = new float[0];
        private static int bufferWidth, bufferHeight;
        private static BufferedImage spare;
    }
}
