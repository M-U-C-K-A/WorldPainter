package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Utilities for applying a generated terrain to the selection only, with feathered edges.
 */
public final class SelectionWeights {
    private SelectionWeights() {
        // Prevent instantiation
    }

    /**
     * Get the coordinates of the existing tiles which contain a selection or are within {@code feather} blocks of one.
     */
    public static Set<Point> affectedTiles(Dimension dimension, int feather) {
        final Set<Point> tileCoords = dimension.getTileCoords();
        final Set<Point> selectedTileCoords = new HashSet<>();
        for (Point coords: tileCoords) {
            if (dimension.getTile(coords).containsOneOf(SelectionBlock.INSTANCE, SelectionChunk.INSTANCE)) {
                selectedTileCoords.add(coords);
            }
        }
        final int featherTiles = (feather > 0) ? (feather + TILE_SIZE - 1) >> TILE_SIZE_BITS : 0;
        final Set<Point> affected = new HashSet<>();
        for (Point coords: tileCoords) {
            if (isNear(coords, selectedTileCoords, featherTiles)) {
                affected.add(coords);
            }
        }
        return affected;
    }

    /**
     * Calculate how much each block of a tile is selected (0-1), taking the feather width into account.
     *
     * @param x0      The X coordinate in blocks of the top left corner of the tile.
     * @param y0      The Y coordinate in blocks of the top left corner of the tile.
     * @param feather The width in blocks over which the selection fades out.
     * @return The weights in the order {@code x | (y << TILE_SIZE_BITS)}.
     */
    public static float[] forTile(Dimension dimension, int x0, int y0, int feather) {
        final float[] weights = new float[TILE_SIZE * TILE_SIZE];
        if (feather <= 0) {
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    weights[x | (y << TILE_SIZE_BITS)] = isSelected(dimension, x0 + x, y0 + y) ? 1.0f : 0.0f;
                }
            }
            return weights;
        }
        // Box blur the selection mask using a summed area table, then smooth the result
        final int r = feather / 2 + 1, size = TILE_SIZE + 2 * r;
        final int[] sat = new int[(size + 1) * (size + 1)];
        for (int y = 0; y < size; y++) {
            int rowSum = 0;
            for (int x = 0; x < size; x++) {
                if (isSelected(dimension, x0 - r + x, y0 - r + y)) {
                    rowSum++;
                }
                sat[(y + 1) * (size + 1) + x + 1] = sat[y * (size + 1) + x + 1] + rowSum;
            }
        }
        final float area = (2 * r + 1) * (2 * r + 1);
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                // Window [x - r, x + r] in tile coordinates is [x, x + 2r] in grid coordinates
                final int gx1 = x, gy1 = y, gx2 = x + 2 * r + 1, gy2 = y + 2 * r + 1;
                final int sum = sat[gy2 * (size + 1) + gx2] - sat[gy1 * (size + 1) + gx2] - sat[gy2 * (size + 1) + gx1] + sat[gy1 * (size + 1) + gx1];
                weights[x | (y << TILE_SIZE_BITS)] = (float) ProceduralTerrain.smoothStep(0, 1, sum / area);
            }
        }
        return weights;
    }

    private static boolean isSelected(Dimension dimension, int x, int y) {
        return dimension.getBitLayerValueAt(SelectionChunk.INSTANCE, x, y) || dimension.getBitLayerValueAt(SelectionBlock.INSTANCE, x, y);
    }

    private static boolean isNear(Point coords, Set<Point> tileCoords, int distance) {
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                if (tileCoords.contains(new Point(coords.x + dx, coords.y + dy))) {
                    return true;
                }
            }
        }
        return false;
    }
}
