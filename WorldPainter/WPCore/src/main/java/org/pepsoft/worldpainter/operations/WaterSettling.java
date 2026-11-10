package org.pepsoft.worldpainter.operations;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.FloodWithLava;

import java.awt.*;
import java.util.ArrayDeque;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * Lets water (and lava) flow into land which is lower than the water next to it, as it would in Minecraft. Without
 * this, land which is lowered under the sea (an island which is flattened away, say) keeps no water above it, and comes
 * out in Minecraft as a dry hole with walls of water around it.
 *
 * <p>A dry column whose top block is lower than the surface of the water of a column next to it gets the same water
 * level (and the same kind of fluid), and so on from there, like the Flood tool; columns which are already under water
 * or lava, at whatever level, are left as they are.
 */
public final class WaterSettling {
    private WaterSettling() {
        // Prevent instantiation
    }

    /**
     * Let the water flow into the dry low land of an area, from the water in and around it.
     *
     * @param dimension The dimension.
     * @param area      The area, in blocks; only columns in it are changed.
     * @return The number of columns which were flooded.
     */
    public static int settle(Dimension dimension, Rectangle area) {
        try {
            return settle(dimension, area, null);
        } catch (OperationCancelled e) {
            throw new InternalError(e);
        }
    }

    /**
     * Let the water flow into all the dry low land of a dimension next to water.
     *
     * @return The number of columns which were flooded.
     */
    public static int settleEverywhere(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        final Rectangle area = new Rectangle(dimension.getLowestX() << TILE_SIZE_BITS, dimension.getLowestY() << TILE_SIZE_BITS,
                dimension.getWidth() << TILE_SIZE_BITS, dimension.getHeight() << TILE_SIZE_BITS);
        return settle(dimension, area, progressReceiver);
    }

    private static int settle(Dimension dimension, Rectangle area, ProgressReceiver progressReceiver) throws OperationCancelled {
        // First the columns which water can flow into from a column next to them, then on from there
        final ArrayDeque<long[]> queue = new ArrayDeque<>();
        int count = 0;
        final int x1 = area.x + area.width, y1 = area.y + area.height;
        for (int y = area.y; y < y1; y++) {
            if ((progressReceiver != null) && ((y & 63) == 0)) {
                progressReceiver.setProgress(0.5f * (y - area.y) / area.height);
            }
            for (int x = area.x; x < x1; x++) {
                final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
                if (tile == null) {
                    x |= TILE_SIZE_MASK; // On to the next tile
                    continue;
                }
                final int xInTile = x & TILE_SIZE_MASK, yInTile = y & TILE_SIZE_MASK;
                final int height = tile.getIntHeight(xInTile, yInTile);
                if (tile.getWaterLevel(xInTile, yInTile) > height) {
                    continue; // Already under water or lava
                }
                // The highest fluid surface next to it which is above its top block
                int level = Integer.MIN_VALUE;
                boolean lava = false;
                for (int d = 0; d < 4; d++) {
                    final int nx = x + DX[d], ny = y + DY[d];
                    final int neighbourHeight = dimension.getIntHeightAt(nx, ny);
                    if (neighbourHeight == Integer.MIN_VALUE) {
                        continue;
                    }
                    final int neighbourLevel = dimension.getWaterLevelAt(nx, ny);
                    if ((neighbourLevel > neighbourHeight) && (neighbourLevel > height) && (neighbourLevel > level)) {
                        level = neighbourLevel;
                        lava = dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, nx, ny);
                    }
                }
                if (level != Integer.MIN_VALUE) {
                    flood(dimension, x, y, level, lava);
                    count++;
                    queue.add(new long[] {x, y, level, lava ? 1 : 0});
                }
            }
        }
        while (! queue.isEmpty()) {
            final long[] entry = queue.poll();
            final int level = (int) entry[2];
            final boolean lava = entry[3] != 0;
            for (int d = 0; d < 4; d++) {
                final int nx = (int) entry[0] + DX[d], ny = (int) entry[1] + DY[d];
                if (! area.contains(nx, ny)) {
                    continue;
                }
                final int height = dimension.getIntHeightAt(nx, ny);
                if ((height == Integer.MIN_VALUE) || (height >= level) || (dimension.getWaterLevelAt(nx, ny) > height)) {
                    continue;
                }
                flood(dimension, nx, ny, level, lava);
                count++;
                queue.add(new long[] {nx, ny, level, lava ? 1 : 0});
            }
            if ((progressReceiver != null) && ((count & 4095) == 0)) {
                progressReceiver.setProgress(0.5f + 0.5f * Math.min(1f, count / (float) Math.max(1, count + queue.size())));
            }
        }
        if (progressReceiver != null) {
            progressReceiver.setProgress(1.0f);
        }
        return count;
    }

    private static void flood(Dimension dimension, int x, int y, int level, boolean lava) {
        dimension.setWaterLevelAt(x, y, level);
        if (dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y) != lava) {
            dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, lava);
        }
    }

    private static final int[] DX = {-1, 1, 0, 0}, DY = {0, 0, -1, 1};
}
