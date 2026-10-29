package org.pepsoft.worldpainter.vanilla;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.ColourUtils;
import org.pepsoft.util.swing.TileListener;
import org.pepsoft.util.swing.TileProvider;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.layers.NotPresentBlock;
import org.pepsoft.worldpainter.procedural.EdgeBlendOperation;
import org.pepsoft.worldpainter.procedural.ProceduralTerrain;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Renders, faded out around the map, the land Minecraft will generate beyond the edges of the map for the world seed, so
 * that the user can anticipate what the map will border on (a desert to the left, an ocean to the right, ...). It is
 * drawn like the map itself: with the same terrain colours, relief shading and contour lines, from the approximate
 * height and the biomes of Minecraft's terrain. Only the area within a configurable margin around the map is rendered.
 */
public class SurroundingsTileProvider implements TileProvider {
    public SurroundingsTileProvider(VanillaBiomeSource source, Dimension dimension, int margin, ColourScheme colourScheme, boolean contourLines, int contourSeparation, TileRenderer.LightOrigin lightOrigin) {
        this.source = source;
        this.dimension = dimension;
        this.margin = margin;
        this.colourScheme = colourScheme;
        this.contourLines = contourLines;
        this.contourSeparation = Math.max(1, contourSeparation);
        this.lightOrigin = lightOrigin;
        final Rectangle extent = dimension.getExtent(); // In tiles
        bounds = new Rectangle((extent.x << TILE_SIZE_BITS) - margin, (extent.y << TILE_SIZE_BITS) - margin,
                (extent.width << TILE_SIZE_BITS) + 2 * margin, (extent.height << TILE_SIZE_BITS) + 2 * margin);
        final World2 world = dimension.getWorld();
        platform = (world != null) ? world.getPlatform() : DefaultPlugin.JAVA_ANVIL_1_20_5;
        seed = dimension.getSeed();
        waterColour = colourScheme.getColour(Material.WATER);
    }

    public VanillaBiomeSource getSource() {
        return source;
    }

    public int getMargin() {
        return margin;
    }

    /**
     * Get the biome Minecraft generates at a location, if it is within the area shown by this provider.
     *
     * @return The biome as a cubiomes biome ID, or -1 if the location is not in the shown area.
     */
    public int getBiomeAt(int x, int y) {
        return isShown(x, y) ? source.getSurfaceBiomeAtBlock(x, y) : -1;
    }

    @Override
    public int getTileSize() {
        return TILE_SIZE;
    }

    @Override
    public boolean isTilePresent(int x, int y) {
        final int scale = 1 << -zoom;
        final int size = TILE_SIZE * scale;
        return bounds.intersects(new Rectangle(x * size, y * size, size, size));
    }

    @Override
    public boolean paintTile(Image image, int tileX, int tileY, int imageX, int imageY) {
        final int scale = 1 << -zoom;
        final int worldX0 = tileX * TILE_SIZE * scale, worldY0 = tileY * TILE_SIZE * scale;
        final BufferedImage tile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        // The heights at the pixels, with a border of one pixel for the relief shading and the contour lines
        final int size = TILE_SIZE + 2;
        float[] heights = null;
        boolean painted = false;
        for (int py = 0; py < TILE_SIZE; py++) {
            for (int px = 0; px < TILE_SIZE; px++) {
                final int x = worldX0 + px * scale, y = worldY0 + py * scale;
                if (! isShown(x, y)) {
                    continue;
                }
                if (heights == null) {
                    heights = new float[size * size];
                    for (int hy = 0; hy < size; hy++) {
                        for (int hx = 0; hx < size; hx++) {
                            heights[hy * size + hx] = heightAt(worldX0 + (hx - 1) * scale, worldY0 + (hy - 1) * scale, scale);
                        }
                    }
                }
                final int i = (py + 1) * size + px + 1;
                final float height = heights[i];
                final int intHeight = Math.round(height);
                // Differences with the neighbours, per block
                final float west = (heights[i - 1] - height) / scale, east = (heights[i + 1] - height) / scale;
                final float north = (heights[i - size] - height) / scale, south = (heights[i + size] - height) / scale;
                int colour;
                if (contourLines && ((intHeight % contourSeparation) == 0)
                        && ((Math.round(heights[i - 1]) < intHeight) || (Math.round(heights[i + 1]) < intHeight) || (Math.round(heights[i - size]) < intHeight) || (Math.round(heights[i + size]) < intHeight))) {
                    colour = 0x000000;
                } else if (intHeight < WATER_LEVEL) {
                    colour = waterColour;
                } else {
                    final float slope = ProceduralTerrain.slope(heights[i - 1], heights[i + 1], heights[i - size], heights[i + size]) / Math.max(1, scale);
                    final Terrain terrain = EdgeBlendOperation.vanillaTerrain(VanillaBiomes.getCategory(biomeAt(x, y, scale)), height, slope);
                    colour = terrain.getColour(seed, x, y, height, intHeight, platform, colourScheme);
                }
                colour = ColourUtils.multiply(colour, brightenAmount(west, east, north, south));
                // Fade out towards the edge of the margin
                final float fade = smoothStep(distanceToMap(x, y) / margin);
                final int alpha = Math.round(235 - 140 * fade);
                tile.setRGB(px, py, (alpha << 24) | (colour & 0xffffff));
                painted = true;
            }
        }
        final Graphics2D g2 = (Graphics2D) image.getGraphics();
        try {
            // The contents of the image are undefined, so clear it to transparent first
            g2.setBackground(new Color(0, 0, 0, 0));
            g2.clearRect(imageX, imageY, TILE_SIZE, TILE_SIZE);
            if (painted) {
                g2.drawImage(tile, imageX, imageY, null);
            }
        } finally {
            g2.dispose();
        }
        return true;
    }

    @Override
    public int getTilePriority(int x, int y) {
        return 0;
    }

    @Override
    public Rectangle getExtent() {
        return null;
    }

    @Override
    public void addTileListener(TileListener tileListener) {
        // Tiles never change
    }

    @Override
    public void removeTileListener(TileListener tileListener) {
        // Tiles never change
    }

    @Override
    public boolean isZoomSupported() {
        return true;
    }

    @Override
    public int getZoom() {
        return zoom;
    }

    @Override
    public void setZoom(int zoom) {
        if (zoom > 0) {
            throw new UnsupportedOperationException("Zooming in not supported");
        }
        this.zoom = zoom;
    }

    /**
     * Whether a location lies in the margin around the map (and not on the map itself).
     */
    private boolean isShown(int x, int y) {
        if ((! bounds.contains(x, y)) || (distanceToMap(x, y) > margin)) {
            return false;
        }
        // Outside the tiles of the map, or on a block of a tile which is not part of the map (round maps)
        return (! dimension.isTilePresent(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS))
                || dimension.getBitLayerValueAt(NotPresentBlock.INSTANCE, x, y) || dimension.getBitLayerValueAt(NotPresent.INSTANCE, x, y);
    }

    /**
     * The distance from a location to the rectangular extent of the map, measured along the axes (the larger of the
     * horizontal and vertical distances), so that the shown area is a rectangle with square corners.
     */
    private float distanceToMap(int x, int y) {
        final int x1 = bounds.x + margin, y1 = bounds.y + margin, x2 = bounds.x + bounds.width - margin, y2 = bounds.y + bounds.height - margin;
        return Math.max(Math.max(Math.max(x1 - x, 0), x - x2), Math.max(Math.max(y1 - y, 0), y - y2));
    }

    /**
     * The approximate height of Minecraft's surface at a block, interpolated between the centres of the biome cells
     * (every four blocks). When zoomed out far the cells are sampled directly.
     */
    private float heightAt(int x, int y, int scale) {
        if (scale > 4) {
            final int qx = x >> 2, qy = y >> 2;
            return EdgeBlendOperation.vanillaSurfaceHeight(source.getApproximateSurfaceHeight(qx, qy), VanillaBiomes.getCategory(source.getSurfaceBiome(qx, qy)));
        }
        final double qx = (x - 1.5) / 4.0, qy = (y - 1.5) / 4.0;
        final int ix = (int) Math.floor(qx), iy = (int) Math.floor(qy);
        final float fx = (float) (qx - ix), fy = (float) (qy - iy);
        return (cellHeight(ix, iy) * (1 - fx) + cellHeight(ix + 1, iy) * fx) * (1 - fy)
                + (cellHeight(ix, iy + 1) * (1 - fx) + cellHeight(ix + 1, iy + 1) * fx) * fy;
    }

    private int biomeAt(int x, int y, int scale) {
        if (scale > 4) {
            return source.getSurfaceBiome(x >> 2, y >> 2);
        }
        return getCell(x >> 2, y >> 2).biomes[((y >> 2) & CELL_MASK) * CELL_SIZE + ((x >> 2) & CELL_MASK)];
    }

    private float cellHeight(int qx, int qy) {
        return getCell(qx, qy).heights[(qy & CELL_MASK) * CELL_SIZE + (qx & CELL_MASK)];
    }

    /**
     * The heights and biomes of a block of biome cells, which are cached since sampling Minecraft's terrain is
     * relatively slow and each cell is used for many pixels.
     */
    private Cells getCell(int qx, int qy) {
        final long key = (((long) (qx >> CELL_BITS)) << 32) | ((qy >> CELL_BITS) & 0xffffffffL);
        return cells.computeIfAbsent(key, k -> {
            final int qx0 = (qx >> CELL_BITS) << CELL_BITS, qy0 = (qy >> CELL_BITS) << CELL_BITS;
            final Cells cells = new Cells();
            for (int dy = 0; dy < CELL_SIZE; dy++) {
                for (int dx = 0; dx < CELL_SIZE; dx++) {
                    final int biome = source.getSurfaceBiome(qx0 + dx, qy0 + dy);
                    cells.biomes[dy * CELL_SIZE + dx] = biome;
                    cells.heights[dy * CELL_SIZE + dx] = EdgeBlendOperation.vanillaSurfaceHeight(source.getApproximateSurfaceHeight(qx0 + dx, qy0 + dy), VanillaBiomes.getCategory(biome));
                }
            }
            return cells;
        });
    }

    /**
     * The amount by which to brighten a pixel (out of 256) for the relief, from the differences in height with its
     * neighbours, the same way as the map is shaded.
     */
    private int brightenAmount(float west, float east, float north, float south) {
        final float amount;
        switch (lightOrigin) {
            case NORTHWEST:
                amount = east - west + south - north;
                break;
            case NORTHEAST:
                amount = west - east + south - north;
                break;
            case SOUTHEAST:
                amount = west - east + north - south;
                break;
            case SOUTHWEST:
                amount = east - west + north - south;
                break;
            default:
                return 256;
        }
        return Math.max(0, Math.round(amount * 32) + 256);
    }

    private static float smoothStep(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static final class Cells {
        final float[] heights = new float[CELL_SIZE * CELL_SIZE];
        final int[] biomes = new int[CELL_SIZE * CELL_SIZE];
    }

    private final VanillaBiomeSource source;
    private final Dimension dimension;
    private final int margin, contourSeparation, waterColour;
    private final Rectangle bounds;
    private final ColourScheme colourScheme;
    private final boolean contourLines;
    private final TileRenderer.LightOrigin lightOrigin;
    private final Platform platform;
    private final long seed;
    private final Map<Long, Cells> cells = new ConcurrentHashMap<>();
    private volatile int zoom;

    /**
     * Below this height Minecraft's surface is under water (the sea level is 63).
     */
    private static final int WATER_LEVEL = EdgeBlendOperation.VANILLA_SEA_LEVEL - 1;
    private static final int CELL_BITS = 4, CELL_SIZE = 1 << CELL_BITS, CELL_MASK = CELL_SIZE - 1;
}
