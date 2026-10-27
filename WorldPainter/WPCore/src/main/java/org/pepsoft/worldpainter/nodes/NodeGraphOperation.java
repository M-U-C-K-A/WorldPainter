package org.pepsoft.worldpainter.nodes;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.procedural.ParallelTileProcessor;
import org.pepsoft.worldpainter.procedural.ProceduralTerrainOperation.Mode;
import org.pepsoft.worldpainter.procedural.SelectionWeights;

import java.awt.*;
import java.util.List;
import java.util.*;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Applies the output of a {@link NodeGraph} to the existing tiles of a dimension, either everywhere or only inside the
 * selection (optionally with feathered edges). Tiles are processed in parallel, in groups of up to
 * {@value #GROUP_SIZE}x{@value #GROUP_SIZE} so that nodes which need a margin around the evaluated area (blur and
 * erosion) waste less work.
 *
 * <p>The caller is responsible for undo support and for inhibiting events on the dimension.
 */
public class NodeGraphOperation {
    public NodeGraphOperation(NodeGraph graph) {
        this.graph = graph.clone();
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

    /**
     * Whether to repaint the terrain types (and layers) according to the theme of the dimension, as for a new map.
     */
    public boolean isApplyTheme() {
        return applyTheme;
    }

    public void setApplyTheme(boolean applyTheme) {
        this.applyTheme = applyTheme;
    }

    /**
     * The water level to set, or {@link Integer#MIN_VALUE} to leave the water level unchanged.
     */
    public int getWaterLevel() {
        return waterLevel;
    }

    public void setWaterLevel(int waterLevel) {
        this.waterLevel = waterLevel;
    }

    public void apply(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        final CompiledGraph compiled = graph.compile(getMapBounds(dimension));
        final Set<Point> tileCoords = selectionOnly ? SelectionWeights.affectedTiles(dimension, feather) : dimension.getTileCoords();
        final Map<Point, List<Tile>> groups = new HashMap<>();
        final List<Tile> tiles = new ArrayList<>(tileCoords.size());
        for (Point coords: tileCoords) {
            final Tile tile = dimension.getTileForEditing(coords);
            tiles.add(tile);
            groups.computeIfAbsent(new Point(Math.floorDiv(coords.x, GROUP_SIZE), Math.floorDiv(coords.y, GROUP_SIZE)), p -> new ArrayList<>()).add(tile);
        }
        tiles.forEach(Tile::inhibitEvents);
        try {
            ParallelTileProcessor.forEach(groups.values(), group -> processGroup(dimension, compiled, group), progressReceiver);
        } finally {
            tiles.forEach(Tile::releaseEvents);
        }
    }

    /**
     * Get the extent of the existing tiles of a dimension in blocks, or {@code null} if it has no tiles.
     */
    public static Rectangle getMapBounds(Dimension dimension) {
        if (dimension.getTileCount() == 0) {
            return null;
        }
        final Rectangle extent = dimension.getExtent();
        return new Rectangle(extent.x << TILE_SIZE_BITS, extent.y << TILE_SIZE_BITS, extent.width << TILE_SIZE_BITS, extent.height << TILE_SIZE_BITS);
    }

    private void processGroup(Dimension dimension, CompiledGraph compiled, List<Tile> group) {
        // Only evaluate the bounding box of the tiles that are actually present
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Tile tile: group) {
            minX = Math.min(minX, tile.getX());
            minY = Math.min(minY, tile.getY());
            maxX = Math.max(maxX, tile.getX());
            maxY = Math.max(maxY, tile.getY());
        }
        final int width = (maxX - minX + 1) << TILE_SIZE_BITS, height = (maxY - minY + 1) << TILE_SIZE_BITS;
        final Region region = new Region(minX << TILE_SIZE_BITS, minY << TILE_SIZE_BITS, 1, width, height);
        final float[] generated = compiled.evaluateHeights(region);
        for (Tile tile: group) {
            final int offsetX = (tile.getX() - minX) << TILE_SIZE_BITS, offsetY = (tile.getY() - minY) << TILE_SIZE_BITS;
            processTile(dimension, compiled, tile, generated, offsetX + offsetY * width, width);
        }
    }

    private void processTile(Dimension dimension, CompiledGraph compiled, Tile tile, float[] generated, int offset, int stride) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final float baseHeight = compiled.getMinHeight();
        final float[] weights = selectionOnly ? SelectionWeights.forTile(dimension, x0, y0, feather) : null;
        final TileFactory tileFactory = applyTheme ? dimension.getTileFactory() : null;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final float weight = strength * ((weights != null) ? weights[x | (y << TILE_SIZE_BITS)] : 1.0f);
                if (weight <= 0) {
                    continue;
                }
                final float current = tile.getHeight(x, y);
                final float gen = generated[offset + x + y * stride];
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
                tile.setHeight(x, y, Math.max(minHeight, Math.min(maxHeight, current + (target - current) * weight)));
                if (weight >= 0.5f) {
                    if (waterLevel != Integer.MIN_VALUE) {
                        tile.setWaterLevel(x, y, waterLevel);
                    }
                    if (tileFactory != null) {
                        tileFactory.applyTheme(tile, x, y);
                    }
                }
            }
        }
    }

    private final NodeGraph graph;
    private Mode mode = Mode.REPLACE;
    private float strength = 1.0f;
    private boolean selectionOnly, applyTheme = true;
    private int feather = 32, waterLevel = Integer.MIN_VALUE;

    private static final int GROUP_SIZE = 4;
}
