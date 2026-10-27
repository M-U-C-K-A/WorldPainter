package org.pepsoft.worldpainter.procedural;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
        final List<Tile> tiles = new ArrayList<>();
        for (Point coords: selectionOnly ? SelectionWeights.affectedTiles(dimension, feather) : dimension.getTileCoords()) {
            tiles.add(dimension.getTileForEditing(coords));
        }
        ParallelTileProcessor.process(tiles, tile -> processTile(dimension, terrain, tile), progressReceiver);
    }

    private void processTile(Dimension dimension, ProceduralTerrain terrain, Tile tile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final int baseHeight = settings.getBaseHeight();
        final float[] weights = selectionOnly ? SelectionWeights.forTile(dimension, x0, y0, feather) : null;
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
