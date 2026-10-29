package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.util.ThemeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a rendered preview of a height map, with a badge while a new one is being rendered. Also contains the
 * renderers for the previews and the node thumbnails.
 */
public class HeightPreviewPanel extends JComponent {
    public HeightPreviewPanel() {
        setPreferredSize(new Dimension(PREFERRED_SIZE, PREFERRED_SIZE));
        setMinimumSize(new Dimension(160, 160));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public void setRendering(boolean rendering) {
        this.rendering = rendering;
        repaint();
    }

    /**
     * Set a message to show instead of the image, or {@code null} to show the image.
     */
    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            final int size = Math.min(getWidth(), getHeight()) - 1;
            final int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
            final RoundRectangle2D frame = new RoundRectangle2D.Double(x, y, size, size, 12, 12);
            g2.setColor(ThemeUtils.getCanvasBackground());
            g2.fill(frame);
            if ((image != null) && (message == null)) {
                final Shape oldClip = g2.getClip();
                g2.clip(frame);
                g2.drawImage(image, x, y, size, size, null);
                g2.setClip(oldClip);
            } else if (message != null) {
                g2.setColor(ThemeUtils.getColour("WorldPainter.mutedForeground", Color.GRAY));
                final FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(message, x + (size - metrics.stringWidth(message)) / 2f, y + size / 2f);
            }
            g2.setColor(ThemeUtils.getColour("WorldPainter.border", Color.LIGHT_GRAY));
            g2.draw(frame);
            if (rendering) {
                final String text = "Rendering\u2026";
                g2.setFont(getFont().deriveFont(11.5f));
                final FontMetrics metrics = g2.getFontMetrics();
                final int width = metrics.stringWidth(text) + 16;
                g2.setColor(new Color(0, 0, 0, 140));
                g2.fill(new RoundRectangle2D.Double(x + 8, y + 8, width, 22, 11, 11));
                g2.setColor(Color.WHITE);
                g2.drawString(text, x + 16, y + 8 + (22 + metrics.getAscent()) / 2f - 2);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Set the sea level of the map, to which the elevation colours are relative.
     */
    public static void setSeaLevel(int seaLevel) {
        seaLevelForColours = seaLevel;
    }

    /**
     * Render heights as a shaded relief map with hypsometric tints and water.
     *
     * @param heights    The heights in blocks, in row major order.
     * @param size       The width and height of the image.
     * @param step       The distance between samples in blocks, to calibrate the shading.
     * @param waterLevel The water level, below which water is shown.
     */
    public static BufferedImage renderRelief(float[] heights, int size, double step, int waterLevel) {
        final float[] waterLevels = new float[heights.length];
        java.util.Arrays.fill(waterLevels, waterLevel);
        return renderRelief(heights, size, step, waterLevels);
    }

    /**
     * Render heights as a shaded relief map with hypsometric tints and water.
     *
     * @param heights     The heights in blocks, in row major order.
     * @param size        The width and height of the image.
     * @param step        The distance between samples in blocks, to calibrate the shading.
     * @param waterLevels The water level of each sample, below which water is shown.
     */
    public static BufferedImage renderRelief(float[] heights, int size, double step, float[] waterLevels) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        final double shadeFactor = 0.9 / Math.max(step, 0.25);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final float h = heights[x + y * size], waterLevel = waterLevels[x + y * size];
                final int colour;
                if (h < waterLevel) {
                    colour = blend(0x5b8fd6, 0x1e3a74, Math.min(1.0f, (waterLevel - h) / 40f));
                } else {
                    colour = elevationColour(h - seaLevelForColours);
                }
                // Light from the north west
                final float west = heights[Math.max(0, x - 1) + y * size], north = heights[x + Math.max(0, y - 1) * size];
                final float east = heights[Math.min(size - 1, x + 1) + y * size], south = heights[x + Math.min(size - 1, y + 1) * size];
                final double shade = (h < waterLevel) ? 1.0 : Math.max(0.45, Math.min(1.35, 1.0 + ((north - south) + (west - east)) * shadeFactor * 0.5));
                image.setRGB(x, y, shade(colour, shade));
            }
        }
        return image;
    }

    /**
     * Render what the surface painters of a graph will produce: terrain types (or, where no terrain painter wins, the
     * elevation colours), water and vegetation, or the biomes.
     *
     * @param heights     The heights in blocks for the region.
     * @param region      The region, for the world coordinates of the samples.
     * @param waterLevels The water level of each sample.
     * @param painters   The painters of the graph.
     * @param strengths  The strengths of the painters for the region.
     * @param biomes     Whether to show the biomes instead of the terrain and vegetation.
     */
    /**
     * Draw the floating islands of a graph over a preview of the land, seen from above: their tops, lit like the land,
     * with their shadow cast on the land below.
     */
    public static void overlayFloatingIslands(BufferedImage image, CompiledGraph compiled, Evaluation evaluation, Region region) {
        final List<FloatingIslandsNode.Compiled> allIslands = compiled.getFloatingIslands();
        if (allIslands.isEmpty()) {
            return;
        }
        final int size = region.getWidth();
        final double step = region.getStep();
        for (FloatingIslandsNode.Compiled islands: allIslands) {
            final float[] shape = islands.shape(evaluation, region);
            final float[] tops = islands.tops(evaluation, region);
            final int grass = 0x7dbb4f;
            // The shadow, offset to the south east
            final int shadow = (int) Math.max(1, Math.round(24 / step));
            for (int y = size - 1; y >= 0; y--) {
                for (int x = size - 1; x >= 0; x--) {
                    final int sx = x - shadow, sy = y - shadow;
                    if ((sx >= 0) && (sy >= 0) && (shape[sx + sy * size] > 0.5f) && (shape[x + y * size] <= 0.5f)) {
                        image.setRGB(x, y, blend(image.getRGB(x, y) & 0xffffff, 0x0b1220, 0.35f));
                    }
                }
            }
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    final int i = x + y * size;
                    if (shape[i] <= 0.5f) {
                        continue;
                    }
                    final float west = tops[(x > 0) ? (i - 1) : i], north = tops[(y > 0) ? (i - size) : i];
                    final double light = Math.max(0.6, Math.min(1.25, 1 + ((tops[i] - west) + (tops[i] - north)) / step * 0.25));
                    // A dark rim where the island ends
                    final boolean edge = ((x > 0) && (shape[i - 1] <= 0.5f)) || ((y > 0) && (shape[i - size] <= 0.5f))
                            || ((x < size - 1) && (shape[i + 1] <= 0.5f)) || ((y < size - 1) && (shape[i + size] <= 0.5f));
                    final int colour = edge ? 0x5a4a3a : grass;
                    final int r = (int) Math.min(255, ((colour >> 16) & 0xff) * light), g = (int) Math.min(255, ((colour >> 8) & 0xff) * light), b = (int) Math.min(255, (colour & 0xff) * light);
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }
    }

    /**
     * The colour of the water of a biome in Minecraft.
     */
    private static int waterColour(MinecraftBiome biome) {
        if (biome == null) {
            return 0x3f76e4;
        }
        switch (biome) {
            case WARM_OCEAN:
                return 0x43d5ee;
            case LUKEWARM_OCEAN:
                return 0x45adf2;
            case COLD_OCEAN:
                return 0x3d57d6;
            case FROZEN_OCEAN:
            case FROZEN_RIVER:
                return 0x3938c9;
            case SWAMP:
                return 0x617b64;
            case MANGROVE_SWAMP:
                return 0x3a7a6a;
            default:
                return 0x3f76e4;
        }
    }

    public static BufferedImage renderSurface(float[] heights, Region region, float[] waterLevels, List<Painter> painters, float[][] strengths, boolean biomes,
                                              ColourScheme colourScheme, Platform platform, long seed) {
        final int size = region.getWidth();
        final List<Integer> terrainIndices = new ArrayList<>(), biomeIndices = new ArrayList<>(), vegetationIndices = new ArrayList<>();
        for (int p = 0; p < painters.size(); p++) {
            final Object target = painters.get(p).getTarget();
            if ((target instanceof Terrain) || (target instanceof org.pepsoft.worldpainter.MaterialGroup)) {
                terrainIndices.add(p);
            } else if (target instanceof MinecraftBiome) {
                biomeIndices.add(p);
            } else if (target instanceof Vegetation) {
                vegetationIndices.add(p);
            }
        }
        final float[][] terrainStrengths = select(strengths, terrainIndices), biomeStrengths = select(strengths, biomeIndices);
        final int[] terrainSalts = salts(painters, terrainIndices), biomeSalts = salts(painters, biomeIndices);
        final double shadeFactor = 0.9 / Math.max(region.getStep(), 0.25);
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            final int worldY = (int) Math.floor(region.y(y));
            for (int x = 0; x < size; x++) {
                final int index = x + y * size, worldX = (int) Math.floor(region.x(x));
                final float h = heights[index], waterLevel = waterLevels[index];
                final boolean underWater = h < waterLevel;
                int colour;
                if (biomes) {
                    final int winner = SurfaceRules.winner(biomeStrengths, index, worldX, worldY, biomeSalts);
                    if (winner >= 0) {
                        colour = ((MinecraftBiome) painters.get(biomeIndices.get(winner)).getTarget()).getColour();
                    } else {
                        colour = underWater ? 0x5b8fd6 : 0x9e9e9e;
                    }
                } else if (underWater) {
                    // As in Minecraft: the colour of the water depends on the biome (turquoise in warm oceans), and
                    // the sea bed shows through shallow water
                    final int biomeWinner = SurfaceRules.winner(biomeStrengths, index, worldX, worldY, biomeSalts);
                    final int water = waterColour((biomeWinner >= 0) ? (MinecraftBiome) painters.get(biomeIndices.get(biomeWinner)).getTarget() : null);
                    final int terrainWinner = SurfaceRules.winner(terrainStrengths, index, worldX, worldY, terrainSalts);
                    final Object bed = (terrainWinner >= 0) ? painters.get(terrainIndices.get(terrainWinner)).getTarget() : null;
                    final int intHeight = Math.round(h);
                    final int bedColour = (bed instanceof Terrain) ? ((Terrain) bed).getColour(seed, worldX, worldY, intHeight, intHeight, platform, colourScheme)
                            : ((bed instanceof org.pepsoft.worldpainter.MaterialGroup) ? ((org.pepsoft.worldpainter.MaterialGroup) bed).getColour() : 0xd8cfa0);
                    final float depth = waterLevel - h;
                    colour = blend(blend(bedColour, water, 0.45f), water, Math.min(1.0f, depth / 10f));
                    colour = blend(colour, blend(water, 0x061a3a, 0.7f), Math.min(0.85f, Math.max(0, depth - 8) / 60f));
                } else {
                    final int winner = SurfaceRules.winner(terrainStrengths, index, worldX, worldY, terrainSalts);
                    final int intHeight = Math.round(h);
                    final Object terrain = (winner >= 0) ? painters.get(terrainIndices.get(winner)).getTarget() : null;
                    if (terrain instanceof Terrain) {
                        colour = ((Terrain) terrain).getColour(seed, worldX, worldY, intHeight, intHeight, platform, colourScheme);
                    } else if (terrain instanceof org.pepsoft.worldpainter.MaterialGroup) {
                        colour = ((org.pepsoft.worldpainter.MaterialGroup) terrain).getColour();
                    } else {
                        colour = elevationColour(h - seaLevelForColours);
                    }
                    for (int p: vegetationIndices) {
                        final Vegetation vegetation = (Vegetation) painters.get(p).getTarget();
                        final float strength = Math.min(1.0f, strengths[p][index]);
                        if (strength <= 0) {
                            continue;
                        }
                        if (vegetation == Vegetation.CHERRY_TREES) {
                            // Pink blossom, which should stand out
                            colour = blend(colour, vegetation.getColour(), Math.min(0.85f, SurfaceRules.forestLevel(strength) / 15f));
                        } else if (vegetation.isForest()) {
                            // Darker, speckled canopy
                            final float cover = SurfaceRules.forestLevel(strength) / 15f * 0.75f;
                            colour = blend(colour, vegetation.getColour(), cover);
                            if (SurfaceRules.place(strength, worldX, worldY, p)) {
                                colour = blend(colour, 0x0c2410, 0.35f);
                            }
                        } else if (vegetation == Vegetation.SNOW) {
                            colour = blend(colour, vegetation.getColour(), strength * 0.9f);
                        } else {
                            colour = blend(colour, vegetation.getColour(), strength * 0.45f);
                        }
                    }
                }
                final float west = heights[Math.max(0, x - 1) + y * size], north = heights[x + Math.max(0, y - 1) * size];
                final float east = heights[Math.min(size - 1, x + 1) + y * size], south = heights[x + Math.min(size - 1, y + 1) * size];
                final double shade = (underWater && (! biomes)) ? 1.0 : Math.max(0.45, Math.min(1.35, 1.0 + ((north - south) + (west - east)) * shadeFactor * 0.5));
                image.setRGB(x, y, shade(colour, shade));
            }
        }
        return image;
    }

    /**
     * Render values as a colour on a dark background, with zero showing only the background and one the full colour.
     */
    public static BufferedImage renderTinted(float[] values, int size, int colour) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < values.length; i++) {
            image.setRGB(i % size, i / size, blend(0x18181b, colour, Math.max(0.0f, Math.min(1.0f, values[i]))));
        }
        return image;
    }

    /**
     * Render values as greyscale, with zero black and one white.
     */
    public static BufferedImage renderGreyscale(float[] values, int size) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < values.length; i++) {
            final int grey = Math.max(0, Math.min(255, Math.round(values[i] * 255)));
            image.setRGB(i % size, i / size, (grey << 16) | (grey << 8) | grey);
        }
        return image;
    }

    private static float[][] select(float[][] strengths, List<Integer> indices) {
        final float[][] result = new float[indices.size()][];
        for (int i = 0; i < result.length; i++) {
            result[i] = strengths[indices.get(i)];
        }
        return result;
    }

    private static int[] salts(List<Painter> painters, List<Integer> indices) {
        return indices.stream().mapToInt(p -> painters.get(p).getNodeId()).toArray();
    }

    private static int elevationColour(float heightAboveWater) {
        // Beach, lowland green, upland olive, rock brown, grey and snow
        for (int i = 1; i < ELEVATION_STOPS.length; i++) {
            if (heightAboveWater < ELEVATION_STOPS[i]) {
                final float t = (heightAboveWater - ELEVATION_STOPS[i - 1]) / (ELEVATION_STOPS[i] - ELEVATION_STOPS[i - 1]);
                return blend(ELEVATION_COLOURS[i - 1], ELEVATION_COLOURS[i], Math.max(0, t));
            }
        }
        return ELEVATION_COLOURS[ELEVATION_COLOURS.length - 1];
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

    private BufferedImage image;
    private boolean rendering;
    private String message;

    private static final int PREFERRED_SIZE = 340;
    /**
     * The height which the elevation colours are relative to, so that rivers and lakes high up do not turn the land
     * around them into beaches.
     */
    private static volatile int seaLevelForColours = 62;
    private static final float[] ELEVATION_STOPS = {0, 3, 30, 80, 140, 190, 240};
    private static final int[] ELEVATION_COLOURS = {0xd9c88f, 0x7fb069, 0x5c8f45, 0x8a8a4e, 0x8c6d4f, 0x9a9a9a, 0xf4f4f4};
    private static final long serialVersionUID = 1L;
}
