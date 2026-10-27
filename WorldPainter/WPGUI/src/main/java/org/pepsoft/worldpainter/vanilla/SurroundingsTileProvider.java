package org.pepsoft.worldpainter.vanilla;

import org.pepsoft.util.swing.TileListener;
import org.pepsoft.util.swing.TileProvider;
import org.pepsoft.worldpainter.Dimension;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Renders, as a faded "ghost" around the map, the biomes Minecraft will generate beyond the edges of the map for the
 * world seed, so that the user can anticipate what the map will border on (a desert to the left, an ocean to the
 * right, ...). Only the area within a configurable margin around the map is rendered.
 */
public class SurroundingsTileProvider implements TileProvider {
    public SurroundingsTileProvider(VanillaBiomeSource source, Dimension dimension, int margin) {
        this.source = source;
        this.dimension = dimension;
        this.margin = margin;
        final Rectangle extent = dimension.getExtent(); // In tiles
        bounds = new Rectangle((extent.x << TILE_SIZE_BITS) - margin, (extent.y << TILE_SIZE_BITS) - margin,
                (extent.width << TILE_SIZE_BITS) + 2 * margin, (extent.height << TILE_SIZE_BITS) + 2 * margin);
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
        // Sample the biomes at Minecraft's biome resolution (or the pixel resolution, if coarser), with a one sample
        // margin so that the boundaries between biomes can be outlined
        final int step = Math.max(4, scale);
        final int samplesX = (TILE_SIZE * scale) / step + 2, samplesY = samplesX;
        final int[] biomes = new int[samplesX * samplesY];
        for (int sy = 0; sy < samplesY; sy++) {
            for (int sx = 0; sx < samplesX; sx++) {
                final int x = worldX0 + (sx - 1) * step, y = worldY0 + (sy - 1) * step;
                biomes[sy * samplesX + sx] = isShown(x, y) ? source.getSurfaceBiomeAtBlock(x, y) : -1;
            }
        }
        boolean painted = false;
        for (int py = 0; py < TILE_SIZE; py++) {
            for (int px = 0; px < TILE_SIZE; px++) {
                final int x = worldX0 + px * scale, y = worldY0 + py * scale;
                final int sx = (px * scale) / step + 1, sy = (py * scale) / step + 1;
                final int biome = biomes[sy * samplesX + sx];
                if ((biome == -1) || (! isShown(x, y))) {
                    continue;
                }
                // Fade out towards the edge of the margin
                final float distance = distanceToMap(x, y);
                final float fade = 1.0f - Math.min(1.0f, distance / margin) * 0.55f;
                int alpha = Math.round(170 * fade);
                int colour = GHOST_COLOURS[biome & 0xff];
                // Outline the boundaries between biomes
                if ((biomes[sy * samplesX + sx - 1] != biome) || (biomes[(sy - 1) * samplesX + sx] != biome)) {
                    colour = darken(colour);
                    alpha = Math.min(255, alpha + 50);
                }
                tile.setRGB(px, py, (alpha << 24) | colour);
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
        return bounds.contains(x, y) && (! dimension.isTilePresent(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS)) && (distanceToMap(x, y) <= margin);
    }

    /**
     * The distance from a location to the rectangular extent of the map.
     */
    private float distanceToMap(int x, int y) {
        final int x1 = bounds.x + margin, y1 = bounds.y + margin, x2 = bounds.x + bounds.width - margin, y2 = bounds.y + bounds.height - margin;
        final int dx = Math.max(Math.max(x1 - x, 0), x - x2), dy = Math.max(Math.max(y1 - y, 0), y - y2);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int darken(int colour) {
        return ((((colour >> 16) & 0xff) * 3 / 4) << 16) | ((((colour >> 8) & 0xff) * 3 / 4) << 8) | ((colour & 0xff) * 3 / 4);
    }

    /**
     * The colours used for the ghost: the usual biome map colours, except that the very dark blues of the oceans are
     * replaced with lighter, more watery ones, which read better when faded out.
     */
    private static final int[] GHOST_COLOURS = new int[256];

    static {
        for (int biome = 0; biome < 256; biome++) {
            final VanillaBiomes.Category category = VanillaBiomes.getCategory(biome);
            final String name = VanillaBiomes.getName(biome);
            if (category == VanillaBiomes.Category.OCEAN) {
                GHOST_COLOURS[biome] = name.startsWith("deep_") ? 0x2f62c8 : (name.contains("frozen") ? 0x7fa6e8 : 0x3f78e0);
            } else if (category == VanillaBiomes.Category.RIVER) {
                GHOST_COLOURS[biome] = name.contains("frozen") ? 0x9cbcf2 : 0x4f8cf0;
            } else {
                GHOST_COLOURS[biome] = VanillaBiomes.getColour(biome);
            }
        }
    }

    private final VanillaBiomeSource source;
    private final Dimension dimension;
    private final int margin;
    private final Rectangle bounds;
    private volatile int zoom;
}
