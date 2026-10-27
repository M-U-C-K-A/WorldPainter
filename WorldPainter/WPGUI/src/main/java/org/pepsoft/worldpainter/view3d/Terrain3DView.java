package org.pepsoft.worldpainter.view3d;

import org.pepsoft.worldpainter.util.ThemeUtils;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.function.IntConsumer;

/**
 * An interactive 3D view of a {@link TerrainModel}, rendered in software as a lit height field seen in perspective
 * (rendering screen column by screen column, front to back, with occlusion), which is fast enough for interactive
 * rotation of maps of a million samples. A pick buffer records which sample each pixel shows, so that the location
 * under the mouse is known exactly.
 */
class Terrain3DView extends JComponent {
    Terrain3DView() {
        setOpaque(true);
        setFocusable(true);
        final MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
                requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                final int dx = e.getX() - lastX, dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                if (SwingUtilities.isRightMouseButton(e) || e.isShiftDown()) {
                    // Pan
                    // Drag the ground: right moves the target left, down moves it away from the camera
                    final double scale = distance / focal() * 1.2;
                    final double fx = Math.sin(yaw), fy = -Math.cos(yaw), rx = Math.cos(yaw), ry = Math.sin(yaw);
                    targetX += (-dx * rx + dy * fx / Math.max(0.2, Math.sin(pitch))) * scale;
                    targetY += (-dx * ry + dy * fy / Math.max(0.2, Math.sin(pitch))) * scale;
                } else {
                    // Orbit
                    yaw += dx * 0.008;
                    pitch = Math.max(Math.toRadians(8), Math.min(Math.toRadians(80), pitch + dy * 0.006));
                }
                interacting = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                interacting = false;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverListener != null) {
                    hoverListener.accept(-1);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                distance = Math.max(8, Math.min(maxDistance, distance * Math.pow(1.12, e.getPreciseWheelRotation())));
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Focus on the clicked location
                    final int sample = sampleAt(e.getX(), e.getY());
                    if (sample >= 0) {
                        targetX = (sample % model.width) + 0.5;
                        targetY = (sample / model.width) + 0.5;
                        distance = Math.max(8, distance * 0.6);
                        repaint();
                    }
                }
            }

            private int lastX, lastY;
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    void setModel(TerrainModel model) {
        final boolean first = this.model == null;
        this.model = model;
        maxDistance = Math.max(model.width, model.height) * 3.0;
        if (first || (targetX > model.width) || (targetY > model.height)) {
            resetView();
        }
        rebuildColours();
        repaint();
    }

    TerrainModel getModel() {
        return model;
    }

    void resetView() {
        if (model == null) {
            return;
        }
        targetX = model.width / 2.0;
        targetY = model.height / 2.0;
        yaw = Math.toRadians(-35);
        pitch = Math.toRadians(38);
        distance = Math.max(model.width, model.height) * 1.75;
        repaint();
    }

    void rotateBy(double degrees) {
        yaw += Math.toRadians(degrees);
        repaint();
    }

    void setPitchDegrees(double degrees) {
        pitch = Math.toRadians(degrees);
        repaint();
    }

    Mode getMode() {
        return mode;
    }

    void setMode(Mode mode) {
        this.mode = mode;
        rebuildColours();
        repaint();
    }

    void setShowWater(boolean showWater) {
        this.showWater = showWater;
        rebuildColours();
        repaint();
    }

    void setShowContours(boolean showContours) {
        this.showContours = showContours;
        rebuildColours();
        repaint();
    }

    void setExaggeration(float exaggeration) {
        this.exaggeration = exaggeration;
        repaint();
    }

    /**
     * Set a listener which is notified of the sample under the mouse (or -1).
     */
    void setHoverListener(IntConsumer hoverListener) {
        this.hoverListener = hoverListener;
    }

    /**
     * Recalculate the colour of every sample for the current mode, including shading and contour lines.
     */
    private void rebuildColours() {
        if (model == null) {
            return;
        }
        final int n = model.width * model.height;
        final int[] result = new int[n];
        final float range = Math.max(1, model.highestHeight - model.lowestHeight);
        for (int i = 0; i < n; i++) {
            if (! model.present[i]) {
                continue;
            }
            final float h = model.heights[i];
            final float shade = model.normalShades[i];
            int colour;
            switch (mode) {
                case HEIGHT:
                    colour = shade(ramp(HEIGHT_RAMP, (h - model.lowestHeight) / range), 0.55f + 0.45f * shade);
                    break;
                case SLOPE:
                    colour = shade(ramp(SLOPE_RAMP, Math.min(1, model.slopes[i] / 60f)), 0.7f + 0.3f * shade);
                    break;
                case BIOME:
                    colour = shade((model.biomes[i] >= 0) ? VanillaBiomes.getColour(VanillaBiomes.fromWorldPainterBiome(model.biomes[i])) : 0x808080, 0.5f + 0.5f * shade);
                    break;
                case RELIEF:
                    colour = shade(0xd6d3d1, shade);
                    break;
                default:
                    colour = shade(model.colours[i], shade);
                    break;
            }
            if (showContours && isContour(i)) {
                colour = shade(colour, 0.62f);
            }
            if (showWater && (model.waterLevels[i] > h) && (mode != Mode.SLOPE)) {
                final float depth = Math.min(1, (model.waterLevels[i] - h) / 24f);
                final int water = blend(0x4a8fe7, 0x1d3f8f, depth);
                colour = (mode == Mode.TERRAIN) ? water : blend(colour, water, 0.55f + 0.35f * depth);
            }
            result[i] = colour;
        }
        sampleColours = result;
    }

    private boolean isContour(int i) {
        final int sx = i % model.width, sy = i / model.width;
        final int band = (int) Math.floor(model.heights[i] / CONTOUR_INTERVAL);
        return ((sx + 1 < model.width) && model.present[i + 1] && ((int) Math.floor(model.heights[i + 1] / CONTOUR_INTERVAL) != band))
                || ((sy + 1 < model.height) && model.present[i + model.width] && ((int) Math.floor(model.heights[i + model.width] / CONTOUR_INTERVAL) != band));
    }

    @Override
    protected void paintComponent(Graphics g) {
        final int width = getWidth(), height = getHeight();
        if ((width <= 0) || (height <= 0)) {
            return;
        }
        if ((model == null) || (sampleColours == null)) {
            g.setColor(ThemeUtils.getCanvasBackground());
            g.fillRect(0, 0, width, height);
            return;
        }
        // Render at half resolution while the user is dragging, for responsiveness
        final int divisor = interacting ? 2 : 1;
        final int w = Math.max(1, width / divisor), h = Math.max(1, height / divisor);
        if ((image == null) || (image.getWidth() != w) || (image.getHeight() != h)) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            pick = new int[w * h];
        }
        render(((DataBufferInt) image.getRaster().getDataBuffer()).getData(), w, h);
        final Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, 0, 0, width, height, null);
    }

    private double focal() {
        return Math.max(getWidth(), 1) * 0.9;
    }

    /**
     * Render the height field into a pixel buffer, front to back, one screen column at a time.
     */
    private void render(int[] pixels, int w, int h) {
        final boolean dark = ThemeUtils.isDarkTheme();
        final int skyTop = dark ? 0x0b1220 : 0xdbeafe, skyBottom = dark ? 0x18181b : 0xf8fafc;
        final int fogColour = skyBottom;
        for (int y = 0; y < h; y++) {
            final int c = blend(skyTop, skyBottom, (float) y / h);
            java.util.Arrays.fill(pixels, y * w, (y + 1) * w, c);
        }
        java.util.Arrays.fill(pick, 0);
        final int[] yBuffer = new int[w];
        java.util.Arrays.fill(yBuffer, h);

        final TerrainModel model = this.model;
        final int[] colours = sampleColours;
        final double focal = focal() * w / Math.max(getWidth(), 1);
        final double fx = Math.sin(yaw), fy = -Math.cos(yaw); // Forward
        final double rx = Math.cos(yaw), ry = Math.sin(yaw); // Right
        final double cosPitch = Math.cos(pitch), sinPitch = Math.sin(pitch);
        final float vertical = exaggeration / model.step;
        final double targetHeight = (sampleHeight(model, (int) targetX, (int) targetY) - model.lowestHeight) * vertical;
        final double camX = targetX - fx * distance * cosPitch, camY = targetY - fy * distance * cosPitch;
        final double camH = targetHeight + distance * sinPitch;
        // The base of the model, a little below the lowest point, relative to the camera
        final double floorH = -BASE_THICKNESS * vertical - camH;
        final double halfWidth = w / 2.0 / focal;
        final double zFar = distance * 2.5 + Math.max(model.width, model.height) * 1.5;
        final double fogStart = distance * 0.9, fogEnd = zFar;
        double z = 0.5;
        double dz = 0.25;
        final double centreY = h / 2.0;
        while (z < zFar) {
            // The row of samples at depth z, from the left to the right edge of the view
            final double leftX = camX + (fx - halfWidth * rx) * z, leftY = camY + (fy - halfWidth * ry) * z;
            final double stepX = (2 * halfWidth * rx * z) / w, stepY = (2 * halfWidth * ry * z) / w;
            final float fog = (float) Math.max(0, Math.min(0.85, (z - fogStart) / (fogEnd - fogStart)));
            double px = leftX, py = leftY;
            for (int column = 0; column < w; column++, px += stepX, py += stepY) {
                final int sx = (int) Math.floor(px), sy = (int) Math.floor(py);
                if ((sx < 0) || (sy < 0) || (sx >= model.width) || (sy >= model.height)) {
                    continue;
                }
                final int sample = sy * model.width + sx;
                if (! model.present[sample]) {
                    continue;
                }
                float surface = model.heights[sample];
                if (showWater && (model.waterLevels[sample] > surface) && (mode != Mode.SLOPE)) {
                    surface = model.waterLevels[sample];
                }
                final double dh = (surface - model.lowestHeight) * vertical - camH;
                final double depth = z * cosPitch - dh * sinPitch;
                if (depth <= 0.1) {
                    continue;
                }
                final int screenY = (int) (centreY - focal * (z * sinPitch + dh * cosPitch) / depth);
                if (screenY < yBuffer[column]) {
                    int colour = colours[sample];
                    if (fog > 0) {
                        colour = blend(colour, fogColour, fog);
                    }
                    final int top = Math.max(0, screenY);
                    // Fill down to the base of the model (like a diorama) rather than to the bottom of the screen
                    final int bottom;
                    final double floorDepth = z * cosPitch - floorH * sinPitch;
                    if (floorDepth > 0.1) {
                        bottom = Math.min(yBuffer[column], (int) (centreY - focal * (z * sinPitch + floorH * cosPitch) / floorDepth) + 1);
                    } else {
                        bottom = yBuffer[column];
                    }
                    if (yBuffer[column] == h) {
                        // The front face of the model: the surface colour on top, darker earth below
                        final int side = shade(colour, 0.55f);
                        for (int y = top; y < bottom; y++) {
                            pixels[y * w + column] = (y <= top + 1) ? colour : side;
                            pick[y * w + column] = sample + 1;
                        }
                    } else {
                        for (int y = top; y < bottom; y++) {
                            pixels[y * w + column] = colour;
                            pick[y * w + column] = sample + 1;
                        }
                    }
                    yBuffer[column] = Math.min(yBuffer[column], top);
                }
            }
            z += dz;
            dz *= 1.012;
        }
    }

    private static float sampleHeight(TerrainModel model, int sx, int sy) {
        sx = Math.max(0, Math.min(model.width - 1, sx));
        sy = Math.max(0, Math.min(model.height - 1, sy));
        final int i = sy * model.width + sx;
        return model.present[i] ? model.heights[i] : model.lowestHeight;
    }

    /**
     * The sample shown at a location in the component, or -1.
     */
    int sampleAt(int x, int y) {
        if ((image == null) || (pick == null) || (getWidth() <= 0)) {
            return -1;
        }
        final int px = x * image.getWidth() / getWidth(), py = y * image.getHeight() / getHeight();
        if ((px < 0) || (py < 0) || (px >= image.getWidth()) || (py >= image.getHeight())) {
            return -1;
        }
        return pick[py * image.getWidth() + px] - 1;
    }

    private void updateHover(int x, int y) {
        if (hoverListener != null) {
            hoverListener.accept(sampleAt(x, y));
        }
    }

    static int ramp(int[] stops, float t) {
        t = Math.max(0, Math.min(1, t));
        final float position = t * (stops.length - 1);
        final int index = Math.min(stops.length - 2, (int) position);
        return blend(stops[index], stops[index + 1], position - index);
    }

    static int blend(int colour1, int colour2, float t) {
        final int r = (int) (((colour1 >> 16) & 0xff) * (1 - t) + ((colour2 >> 16) & 0xff) * t);
        final int g = (int) (((colour1 >> 8) & 0xff) * (1 - t) + ((colour2 >> 8) & 0xff) * t);
        final int b = (int) ((colour1 & 0xff) * (1 - t) + (colour2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int colour, float factor) {
        final int r = Math.min(255, (int) (((colour >> 16) & 0xff) * factor));
        final int g = Math.min(255, (int) (((colour >> 8) & 0xff) * factor));
        final int b = Math.min(255, (int) ((colour & 0xff) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private TerrainModel model;
    private int[] sampleColours, pick;
    private BufferedImage image;
    private Mode mode = Mode.TERRAIN;
    private boolean showWater = true, showContours, interacting;
    private float exaggeration = 1.0f;
    private double targetX, targetY, yaw, pitch, distance = 200, maxDistance = 1000;
    private IntConsumer hoverListener;

    static final int[] HEIGHT_RAMP = {0x2e6b3f, 0x5f9e4a, 0xa9c56b, 0xe6d79a, 0xc39a68, 0x8b6a52, 0xf5f5f4};
    static final int[] SLOPE_RAMP = {0x3b82f6, 0x22c55e, 0xeab308, 0xf97316, 0xdc2626};
    static final int CONTOUR_INTERVAL = 8;
    private static final float BASE_THICKNESS = 12;
    private static final long serialVersionUID = 1L;

    enum Mode {
        TERRAIN("Terrain", "The colours of the terrain types, as on the map"),
        HEIGHT("Height", "A colour gradient from the lowest to the highest point, to read the altitude at a glance"),
        SLOPE("Slope", "From blue (flat) to red (steep): find cliffs and buildable flat land"),
        BIOME("Biome", "The biome of each column, painted or automatic"),
        RELIEF("Relief", "Plain shaded relief, to judge the shape of the land");

        Mode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        final String displayName, description;
    }
}
