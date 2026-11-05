package org.pepsoft.worldpainter.operations;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.HalfBlocks;

import java.util.*;

import static org.pepsoft.worldpainter.Constants.*;

/**
 * Smooths the terrain of a whole dimension at once, like the Smooth tool everywhere: each column moves towards the
 * average height around it (a box blur applied twice, which weighs the nearest columns most). Optionally the smoothed
 * land is marked for {@link HalfBlocks half blocks}, so that it is built with slabs and snow layers.
 *
 * <p>The tiles are smoothed one row at a time, always from the heights as they were before, keeping only the rows
 * next to the one being smoothed in memory, so that this also works for very large maps.
 */
public final class GlobalSmooth {
    private GlobalSmooth() {
        // Prevent instantiation
    }

    /**
     * Smooth a dimension.
     *
     * @param dimension  The dimension to smooth.
     * @param radius     How far around each column the heights are averaged, in blocks (1 to {@link #MAX_RADIUS}).
     * @param filter     Where and how strongly to smooth, or {@code null} to smooth everywhere fully.
     * @param halfBlocks Whether to mark the smoothed land for half blocks.
     * @return The number of columns which were smoothed.
     */
    public static long smooth(Dimension dimension, int radius, Filter filter, boolean halfBlocks, ProgressReceiver progressReceiver) throws OperationCancelled {
        if ((radius < 1) || (radius > MAX_RADIUS)) {
            throw new IllegalArgumentException("radius " + radius);
        }
        final SortedMap<Integer, List<Tile>> rows = new TreeMap<>();
        for (Tile tile: dimension.getTiles()) {
            rows.computeIfAbsent(tile.getY(), y -> new ArrayList<>()).add(tile);
        }
        final int minHeight = dimension.getMinHeight(), maxHeight = dimension.getMaxHeight() - 1;
        // The heights of the previous row of tiles as they were before it was smoothed
        Map<Integer, float[]> previousRow = Collections.emptyMap();
        int previousRowY = Integer.MIN_VALUE;
        long count = 0;
        int done = 0;
        for (Map.Entry<Integer, List<Tile>> row: rows.entrySet()) {
            final int tileY = row.getKey();
            final Map<Integer, float[]> originalsAbove = (previousRowY == tileY - 1) ? previousRow : Collections.emptyMap();
            final List<Tile> tiles = row.getValue();
            // First work out the smoothed heights of the whole row, then change them
            final Map<Tile, float[]> results = new HashMap<>();
            tiles.parallelStream().forEach(tile -> {
                final float[] smoothed = smoothTile(dimension, tile, radius, originalsAbove);
                synchronized (results) {
                    results.put(tile, smoothed);
                }
            });
            final Map<Integer, float[]> originals = new HashMap<>();
            for (Tile original: tiles) {
                originals.put(original.getX(), heights(original));
                final float[] smoothed = results.get(original);
                // (Taken for editing, so that the change can be undone)
                final Tile tile = dimension.getTileForEditing(original.getX(), original.getY());
                final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
                tile.inhibitEvents();
                try {
                    for (int y = 0; y < TILE_SIZE; y++) {
                        for (int x = 0; x < TILE_SIZE; x++) {
                            final float strength = (filter != null) ? filter.modifyStrength(x0 | x, y0 | y, 1.0f) : 1.0f;
                            if (strength <= 0.0f) {
                                continue;
                            }
                            final float current = tile.getHeight(x, y), target = smoothed[x | (y << TILE_SIZE_BITS)];
                            if (Float.isNaN(target)) {
                                continue;
                            }
                            final float height = Math.max(minHeight, Math.min(maxHeight, current + (target - current) * strength));
                            if (height != current) {
                                tile.setHeight(x, y, height);
                            }
                            if (halfBlocks && (strength >= 0.5f)) {
                                tile.setBitLayerValue(HalfBlocks.INSTANCE, x, y, true);
                            }
                            count++;
                        }
                    }
                } finally {
                    tile.releaseEvents();
                }
            }
            previousRow = originals;
            previousRowY = tileY;
            done += tiles.size();
            if (progressReceiver != null) {
                progressReceiver.setProgress((float) done / dimension.getTileCount());
            }
        }
        return count;
    }

    /**
     * The smoothed heights of a tile, from the heights around it as they were before smoothing.
     */
    private static float[] smoothTile(Dimension dimension, Tile tile, int radius, Map<Integer, float[]> originalsAbove) {
        // Two box blurs need a margin of twice the radius
        final int margin = radius * 2, size = TILE_SIZE + margin * 2;
        final int x0 = (tile.getX() << TILE_SIZE_BITS) - margin, y0 = (tile.getY() << TILE_SIZE_BITS) - margin;
        float[] values = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                values[x + y * size] = originalHeight(dimension, tile.getY(), originalsAbove, x0 + x, y0 + y);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            values = boxBlur(values, size, radius, true);
            values = boxBlur(values, size, radius, false);
        }
        final float[] result = new float[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                result[x | (y << TILE_SIZE_BITS)] = values[(x + margin) + (y + margin) * size];
            }
        }
        return result;
    }

    /**
     * The height of a column before smoothing: from the copy of the previous row if it was in it (since that row has
     * already been smoothed), otherwise from the map. NaN if there is no land there.
     */
    private static float originalHeight(Dimension dimension, int rowY, Map<Integer, float[]> originalsAbove, int x, int y) {
        final int tileX = x >> TILE_SIZE_BITS, tileY = y >> TILE_SIZE_BITS;
        if (tileY < rowY) {
            final float[] heights = originalsAbove.get(tileX);
            return (heights != null) ? heights[(x & TILE_SIZE_MASK) | ((y & TILE_SIZE_MASK) << TILE_SIZE_BITS)] : Float.NaN;
        }
        final Tile tile = dimension.getTile(tileX, tileY);
        return (tile != null) ? tile.getHeight(x & TILE_SIZE_MASK, y & TILE_SIZE_MASK) : Float.NaN;
    }

    /**
     * A box blur along one axis, ignoring the samples where there is no land (NaN), which stay NaN.
     */
    private static float[] boxBlur(float[] values, int size, int radius, boolean horizontal) {
        final float[] result = new float[values.length];
        for (int line = 0; line < size; line++) {
            double sum = 0;
            int count = 0;
            // Prime the window with the first radius samples
            for (int i = 0; i < Math.min(radius, size); i++) {
                final float value = values[index(line, i, size, horizontal)];
                if (! Float.isNaN(value)) {
                    sum += value;
                    count++;
                }
            }
            for (int i = 0; i < size; i++) {
                final int entering = i + radius, leaving = i - radius - 1;
                if (entering < size) {
                    final float value = values[index(line, entering, size, horizontal)];
                    if (! Float.isNaN(value)) {
                        sum += value;
                        count++;
                    }
                }
                if (leaving >= 0) {
                    final float value = values[index(line, leaving, size, horizontal)];
                    if (! Float.isNaN(value)) {
                        sum -= value;
                        count--;
                    }
                }
                final int at = index(line, i, size, horizontal);
                result[at] = (Float.isNaN(values[at]) || (count == 0)) ? Float.NaN : (float) (sum / count);
            }
        }
        return result;
    }

    private static int index(int line, int i, int size, boolean horizontal) {
        return horizontal ? (i + line * size) : (line + i * size);
    }

    private static float[] heights(Tile tile) {
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                heights[x | (y << TILE_SIZE_BITS)] = tile.getHeight(x, y);
            }
        }
        return heights;
    }

    /**
     * The largest radius, which keeps the blur within the neighbouring tiles.
     */
    public static final int MAX_RADIUS = 32;
}
