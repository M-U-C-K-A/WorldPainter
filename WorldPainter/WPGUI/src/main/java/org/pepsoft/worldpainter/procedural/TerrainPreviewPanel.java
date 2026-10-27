package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Renders a shaded relief preview of a {@link ProceduralTerrain} in the background, showing terrain colours, water and
 * hill shading.
 */
public class TerrainPreviewPanel extends JComponent {
    public TerrainPreviewPanel(ColourScheme colourScheme, Platform platform, int minHeight, int maxHeight) {
        this.colourScheme = colourScheme;
        this.platform = platform;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        setMinimumSize(new Dimension(200, 200));
        setOpaque(true);
    }

    /**
     * The area to preview, in blocks, centred on {@link #setCentre(Point) the centre}.
     */
    public void setArea(int area) {
        this.area = area;
    }

    public void setCentre(Point centre) {
        this.centre = centre;
    }

    /**
     * Set a listener which is notified of the height range in the preview after each render, as an array of
     * {@code {min, max}}.
     */
    public void setRangeListener(Consumer<float[]> rangeListener) {
        this.rangeListener = rangeListener;
    }

    /**
     * Render the preview for the specified settings in the background, cancelling any render in progress.
     */
    public synchronized void render(ProceduralTerrainSettings settings) {
        if (pending != null) {
            pending.cancel(true);
        }
        final ProceduralTerrain terrain = new ProceduralTerrain(settings);
        final int area = this.area;
        final Point centre = this.centre;
        rendering = true;
        repaint();
        pending = EXECUTOR.submit(() -> {
            final float[] range = new float[2];
            final BufferedImage result = renderImage(terrain, settings, area, centre, range);
            if (result != null) {
                SwingUtilities.invokeLater(() -> {
                    image = result;
                    rendering = false;
                    repaint();
                    if (rangeListener != null) {
                        rangeListener.accept(range);
                    }
                });
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g;
        g2.setColor(UIManager.getColor("Panel.background"));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (image != null) {
            final int size = Math.min(getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, (getWidth() - size) / 2, (getHeight() - size) / 2, size, size, null);
        }
        if (rendering) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 128));
            g2.fillRoundRect(8, 8, 90, 22, 10, 10);
            g2.setColor(Color.WHITE);
            g2.drawString("Rendering…", 16, 24);
        }
    }

    private BufferedImage renderImage(ProceduralTerrain terrain, ProceduralTerrainSettings settings, int area, Point centre, float[] range) {
        final int size = PREVIEW_SIZE;
        final double step = (double) area / size;
        final double x0 = centre.x - area / 2.0, y0 = centre.y - area / 2.0;
        // One extra sample on each side for the shading
        final float[] heights = new float[(size + 2) * (size + 2)];
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int py = 0; py < size + 2; py++) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            for (int px = 0; px < size + 2; px++) {
                final float h = terrain.getHeight(x0 + (px - 1) * step, y0 + (py - 1) * step);
                heights[py * (size + 2) + px] = h;
                if ((px > 0) && (py > 0) && (px <= size) && (py <= size)) {
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
            }
        }
        range[0] = min;
        range[1] = max;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        final boolean water = settings.isWater();
        final int waterLevel = settings.getWaterLevel();
        final long seed = settings.getSeed();
        // Shading strength compensates for the horizontal scale of the preview
        final double shadeFactor = 1.2 / Math.max(step, 0.25);
        for (int py = 0; py < size; py++) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            for (int px = 0; px < size; px++) {
                final int i = (py + 1) * (size + 2) + px + 1;
                final float h = Math.max(minHeight, Math.min(maxHeight - 1, heights[i]));
                final float west = heights[i - 1], east = heights[i + 1], north = heights[i - size - 2], south = heights[i + size + 2];
                final int worldX = (int) Math.floor(x0 + px * step), worldY = (int) Math.floor(y0 + py * step);
                final int colour;
                if (water && (h < waterLevel)) {
                    final float depth = Math.min(1.0f, (waterLevel - h) / 30f);
                    colour = blend(0x3f76e4, 0x1a2f6b, depth);
                } else {
                    final float spacing = (float) step;
                    final float slope = ProceduralTerrain.slope(west / spacing, east / spacing, north / spacing, south / spacing);
                    final Terrain surface = terrain.getTerrain(worldX, worldY, h, slope);
                    final int intHeight = Math.round(h);
                    colour = surface.getColour(seed, worldX, worldY, intHeight, intHeight, platform, colourScheme);
                }
                // Light from the north west, plus a little extra brightness with altitude for legibility
                final double altitude = (max > min) ? (h - min) / (max - min) : 0.5;
                final double shade = Math.max(0.3, Math.min(1.45, 0.88 + altitude * 0.24 + ((north - south) + (west - east)) * shadeFactor * 0.6));
                image.setRGB(px, py, shade(colour, shade));
            }
        }
        return image;
    }

    private static int blend(int colour1, int colour2, float t) {
        final int r = (int) (((colour1 >> 16) & 0xff) * (1 - t) + ((colour2 >> 16) & 0xff) * t);
        final int g = (int) (((colour1 >> 8) & 0xff) * (1 - t) + ((colour2 >> 8) & 0xff) * t);
        final int b = (int) ((colour1 & 0xff) * (1 - t) + (colour2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int colour, double factor) {
        final int r = (int) Math.min(255, ((colour >> 16) & 0xff) * factor);
        final int g = (int) Math.min(255, ((colour >> 8) & 0xff) * factor);
        final int b = (int) Math.min(255, (colour & 0xff) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private final ColourScheme colourScheme;
    private final Platform platform;
    private final int minHeight, maxHeight;
    private int area = 2048;
    private Point centre = new Point();
    private volatile BufferedImage image;
    private volatile boolean rendering;
    private Future<?> pending;
    private Consumer<float[]> rangeListener;

    private static final int PREVIEW_SIZE = 384;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Procedural terrain preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final long serialVersionUID = 1L;
}
