package org.pepsoft.worldpainter.procedural;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Applies a {@link ProceduralTerrainSettings procedural terrain} to the existing tiles of a dimension, either
 * everywhere or only inside the selection (optionally with feathered edges). Tiles are processed in parallel.
 *
 * <p>The caller is responsible for undo support and for inhibiting events on the dimension.
 */
public class ProceduralTerrainOperation {
    public ProceduralTerrainOperation(ProceduralTerrainSettings settings) {
        this.settings = settings;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * How strongly the generated terrain is applied (0-1).
     */
    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public boolean isSelectionOnly() {
        return selectionOnly;
    }

    public void setSelectionOnly(boolean selectionOnly) {
        this.selectionOnly = selectionOnly;
    }

    /**
     * The width in blocks over which the effect fades out at the edges of the selection.
     */
    public int getFeather() {
        return feather;
    }

    public void setFeather(int feather) {
        this.feather = feather;
    }

    public boolean isApplyTerrain() {
        return applyTerrain;
    }

    public void setApplyTerrain(boolean applyTerrain) {
        this.applyTerrain = applyTerrain;
    }

    public boolean isApplyWater() {
        return applyWater;
    }

    public void setApplyWater(boolean applyWater) {
        this.applyWater = applyWater;
    }

    public void apply(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        final ProceduralTerrain terrain = new ProceduralTerrain(settings);
        final Set<Point> tileCoords = new HashSet<>(dimension.getTileCoords());
        final Set<Point> selectedTileCoords = new HashSet<>();
        if (selectionOnly) {
            for (Point coords: tileCoords) {
                if (dimension.getTile(coords).containsOneOf(SelectionBlock.INSTANCE, SelectionChunk.INSTANCE)) {
                    selectedTileCoords.add(coords);
                }
            }
        }
        final int featherTiles = (feather > 0) ? (feather + TILE_SIZE - 1) >> TILE_SIZE_BITS : 0;
        final List<Tile> tiles = new ArrayList<>();
        for (Point coords: tileCoords) {
            if ((! selectionOnly) || isNear(coords, selectedTileCoords, featherTiles)) {
                tiles.add(dimension.getTileForEditing(coords));
            }
        }
        ParallelTileProcessor.process(tiles, tile -> processTile(dimension, terrain, tile), progressReceiver);
    }

    private void processTile(Dimension dimension, ProceduralTerrain terrain, Tile tile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final int baseHeight = settings.getBaseHeight();
        final float[] weights = selectionOnly ? selectionWeights(dimension, x0, y0) : null;
        final float[] generated = new float[TILE_SIZE * TILE_SIZE];
        terrain.getHeights(x0, y0, TILE_SIZE, TILE_SIZE, generated);
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                final float weight = strength * ((weights != null) ? weights[i] : 1.0f);
                final float current = tile.getHeight(x, y);
                if (weight <= 0) {
                    heights[i] = current;
                    continue;
                }
                final float gen = generated[i];
                final float target;
                switch (mode) {
                    case REPLACE:
                        target = gen;
                        break;
                    case ADD:
                        target = current + (gen - baseHeight);
                        break;
                    case RAISE:
                        target = Math.max(current, gen);
                        break;
                    case LOWER:
                        target = Math.min(current, gen);
                        break;
                    default:
                        throw new InternalError();
                }
                final float height = Math.max(minHeight, Math.min(maxHeight, current + (target - current) * weight));
                heights[i] = height;
                tile.setHeight(x, y, height);
            }
        }
        if (applyTerrain || applyWater) {
            final int waterLevel = settings.isWater() ? settings.getWaterLevel() : minHeight;
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    final int i = x | (y << TILE_SIZE_BITS);
                    final float weight = strength * ((weights != null) ? weights[i] : 1.0f);
                    if (weight < 0.5f) {
                        continue;
                    }
                    if (applyTerrain) {
                        final float slope = ProceduralTerrain.slope(heights[clampedIndex(x - 1, y)], heights[clampedIndex(x + 1, y)], heights[clampedIndex(x, y - 1)], heights[clampedIndex(x, y + 1)]);
                        final Terrain surface = terrain.getTerrain(x0 + x, y0 + y, heights[i], slope);
                        tile.setTerrain(x, y, surface);
                    }
                    if (applyWater) {
                        tile.setWaterLevel(x, y, waterLevel);
                    }
                }
            }
        }
    }

    /**
     * Calculate how much each block of a tile is selected (0-1), taking the feather width into account.
     */
    private float[] selectionWeights(Dimension dimension, int x0, int y0) {
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

    private static int clampedIndex(int x, int y) {
        return Math.max(0, Math.min(TILE_SIZE - 1, x)) | (Math.max(0, Math.min(TILE_SIZE - 1, y)) << TILE_SIZE_BITS);
    }

    private final ProceduralTerrainSettings settings;
    private Mode mode = Mode.REPLACE;
    private float strength = 1.0f;
    private boolean selectionOnly, applyTerrain = true, applyWater = true;
    private int feather = 32;

    public enum Mode {
        /**
         * Replace the existing terrain height.
         */
        REPLACE("Replace"),
        /**
         * Add the generated relief (relative to the base height) to the existing terrain.
         */
        ADD("Add relief"),
        /**
         * Only raise the existing terrain where the generated terrain is higher.
         */
        RAISE("Raise only"),
        /**
         * Only lower the existing terrain where the generated terrain is lower.
         */
        LOWER("Lower only");

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
