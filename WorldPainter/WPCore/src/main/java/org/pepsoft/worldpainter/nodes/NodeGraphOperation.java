package org.pepsoft.worldpainter.nodes;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.plants.PlantLayer;
import org.pepsoft.worldpainter.layers.plants.PlantLayerPresets;
import org.pepsoft.worldpainter.procedural.ParallelTileProcessor;
import org.pepsoft.worldpainter.procedural.ProceduralTerrainOperation.Mode;
import org.pepsoft.worldpainter.procedural.SelectionWeights;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

import java.awt.*;
import java.util.List;
import java.util.*;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Applies a {@link NodeGraph} to the existing tiles of a dimension, either everywhere or only inside the selection
 * (optionally with feathered edges): the height of the output, and the terrain types, biomes and vegetation of its
 * {@link PainterNode painters}. Tiles are processed in parallel, in groups of up to {@value #GROUP_SIZE}x{@value
 * #GROUP_SIZE} so that nodes which need a margin around the evaluated area (blur and erosion) waste less work.
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
     * Whether to repaint the terrain types (and layers) according to the theme of the dimension, as for a new map,
     * before applying the terrain painters of the graph.
     */
    public boolean isApplyTheme() {
        return applyTheme;
    }

    public void setApplyTheme(boolean applyTheme) {
        this.applyTheme = applyTheme;
    }

    /**
     * Whether to apply the surface painters (terrain, biomes and vegetation) of the graph.
     */
    public boolean isApplyPainters() {
        return applyPainters;
    }

    public void setApplyPainters(boolean applyPainters) {
        this.applyPainters = applyPainters;
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

    /**
     * Set the custom layers which already exist, so that plant layers created by an earlier application are reused
     * instead of duplicated.
     */
    public void setExistingLayers(Collection<? extends CustomLayer> existingLayers) {
        this.existingLayers = new ArrayList<>(existingLayers);
    }

    /**
     * The plant layers which were created by {@link #apply(Dimension, ProgressReceiver)} and should be added to the
     * user's layers.
     */
    public List<CustomLayer> getCreatedLayers() {
        return createdLayers;
    }

    public void apply(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        // Nodes which read the map must see it as it was before the operation started, not half modified
        final MapSource mapSource = graph.contains(CurrentMapNode.class) ? new FrozenMapSource(dimension)
                : (graph.contains(SelectionMaskNode.class) ? new DimensionMapSource(dimension) : null);
        final CompiledGraph compiled = graph.compile(getMapBounds(dimension), mapSource);
        final List<Painter> painters = applyPainters ? compiled.getPainters() : Collections.emptyList();
        final Map<Vegetation, Layer> vegetationLayers = resolveVegetationLayers(painters);
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
            ParallelTileProcessor.forEach(groups.values(), group -> processGroup(dimension, compiled, painters, vegetationLayers, group), progressReceiver);
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

    /**
     * Find or create the layers for the vegetation painted by the painters.
     */
    private Map<Vegetation, Layer> resolveVegetationLayers(List<Painter> painters) {
        final Map<Vegetation, Layer> layers = new EnumMap<>(Vegetation.class);
        for (Painter painter: painters) {
            if ((painter.getTarget() instanceof Vegetation) && (! layers.containsKey(painter.getTarget()))) {
                final Vegetation vegetation = (Vegetation) painter.getTarget();
                if (vegetation.getLayer() != null) {
                    layers.put(vegetation, vegetation.getLayer());
                } else {
                    final PlantLayerPresets.Preset preset = vegetation.getPlantPreset();
                    PlantLayer layer = null;
                    for (CustomLayer existingLayer: existingLayers) {
                        if ((existingLayer instanceof PlantLayer) && existingLayer.getName().equals(preset.getLayerName())) {
                            layer = (PlantLayer) existingLayer;
                            break;
                        }
                    }
                    if (layer == null) {
                        layer = PlantLayerPresets.create(preset);
                        createdLayers.add(layer);
                    }
                    layers.put(vegetation, layer);
                }
            }
        }
        return layers;
    }

    private void processGroup(Dimension dimension, CompiledGraph compiled, List<Painter> painters, Map<Vegetation, Layer> vegetationLayers, List<Tile> group) {
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
        // One evaluation for the heights and the painters, so that masks on the final terrain reuse it
        final Evaluation evaluation = compiled.createEvaluation();
        final float[] generated = compiled.evaluate(evaluation, compiled.getOutputNodeId(), region);
        for (int i = 0; i < generated.length; i++) {
            generated[i] = compiled.toHeight(generated[i]);
        }
        final float[][] strengths = new float[painters.size()][];
        for (int p = 0; p < painters.size(); p++) {
            strengths[p] = painters.get(p).evaluate(evaluation, region);
        }
        final SurfacePlan plan = new SurfacePlan(painters, vegetationLayers, strengths);
        final float[] waterLevels;
        if (compiled.getWaterBodies().isEmpty()) {
            waterLevels = null;
        } else {
            waterLevels = new float[generated.length];
            Arrays.fill(waterLevels, Float.NaN);
            WaterBody.combine(compiled.getWaterBodies(), evaluation, region, waterLevels);
        }
        for (Tile tile: group) {
            final int offsetX = (tile.getX() - minX) << TILE_SIZE_BITS, offsetY = (tile.getY() - minY) << TILE_SIZE_BITS;
            processTile(dimension, compiled, tile, generated, waterLevels, plan, offsetX + offsetY * width, width);
        }
    }

    private void processTile(Dimension dimension, CompiledGraph compiled, Tile tile, float[] generated, float[] waterLevels, SurfacePlan plan, int offset, int stride) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final float baseHeight = compiled.getMinHeight();
        final float[] weights = selectionOnly ? SelectionWeights.forTile(dimension, x0, y0, feather) : null;
        final TileFactory tileFactory = applyTheme ? dimension.getTileFactory() : null;
        final int seaLevel = (dimension.getTileFactory() instanceof HeightMapTileFactory) ? ((HeightMapTileFactory) dimension.getTileFactory()).getWaterHeight() : Integer.MIN_VALUE;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final float weight = strength * ((weights != null) ? weights[x | (y << TILE_SIZE_BITS)] : 1.0f);
                if (weight <= 0) {
                    continue;
                }
                final float current = tile.getHeight(x, y);
                final int index = offset + x + y * stride;
                final float gen = generated[index];
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
                final float newHeight = Math.max(minHeight, Math.min(maxHeight, current + (target - current) * weight));
                tile.setHeight(x, y, newHeight);
                if (weight >= 0.5f) {
                    if (waterLevel != Integer.MIN_VALUE) {
                        tile.setWaterLevel(x, y, waterLevel);
                    } else if (waterLevels != null) {
                        setWater(tile, x, y, newHeight, waterLevels[index], seaLevel);
                    }
                    if (tileFactory != null) {
                        tileFactory.applyTheme(tile, x, y);
                    }
                    if (! plan.isEmpty()) {
                        paint(tile, x, y, x0 + x, y0 + y, newHeight, index, plan);
                    }
                }
            }
        }
    }

    /**
     * Fill a column with the water of the rivers and lakes of the graph if it is below their level. When replacing
     * the terrain, water left over from earlier rivers and lakes which stands higher than the sea is lowered to it.
     */
    private void setWater(Tile tile, int x, int y, float height, float level, int seaLevel) {
        final int current = tile.getWaterLevel(x, y);
        int target = ((mode == Mode.REPLACE) && (seaLevel != Integer.MIN_VALUE)) ? Math.min(current, seaLevel) : current;
        if ((! Float.isNaN(level)) && (height < level)) {
            target = Math.max(target, (int) level);
        }
        if (target != current) {
            tile.setWaterLevel(x, y, target);
        }
    }

    private static void paint(Tile tile, int x, int y, int worldX, int worldY, float height, int index, SurfacePlan plan) {
        final int terrain = SurfaceRules.winner(plan.terrainStrengths, index, worldX, worldY, plan.terrainSalts);
        if (terrain >= 0) {
            tile.setTerrain(x, y, (Terrain) plan.terrainPainters[terrain].getTarget());
        }
        if (plan.biomePainters.length > 0) {
            final int biome = SurfaceRules.winner(plan.biomeStrengths, index, worldX, worldY, plan.biomeSalts);
            tile.setLayerValue(Biome.INSTANCE, x, y, (biome >= 0) ? ((MinecraftBiome) plan.biomePainters[biome].getTarget()).getId() : Biome.INSTANCE.getDefaultValue());
        }
        if (plan.vegetationLayers.length > 0) {
            final boolean underWater = tile.getWaterLevel(x, y) > height;
            for (int l = 0; l < plan.vegetationLayers.length; l++) {
                final Layer layer = plan.vegetationLayers[l];
                float best = 0;
                for (int p: plan.vegetationPainterIndices[l]) {
                    if ((! underWater) || (! plan.painters.get(p).isOnlyOnLand())) {
                        best = Math.max(best, plan.strengths[p][index]);
                    }
                }
                if (layer.getDataSize() == Layer.DataSize.NIBBLE) {
                    tile.setLayerValue(layer, x, y, SurfaceRules.forestLevel(best));
                } else {
                    tile.setBitLayerValue(layer, x, y, SurfaceRules.place(best, worldX, worldY, layer.getName().hashCode()));
                }
            }
        }
    }

    private final NodeGraph graph;
    private final List<CustomLayer> createdLayers = new ArrayList<>();
    private Mode mode = Mode.REPLACE;
    private float strength = 1.0f;
    private boolean selectionOnly, applyTheme = true, applyPainters = true;
    private int feather = 32, waterLevel = Integer.MIN_VALUE;
    private List<CustomLayer> existingLayers = Collections.emptyList();

    private static final int GROUP_SIZE = 4;

    /**
     * The painters of a graph and their strengths for a group of tiles, sorted by what they paint, with the salts
     * which make their noise differ.
     */
    private static final class SurfacePlan {
        SurfacePlan(List<Painter> painters, Map<Vegetation, Layer> layersByVegetation, float[][] strengths) {
            this.painters = painters;
            this.strengths = strengths;
            final List<Integer> terrain = new ArrayList<>(), biomes = new ArrayList<>();
            final Map<Layer, List<Integer>> vegetation = new LinkedHashMap<>();
            for (int p = 0; p < painters.size(); p++) {
                final Object target = painters.get(p).getTarget();
                if (target instanceof Terrain) {
                    terrain.add(p);
                } else if (target instanceof MinecraftBiome) {
                    biomes.add(p);
                } else if (target instanceof Vegetation) {
                    vegetation.computeIfAbsent(layersByVegetation.get(target), l -> new ArrayList<>()).add(p);
                }
            }
            terrainIndices = terrain.stream().mapToInt(Integer::intValue).toArray();
            biomeIndices = biomes.stream().mapToInt(Integer::intValue).toArray();
            terrainPainters = terrain.stream().map(painters::get).toArray(Painter[]::new);
            biomePainters = biomes.stream().map(painters::get).toArray(Painter[]::new);
            terrainSalts = terrain.stream().mapToInt(p -> painters.get(p).getNodeId()).toArray();
            biomeSalts = biomes.stream().mapToInt(p -> painters.get(p).getNodeId()).toArray();
            vegetationLayers = vegetation.keySet().toArray(new Layer[0]);
            vegetationPainterIndices = vegetation.values().stream().map(list -> list.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
            terrainStrengths = select(strengths, terrainIndices);
            biomeStrengths = select(strengths, biomeIndices);
        }

        boolean isEmpty() {
            return painters.isEmpty();
        }

        private static float[][] select(float[][] strengths, int[] indices) {
            final float[][] result = new float[indices.length][];
            for (int i = 0; i < indices.length; i++) {
                result[i] = strengths[indices[i]];
            }
            return result;
        }

        final List<Painter> painters;
        final float[][] strengths, terrainStrengths, biomeStrengths;
        final int[] terrainIndices, biomeIndices, terrainSalts, biomeSalts;
        final Painter[] terrainPainters, biomePainters;
        final Layer[] vegetationLayers;
        final int[][] vegetationPainterIndices;
    }

    /**
     * Reads the map live. Suitable for previews, and for the selection during an operation (which does not change it).
     */
    public static class DimensionMapSource implements MapSource {
        public DimensionMapSource(Dimension dimension) {
            this.dimension = dimension;
        }

        @Override
        public float getHeight(int x, int y) {
            final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
            return (tile != null) ? tile.getHeight(x & (TILE_SIZE - 1), y & (TILE_SIZE - 1)) : Float.NaN;
        }

        @Override
        public boolean isSelected(int x, int y) {
            final Tile tile = dimension.getTile(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
            return (tile != null) && (tile.getBitLayerValue(SelectionChunk.INSTANCE, x & (TILE_SIZE - 1), y & (TILE_SIZE - 1))
                    || tile.getBitLayerValue(SelectionBlock.INSTANCE, x & (TILE_SIZE - 1), y & (TILE_SIZE - 1)));
        }

        final Dimension dimension;
    }

    /**
     * Reads the heights from a copy taken when it was created, so that they do not change while tiles are being
     * modified in parallel.
     */
    static final class FrozenMapSource extends DimensionMapSource {
        FrozenMapSource(Dimension dimension) {
            super(dimension);
            for (Tile tile: dimension.getTiles()) {
                final float[] tileHeights = new float[TILE_SIZE * TILE_SIZE];
                for (int y = 0; y < TILE_SIZE; y++) {
                    for (int x = 0; x < TILE_SIZE; x++) {
                        tileHeights[x | (y << TILE_SIZE_BITS)] = tile.getHeight(x, y);
                    }
                }
                heights.put(new Point(tile.getX(), tile.getY()), tileHeights);
            }
        }

        @Override
        public float getHeight(int x, int y) {
            final float[] tileHeights = heights.get(new Point(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS));
            return (tileHeights != null) ? tileHeights[(x & (TILE_SIZE - 1)) | ((y & (TILE_SIZE - 1)) << TILE_SIZE_BITS)] : Float.NaN;
        }

        private final Map<Point, float[]> heights = new HashMap<>();
    }
}
