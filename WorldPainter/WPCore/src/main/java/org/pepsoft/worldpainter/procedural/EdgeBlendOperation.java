package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.layers.*;
import org.pepsoft.worldpainter.nodes.SurfaceRules;
import org.pepsoft.worldpainter.vanilla.VanillaBiomeSource;
import org.pepsoft.worldpainter.vanilla.VanillaBiomes;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * Progressively blends the edges of a map into a target terrain, instead of ending in an abrupt cliff. The transition
 * zone can lie inside the existing map (the terrain near the edge is faded towards the target), outside it (the map is
 * extended with new tiles which fade from the edge of the map to the target) or straddle the edge.
 *
 * <p>The target is either a flat level or a {@link ProceduralTerrainSettings procedural terrain}; a gentle
 * "Minecraft-like" target makes the edge of the map match the terrain Minecraft generates beyond it, after which
 * Minecraft's own chunk blending takes care of the last few blocks.
 *
 * <p>The caller is responsible for undo support and for inhibiting events on the dimension.
 */
public class EdgeBlendOperation {
    /**
     * The width of the transition zone in blocks.
     */
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public Placement getPlacement() {
        return placement;
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public Curve getCurve() {
        return curve;
    }

    public void setCurve(Curve curve) {
        this.curve = curve;
    }

    /**
     * How irregular the transition zone is (0-1); zero gives transitions exactly parallel to the edge.
     */
    public float getIrregularity() {
        return irregularity;
    }

    public void setIrregularity(float irregularity) {
        this.irregularity = irregularity;
    }

    /**
     * The procedural terrain to blend into, or {@code null} to blend into a flat level at {@link #getTargetHeight()}.
     */
    public ProceduralTerrainSettings getTarget() {
        return target;
    }

    public void setTarget(ProceduralTerrainSettings target) {
        this.target = target;
    }

    /**
     * The land Minecraft generates for a seed, to blend into instead of a procedural terrain or a flat level: its
     * (approximate) height, its biomes and matching terrain types, so that the map joins the chunks Minecraft generates
     * around it. {@code null} to not blend into the vanilla land.
     */
    public VanillaBiomeSource getVanillaTarget() {
        return vanillaTarget;
    }

    public void setVanillaTarget(VanillaBiomeSource vanillaTarget) {
        this.vanillaTarget = vanillaTarget;
    }

    public int getTargetHeight() {
        return targetHeight;
    }

    public void setTargetHeight(int targetHeight) {
        this.targetHeight = targetHeight;
    }

    /**
     * The terrain type to use for the outer part of the transition zone if the target is a flat level.
     */
    public Terrain getTargetTerrain() {
        return targetTerrain;
    }

    public void setTargetTerrain(Terrain targetTerrain) {
        this.targetTerrain = targetTerrain;
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

    public int getWaterLevel() {
        return waterLevel;
    }

    public void setWaterLevel(int waterLevel) {
        this.waterLevel = waterLevel;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * The tile coordinates of the tiles which would be added by this operation.
     */
    public Set<Point> getTilesToAdd(Dimension dimension) {
        final Set<Point> original = new HashSet<>(dimension.getTileCoords());
        final Set<Point> toAdd = new HashSet<>();
        final int outsideWidth = Math.round(width * placement.outsideFraction);
        if (outsideWidth <= 0) {
            return toAdd;
        }
        final int range = (outsideWidth + TILE_SIZE - 1) >> TILE_SIZE_BITS;
        for (Point coords: original) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dy = -range; dy <= range; dy++) {
                    final Point candidate = new Point(coords.x + dx, coords.y + dy);
                    if ((! original.contains(candidate)) && (distanceToTile(candidate, coords) < outsideWidth)) {
                        toAdd.add(candidate);
                    }
                }
            }
        }
        return toAdd;
    }

    public void apply(Dimension dimension, ProgressReceiver progressReceiver) throws OperationCancelled {
        if (width <= 0) {
            return;
        }
        final Set<Point> original = new HashSet<>(dimension.getTileCoords());
        if (original.isEmpty()) {
            return;
        }
        final ProceduralTerrain targetTerrainGenerator = (target != null) ? new ProceduralTerrain(target) : null;
        final SimplexNoise irregularityNoise = new SimplexNoise(seed ^ 0x2545F4914F6CDD1DL);
        final int insideWidth = Math.round(width * (1 - placement.outsideFraction));
        final int insideRange = (insideWidth + TILE_SIZE - 1) >> TILE_SIZE_BITS;

        // Add the new tiles first (on this thread)
        final TileFactory tileFactory = dimension.getTileFactory();
        final List<Tile> newTiles = new ArrayList<>();
        for (Point coords: getTilesToAdd(dimension)) {
            final Tile tile = tileFactory.createTile(coords.x, coords.y);
            dimension.addTile(tile);
            newTiles.add(dimension.getTileForEditing(coords));
        }

        // Tiles of the map which are only partly present (such as those along the edge of a round map) have their
        // missing blocks filled in like new tiles. Round maps mark the blocks outside the circle as void, and whole
        // chunks outside it as not present; void blocks only count as missing in tiles at the edge of the map, so that
        // voids painted inside the map are left alone. Remember which blocks were missing, before they are filled in
        final Map<Point, boolean[]> notPresent = new HashMap<>();
        for (Point coords: original) {
            final Tile tile = dimension.getTile(coords);
            final boolean notPresentLayers = tile.hasLayer(NotPresent.INSTANCE) || tile.hasLayer(NotPresentBlock.INSTANCE);
            final boolean voids = tile.hasLayer(org.pepsoft.worldpainter.layers.Void.INSTANCE) && (notPresentLayers || hasMissingNeighbour(coords, original, 1));
            if (notPresentLayers || voids) {
                final boolean[] mask = new boolean[TILE_SIZE * TILE_SIZE];
                boolean any = false;
                for (int x = 0; x < TILE_SIZE; x++) {
                    for (int y = 0; y < TILE_SIZE; y++) {
                        final boolean missing = tile.getBitLayerValue(NotPresent.INSTANCE, x, y) || tile.getBitLayerValue(NotPresentBlock.INSTANCE, x, y)
                                || (voids && tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y));
                        mask[x | (y << TILE_SIZE_BITS)] = missing;
                        any |= missing;
                    }
                }
                if (any) {
                    notPresent.put(coords, mask);
                }
            }
        }
        for (Point coords: notPresent.keySet()) {
            newTiles.add(dimension.getTileForEditing(coords));
        }

        // Find the existing tiles which are close enough to the edge to be affected
        final List<Tile> edgeTiles = new ArrayList<>();
        if (insideWidth > 0) {
            for (Point coords: original) {
                if (hasMissingNeighbour(coords, original, insideRange) || notPresent.containsKey(coords)) {
                    edgeTiles.add(dimension.getTileForEditing(coords));
                }
            }
        }

        // Process the new tiles first, since they need to sample the unmodified edge of the original map
        final Context context = new Context(dimension, original, notPresent, targetTerrainGenerator, irregularityNoise);
        if (vanillaTarget != null) {
            context.sampleVanilla(vanillaTarget, newTiles, edgeTiles);
        }
        final float split = (float) newTiles.size() / Math.max(newTiles.size() + edgeTiles.size(), 1);
        if (! newTiles.isEmpty()) {
            ParallelTileProcessor.process(newTiles, tile -> processOutsideTile(context, tile), ((progressReceiver != null) && (split < 1.0f)) ? new SubProgressReceiver(progressReceiver, 0.0f, split) : progressReceiver);
        }
        if (! edgeTiles.isEmpty()) {
            ParallelTileProcessor.process(edgeTiles, tile -> processInsideTile(context, tile), ((progressReceiver != null) && (split > 0.0f)) ? new SubProgressReceiver(progressReceiver, split, 1.0f - split) : progressReceiver);
        }
    }

    /**
     * The weight of the original terrain (1) versus the target (0) at a particular signed distance from the edge
     * (positive inside the original map, negative outside).
     */
    public float originalWeight(float signedDistance) {
        return curve.apply(clamp01(placement.outsideFraction + signedDistance / width));
    }

    private void processInsideTile(Context context, Tile tile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final boolean[] wasMissing = context.notPresent.get(new Point(tile.getX(), tile.getY()));
        final NearestBlocks nearestMissing = context.nearest(tile, false, reach());
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        final float[] targetWeights = new float[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                final int worldX = x0 + x, worldY = y0 + y;
                final float current = tile.getHeight(x, y);
                heights[i] = current;
                if ((wasMissing != null) && wasMissing[i]) {
                    // Filled in with the new tiles
                    continue;
                }
                final float distance = nearestMissing.distance[i];
                final float weight = originalWeight(distance + irregularity(context, worldX, worldY));
                if (weight >= 1.0f) {
                    continue;
                }
                final float targetHeight = context.targetHeight(worldX, worldY);
                final float height = Math.max(minHeight, Math.min(maxHeight, targetHeight + (current - targetHeight) * weight));
                heights[i] = height;
                targetWeights[i] = 1 - weight;
                tile.setHeight(x, y, height);
            }
        }
        applyTerrainAndWater(context, tile, heights, targetWeights, null, null);
    }

    /**
     * Process a tile which was added by this operation, or the blocks which were not present of a tile of the map.
     */
    private void processOutsideTile(Context context, Tile tile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final boolean[] missing = context.notPresent.get(new Point(tile.getX(), tile.getY()));
        final NearestBlocks nearest = context.nearest(tile, true, reach());
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        final float[] targetWeights = new float[TILE_SIZE * TILE_SIZE];
        final Terrain[] edgeTerrains = new Terrain[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                final int worldX = x0 + x, worldY = y0 + y;
                if ((missing != null) && (! missing[i])) {
                    // A block of the map
                    heights[i] = tile.getHeight(x, y);
                    continue;
                }
                final float distance = nearest.distance[i];
                final float weight = (distance < Float.MAX_VALUE) ? originalWeight(-distance + irregularity(context, worldX, worldY)) : 0.0f;
                final float targetHeight = context.targetHeight(worldX, worldY);
                float height;
                if (weight > 0) {
                    final int edgeX = nearest.x[i], edgeY = nearest.y[i];
                    final float edgeHeight = sampleEdgeHeight(context, edgeX, edgeY, distance);
                    height = targetHeight + (edgeHeight - targetHeight) * weight;
                    edgeTerrains[i] = sampleEdgeTerrain(context, edgeX, edgeY, worldX, worldY);
                } else {
                    height = targetHeight;
                }
                height = Math.max(minHeight, Math.min(maxHeight, height));
                heights[i] = height;
                targetWeights[i] = 1 - weight;
                tile.setHeight(x, y, height);
                if (missing != null) {
                    tile.setBitLayerValue(NotPresent.INSTANCE, x, y, false);
                    tile.setBitLayerValue(NotPresentBlock.INSTANCE, x, y, false);
                    tile.setBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y, false);
                }
            }
        }
        applyTerrainAndWater(context, tile, heights, targetWeights, edgeTerrains, missing);
    }

    /**
     * How far from the edge of the map the transition can reach, including its irregularity.
     */
    private int reach() {
        return (int) Math.ceil(width * (1 + irregularity * 0.5f)) + 2;
    }

    /**
     * Set the terrain type and water level in the transition zone. The terrain of the map and that of the target are
     * mixed as a scatter of blocks which grows denser towards the target, rather than switching from one to the other
     * along a line.
     *
     * @param edgeTerrains For a tile which was added by this operation, the terrain of the map near its edge for each
     *                     block within reach of the map ({@code null} elsewhere); {@code null} for a tile of the map.
     *                     The terrain and water of added tiles are determined everywhere.
     * @param only         If not {@code null}, only the blocks for which this is {@code true} are changed.
     */
    private void applyTerrainAndWater(Context context, Tile tile, float[] heights, float[] targetWeights, Terrain[] edgeTerrains, boolean[] only) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int noWater = tile.getMinHeight();
        final boolean newTile = edgeTerrains != null;
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                if ((only != null) && (! only[i])) {
                    continue;
                }
                final int worldX = x0 + x, worldY = y0 + y;
                final float targetWeight = targetWeights[i];
                if (applyTerrain && (newTile || (targetWeight > 0))) {
                    if ((targetWeight > transitionThreshold(worldX, worldY)) || (newTile && (edgeTerrains[i] == null))) {
                        final float slope = ProceduralTerrain.slope(heights[clampedIndex(x - 1, y)], heights[clampedIndex(x + 1, y)], heights[clampedIndex(x, y - 1)], heights[clampedIndex(x, y + 1)]);
                        if (context.vanillaBiomes != null) {
                            final VanillaBiomes.Category category = VanillaBiomes.getCategory(context.vanillaBiome(worldX, worldY));
                            tile.setTerrain(x, y, vanillaTerrain(category, heights[i], slope));
                            if ((heights[i] >= waterLevel) && (slope < 40)) {
                                applyVanillaTrees(tile, x, y, category, targetWeight);
                            }
                        } else {
                            tile.setTerrain(x, y, context.surface.getTerrain(worldX, worldY, heights[i], slope));
                        }
                    } else if (newTile) {
                        tile.setTerrain(x, y, edgeTerrains[i]);
                    }
                    // Minecraft's biomes are four by four blocks, so decide them for the middle of each such cell
                    if ((context.vanillaBiomes != null) && (targetWeight > transitionThreshold((worldX & ~3) + 2, (worldY & ~3) + 2))) {
                        tile.setLayerValue(Biome.INSTANCE, x, y, VanillaBiomes.toWorldPainterBiome(context.vanillaBiome(worldX, worldY)));
                    }
                }
                if (newTile || (targetWeight >= 0.5f)) {
                    if (applyWater) {
                        tile.setWaterLevel(x, y, (heights[i] < waterLevel) ? waterLevel : noWater);
                    } else if (newTile) {
                        tile.setWaterLevel(x, y, noWater);
                    }
                } else if (applyWater && (targetWeight > 0) && (heights[i] < waterLevel) && (tile.getWaterLevel(x, y) < waterLevel)) {
                    // Inside the original map, but lowered below the water level
                    tile.setWaterLevel(x, y, waterLevel);
                }
            }
        }
    }

    /**
     * The weight of the target above which a block takes the terrain of the target: a random value per block, mixed
     * with a gentle noise so that the blocks gather in small clumps.
     */
    private float transitionThreshold(int x, int y) {
        final float clumps = (float) (clumpNoise.noise(x / 6.0, y / 6.0) * 0.5 + 0.5);
        return Math.max(0.02f, Math.min(0.98f, 0.55f * SurfaceRules.random(x, y, 0x3c6ef372) + 0.45f * clumps));
    }

    /**
     * Plant trees like those Minecraft grows in a biome, thinning out towards the map.
     */
    private static void applyVanillaTrees(Tile tile, int x, int y, VanillaBiomes.Category category, float targetWeight) {
        final Layer layer;
        final float density;
        switch (category) {
            case FOREST:
                layer = DeciduousForest.INSTANCE;
                density = 0.55f;
                break;
            case TAIGA:
                layer = PineForest.INSTANCE;
                density = 0.5f;
                break;
            case JUNGLE:
                layer = Jungle.INSTANCE;
                density = 0.7f;
                break;
            case SWAMP:
                layer = SwampLand.INSTANCE;
                density = 0.45f;
                break;
            case PLAINS:
            case SAVANNA:
                layer = DeciduousForest.INSTANCE;
                density = 0.04f;
                break;
            default:
                return;
        }
        final int level = SurfaceRules.forestLevel(density * Math.min(1.0f, targetWeight * 1.5f));
        if (level > tile.getLayerValue(layer, x, y)) {
            tile.setLayerValue(layer, x, y, level);
        }
    }

    /**
     * The terrain of the map near the block of its edge closest to a location outside it, from a point jittered along
     * the edge so that continuing it outwards does not produce streaks.
     */
    private static Terrain sampleEdgeTerrain(Context context, int edgeX, int edgeY, int x, int y) {
        final int jitterX = Math.round((SurfaceRules.random(x, y, 0x1b873593) - 0.5f) * 16), jitterY = Math.round((SurfaceRules.random(x, y, 0x85ebca6b) - 0.5f) * 16);
        final boolean jittered = context.isPresent(edgeX + jitterX, edgeY + jitterY);
        final Terrain terrain = context.dimension.getTerrainAt(jittered ? (edgeX + jitterX) : edgeX, jittered ? (edgeY + jitterY) : edgeY);
        return (terrain != null) ? terrain : Terrain.GRASS;
    }

    /**
     * Adjust the approximate height of Minecraft's surface so that land stays above the sea and water below it, as
     * Minecraft's own surface does.
     */
    public static float vanillaSurfaceHeight(float height, VanillaBiomes.Category category) {
        switch (category) {
            case OCEAN:
                return Math.min(height, VANILLA_SEA_LEVEL - 6);
            case RIVER:
                return Math.min(height, VANILLA_SEA_LEVEL - 3);
            case BEACH:
                return Math.max(VANILLA_SEA_LEVEL, Math.min(height, VANILLA_SEA_LEVEL + 4));
            default:
                return Math.max(height, VANILLA_SEA_LEVEL + 2);
        }
    }

    /**
     * A terrain type which looks like what Minecraft puts on the surface of a biome.
     */
    public static Terrain vanillaTerrain(VanillaBiomes.Category category, float height, float slope) {
        if (slope > 50) {
            return Terrain.ROCK;
        }
        switch (category) {
            case OCEAN:
            case RIVER:
                return (height < 50) ? Terrain.GRAVEL : Terrain.SAND;
            case BEACH:
                return Terrain.BEACHES;
            case DESERT:
                return Terrain.DESERT;
            case BADLANDS:
                return (slope > 25) ? Terrain.MESA : Terrain.RED_DESERT;
            case SNOWY:
                return Terrain.SNOW;
            case MOUNTAINS:
                return (height > 140) ? Terrain.DEEP_SNOW : ((slope > 30) ? Terrain.STONE_MIX : Terrain.GRASS);
            case TAIGA:
                return Terrain.PODZOL;
            case MUSHROOM:
                return Terrain.MYCELIUM;
            case SWAMP:
                return Terrain.MUD;
            default:
                return (slope > 35) ? Terrain.STONE_MIX : Terrain.GRASS;
        }
    }

    private float irregularity(Context context, int x, int y) {
        if (irregularity <= 0) {
            return 0;
        }
        final double scale = Math.max(width, 16) * 1.5;
        final double n = context.irregularityNoise.noise(x / scale, y / scale) * 0.7 + context.irregularityNoise.noise(x / (scale / 3), y / (scale / 3)) * 0.3;
        return (float) (n * irregularity * width * 0.5);
    }

    /**
     * Sample the height of the original map near the block of its edge closest to a location outside it. Close to the
     * edge this is the height of that block; further away it is a Gaussian average of an area of the map around that
     * block which grows with the distance, so that features of the edge do not produce streaks.
     */
    private static float sampleEdgeHeight(Context context, int edgeX, int edgeY, float distance) {
        final float fine = context.dimension.getHeightAt(edgeX, edgeY);
        final float spacing = (2 + distance * 0.75f) / BLUR_RADIUS;
        // Sample from the level of detail matching the sample spacing, to avoid aliasing
        final float level = (float) Math.max(0, Math.min(MIP_LEVELS - 1, Math.log(spacing / (1 << COARSE_CELL_BITS)) / Math.log(2)));
        // Weight each sample by how much of it lies inside the original map, so that samples fade out smoothly
        // instead of switching off when they cross the edge (which would produce visible lines)
        final float[] sample = new float[2];
        float sum = 0, totalWeight = 0;
        for (int i = -BLUR_RADIUS; i <= BLUR_RADIUS; i++) {
            for (int j = -BLUR_RADIUS; j <= BLUR_RADIUS; j++) {
                context.smoothHeight(edgeX + 0.5f + i * spacing, edgeY + 0.5f + j * spacing, level, sample);
                final float weight = BLUR_WEIGHTS[(i + BLUR_RADIUS) * (2 * BLUR_RADIUS + 1) + j + BLUR_RADIUS];
                sum += sample[0] * weight;
                totalWeight += sample[1] * weight;
            }
        }
        final float blurred = (totalWeight > 0.0001f) ? sum / totalWeight : fine;
        final float t = (float) ProceduralTerrain.smoothStep(0, 12, distance);
        return fine + (blurred - fine) * t;
    }

    private static float distanceToTile(Point a, Point b) {
        final int dx = Math.max(Math.abs(a.x - b.x) - 1, 0) * TILE_SIZE, dy = Math.max(Math.abs(a.y - b.y) - 1, 0) * TILE_SIZE;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean hasMissingNeighbour(Point coords, Set<Point> tileCoords, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (! tileCoords.contains(new Point(coords.x + dx, coords.y + dy))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int clampedIndex(int x, int y) {
        return Math.max(0, Math.min(TILE_SIZE - 1, x)) | (Math.max(0, Math.min(TILE_SIZE - 1, y)) << TILE_SIZE_BITS);
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private int width = 128, targetHeight = 66, waterLevel = 62;
    private Placement placement = Placement.OUTSIDE;
    private Curve curve = Curve.SMOOTH;
    private float irregularity = 0.5f;
    private ProceduralTerrainSettings target;
    private VanillaBiomeSource vanillaTarget;
    private Terrain targetTerrain = Terrain.GRASS;
    private boolean applyTerrain = true, applyWater = true;
    private long seed;
    private final SimplexNoise clumpNoise = new SimplexNoise(0x61C8864680B583EBL);

    /**
     * The water level of Minecraft's oceans (the top of the water is at 62).
     */
    public static final int VANILLA_SEA_LEVEL = 63;
    private static final int NONE = Integer.MIN_VALUE;
    private static final int BLUR_RADIUS = 2, COARSE_CELL_BITS = 2, MIP_LEVELS = TILE_SIZE_BITS - COARSE_CELL_BITS + 1;
    private static final float[] BLUR_WEIGHTS = new float[(2 * BLUR_RADIUS + 1) * (2 * BLUR_RADIUS + 1)];

    static {
        for (int i = -BLUR_RADIUS; i <= BLUR_RADIUS; i++) {
            for (int j = -BLUR_RADIUS; j <= BLUR_RADIUS; j++) {
                BLUR_WEIGHTS[(i + BLUR_RADIUS) * (2 * BLUR_RADIUS + 1) + j + BLUR_RADIUS] = (float) Math.exp(-2.0 * (i * i + j * j) / (BLUR_RADIUS * BLUR_RADIUS));
            }
        }
    }

    private final class Context {
        Context(Dimension dimension, Set<Point> original, Map<Point, boolean[]> notPresent, ProceduralTerrain targetTerrainGenerator, SimplexNoise irregularityNoise) {
            this.dimension = dimension;
            this.original = original;
            this.notPresent = notPresent;
            this.targetTerrainGenerator = targetTerrainGenerator;
            this.irregularityNoise = irregularityNoise;
            if (targetTerrainGenerator != null) {
                surface = targetTerrainGenerator;
            } else {
                // Flat target: use the target terrain, with rock on steep slopes and beaches near the water
                final ProceduralTerrainSettings rules = ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.FLAT_PLAINS, seed);
                rules.setDefaultTerrain(targetTerrain);
                surface = new ProceduralTerrain(rules);
            }
        }

        /**
         * Whether a block was part of the original map: its tile was, and the block was present.
         */
        boolean isPresent(int x, int y) {
            final Point coords = new Point(x >> TILE_SIZE_BITS, y >> TILE_SIZE_BITS);
            if (! original.contains(coords)) {
                return false;
            }
            final boolean[] missing = notPresent.get(coords);
            return (missing == null) || (! missing[(x & TILE_SIZE_MASK) | ((y & TILE_SIZE_MASK) << TILE_SIZE_BITS)]);
        }

        /**
         * For each block of a tile, the nearest block (within reach) which was part of the original map, or which was
         * not, found with a two pass vector distance transform over the tile and a margin around it.
         *
         * @param present Whether to look for blocks which were part of the original map ({@code true}) or which were
         *                not ({@code false}).
         */
        NearestBlocks nearest(Tile tile, boolean present, int reach) {
            final int size = TILE_SIZE + 2 * reach;
            final int wx0 = (tile.getX() << TILE_SIZE_BITS) - reach, wy0 = (tile.getY() << TILE_SIZE_BITS) - reach;
            // The offset to the nearest block found so far, per cell of the window; NONE where there is none yet
            final int[] dx = new int[size * size], dy = new int[size * size];
            Arrays.fill(dx, NONE);
            for (int ty = wy0 >> TILE_SIZE_BITS; ty <= (wy0 + size - 1) >> TILE_SIZE_BITS; ty++) {
                for (int tx = wx0 >> TILE_SIZE_BITS; tx <= (wx0 + size - 1) >> TILE_SIZE_BITS; tx++) {
                    final Point coords = new Point(tx, ty);
                    final boolean inMap = original.contains(coords);
                    final boolean[] missing = inMap ? notPresent.get(coords) : null;
                    if ((inMap != present) && (missing == null)) {
                        // No block of this tile qualifies
                        continue;
                    }
                    final int bx0 = Math.max(wx0, tx << TILE_SIZE_BITS), bx1 = Math.min(wx0 + size, (tx + 1) << TILE_SIZE_BITS);
                    final int by0 = Math.max(wy0, ty << TILE_SIZE_BITS), by1 = Math.min(wy0 + size, (ty + 1) << TILE_SIZE_BITS);
                    for (int by = by0; by < by1; by++) {
                        for (int bx = bx0; bx < bx1; bx++) {
                            final boolean blockPresent = inMap && ((missing == null) || (! missing[(bx & TILE_SIZE_MASK) | ((by & TILE_SIZE_MASK) << TILE_SIZE_BITS)]));
                            if (blockPresent == present) {
                                final int i = (by - wy0) * size + (bx - wx0);
                                dx[i] = 0;
                                dy[i] = 0;
                            }
                        }
                    }
                }
            }
            // Forward pass, then backward pass, each with a sweep back along the row
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    propagate(dx, dy, size, x, y, -1, 0);
                    propagate(dx, dy, size, x, y, -1, -1);
                    propagate(dx, dy, size, x, y, 0, -1);
                    propagate(dx, dy, size, x, y, 1, -1);
                }
                for (int x = size - 1; x >= 0; x--) {
                    propagate(dx, dy, size, x, y, 1, 0);
                }
            }
            for (int y = size - 1; y >= 0; y--) {
                for (int x = size - 1; x >= 0; x--) {
                    propagate(dx, dy, size, x, y, 1, 0);
                    propagate(dx, dy, size, x, y, 1, 1);
                    propagate(dx, dy, size, x, y, 0, 1);
                    propagate(dx, dy, size, x, y, -1, 1);
                }
                for (int x = 0; x < size; x++) {
                    propagate(dx, dy, size, x, y, -1, 0);
                }
            }
            final NearestBlocks result = new NearestBlocks();
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    final int i = (y + reach) * size + x + reach, j = x | (y << TILE_SIZE_BITS);
                    if (dx[i] == NONE) {
                        result.distance[j] = Float.MAX_VALUE;
                    } else {
                        result.x[j] = wx0 + x + reach + dx[i];
                        result.y[j] = wy0 + y + reach + dy[i];
                        // Distances between the centres of the blocks; adjacent blocks are half a block from the edge
                        result.distance[j] = Math.max(0.5f, (float) Math.sqrt((double) dx[i] * dx[i] + (double) dy[i] * dy[i]) - 0.5f);
                    }
                }
            }
            return result;
        }

        private void propagate(int[] dx, int[] dy, int size, int x, int y, int ox, int oy) {
            final int nx = x + ox, ny = y + oy;
            if ((nx < 0) || (ny < 0) || (nx >= size) || (ny >= size)) {
                return;
            }
            final int n = ny * size + nx;
            if (dx[n] == NONE) {
                return;
            }
            final int cx = dx[n] + ox, cy = dy[n] + oy, i = y * size + x;
            if ((dx[i] == NONE) || ((long) cx * cx + (long) cy * cy < (long) dx[i] * dx[i] + (long) dy[i] * dy[i])) {
                dx[i] = cx;
                dy[i] = cy;
            }
        }

        float targetHeight(int x, int y) {
            if (vanillaHeights != null) {
                return vanillaHeight(x, y);
            }
            return (targetTerrainGenerator != null) ? targetTerrainGenerator.getHeight(x, y) : targetHeight;
        }

        /**
         * Work out the vanilla height and biome, at Minecraft's biome resolution (every four blocks), for the area
         * covered by the tiles which will be changed.
         */
        void sampleVanilla(VanillaBiomeSource source, List<Tile> newTiles, List<Tile> edgeTiles) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (List<Tile> tiles: List.of(newTiles, edgeTiles)) {
                for (Tile tile: tiles) {
                    minX = Math.min(minX, tile.getX());
                    minY = Math.min(minY, tile.getY());
                    maxX = Math.max(maxX, tile.getX());
                    maxY = Math.max(maxY, tile.getY());
                }
            }
            if (minX > maxX) {
                return;
            }
            // In biome coordinates, with a margin of one for the interpolation
            quartX0 = (minX << TILE_SIZE_BITS >> 2) - 1;
            quartY0 = (minY << TILE_SIZE_BITS >> 2) - 1;
            quartWidth = ((maxX - minX + 1) << TILE_SIZE_BITS >> 2) + 3;
            quartHeight = ((maxY - minY + 1) << TILE_SIZE_BITS >> 2) + 3;
            final float[] heights = new float[quartWidth * quartHeight];
            final int[] biomes = new int[quartWidth * quartHeight];
            java.util.stream.IntStream.range(0, quartHeight).parallel().forEach(qy -> {
                for (int qx = 0; qx < quartWidth; qx++) {
                    final int i = qy * quartWidth + qx;
                    final int biome = source.getSurfaceBiome(quartX0 + qx, quartY0 + qy);
                    heights[i] = vanillaSurfaceHeight(source.getApproximateSurfaceHeight(quartX0 + qx, quartY0 + qy), VanillaBiomes.getCategory(biome));
                    biomes[i] = biome;
                }
            });
            vanillaHeights = heights;
            vanillaBiomes = biomes;
        }

        private float vanillaHeight(int x, int y) {
            // Bilinear interpolation between the centres of the biome cells
            final double qx = (x - 1.5) / 4.0 - quartX0, qy = (y - 1.5) / 4.0 - quartY0;
            final int ix = Math.max(0, Math.min(quartWidth - 2, (int) Math.floor(qx))), iy = Math.max(0, Math.min(quartHeight - 2, (int) Math.floor(qy)));
            final float fx = (float) Math.max(0, Math.min(1, qx - ix)), fy = (float) Math.max(0, Math.min(1, qy - iy));
            final int i = iy * quartWidth + ix;
            return (vanillaHeights[i] * (1 - fx) + vanillaHeights[i + 1] * fx) * (1 - fy)
                    + (vanillaHeights[i + quartWidth] * (1 - fx) + vanillaHeights[i + quartWidth + 1] * fx) * fy;
        }

        int vanillaBiome(int x, int y) {
            final int qx = Math.max(0, Math.min(quartWidth - 1, (x >> 2) - quartX0)), qy = Math.max(0, Math.min(quartHeight - 1, (y >> 2) - quartY0));
            return vanillaBiomes[qy * quartWidth + qx];
        }

        private float[] vanillaHeights;
        private int[] vanillaBiomes;
        private int quartX0, quartY0, quartWidth, quartHeight;

        /**
         * Sample the height of the original map at a location, smoothed to a level of detail (0 = averaged over 4x4
         * blocks, each further level doubling the size), with bilinear interpolation within and between levels.
         *
         * @param result Receives the coverage-weighted height in the first element and the coverage (the fraction of
         *               the sample which lies inside the original map, 0-1) in the second.
         */
        void smoothHeight(float x, float y, float level, float[] result) {
            final int level0 = (int) level;
            final float fraction = level - level0;
            bilinearHeight(x, y, level0, result);
            if ((fraction > 0) && (level0 + 1 < MIP_LEVELS)) {
                final float sum0 = result[0], coverage0 = result[1];
                bilinearHeight(x, y, level0 + 1, result);
                result[0] = sum0 + (result[0] - sum0) * fraction;
                result[1] = coverage0 + (result[1] - coverage0) * fraction;
            }
        }

        private void bilinearHeight(float x, float y, int level, float[] result) {
            final int cellSize = 1 << (COARSE_CELL_BITS + level);
            final float gx = x / cellSize - 0.5f, gy = y / cellSize - 0.5f;
            final int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy);
            final float fx = gx - ix, fy = gy - iy;
            float sum = 0, coverage = 0;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    final float h = cellHeight(ix + dx, iy + dy, level);
                    if (! Float.isNaN(h)) {
                        final float weight = ((dx == 0) ? (1 - fx) : fx) * ((dy == 0) ? (1 - fy) : fy);
                        sum += h * weight;
                        coverage += weight;
                    }
                }
            }
            result[0] = sum;
            result[1] = coverage;
        }

        /**
         * The average height of a cell, in global cell coordinates for the specified level.
         */
        private float cellHeight(int cellX, int cellY, int level) {
            final int cellBits = COARSE_CELL_BITS + level, cellsPerTileBits = TILE_SIZE_BITS - cellBits;
            final int tileX = cellX >> cellsPerTileBits, tileY = cellY >> cellsPerTileBits;
            final long key = ((long) tileX << 32) | (tileY & 0xffffffffL);
            float[][] mips = coarseHeights.get(key);
            if (mips == null) {
                if (! original.contains(new Point(tileX, tileY))) {
                    return Float.NaN;
                }
                mips = coarseHeights.computeIfAbsent(key, k -> calculateMips(dimension.getTile(tileX, tileY)));
            }
            final int mask = (1 << cellsPerTileBits) - 1;
            return mips[level][((cellY & mask) << cellsPerTileBits) | (cellX & mask)];
        }

        private float[][] calculateMips(Tile tile) {
            final float[][] mips = new float[MIP_LEVELS][];
            int cells = TILE_SIZE >> COARSE_CELL_BITS;
            final int cellSize = 1 << COARSE_CELL_BITS;
            // Average only the blocks which were present; cells without any are NaN
            final boolean[] missing = notPresent.get(new Point(tile.getX(), tile.getY()));
            mips[0] = new float[cells * cells];
            final int[] counts = new int[cells * cells];
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    if ((missing == null) || (! missing[x | (y << TILE_SIZE_BITS)])) {
                        final int cell = ((y >> COARSE_CELL_BITS) * cells) + (x >> COARSE_CELL_BITS);
                        mips[0][cell] += tile.getHeight(x, y);
                        counts[cell]++;
                    }
                }
            }
            for (int i = 0; i < mips[0].length; i++) {
                mips[0][i] = (counts[i] > 0) ? mips[0][i] / counts[i] : Float.NaN;
            }
            for (int level = 1; level < MIP_LEVELS; level++) {
                final int previousCells = cells;
                cells >>= 1;
                final float[] previous = mips[level - 1], current = new float[cells * cells];
                for (int cx = 0; cx < cells; cx++) {
                    for (int cy = 0; cy < cells; cy++) {
                        float sum = 0;
                        int count = 0;
                        for (int i: new int[] {(2 * cy) * previousCells + 2 * cx, (2 * cy) * previousCells + 2 * cx + 1, (2 * cy + 1) * previousCells + 2 * cx, (2 * cy + 1) * previousCells + 2 * cx + 1}) {
                            if (! Float.isNaN(previous[i])) {
                                sum += previous[i];
                                count++;
                            }
                        }
                        current[cy * cells + cx] = (count > 0) ? sum / count : Float.NaN;
                    }
                }
                mips[level] = current;
            }
            return mips;
        }

        final Dimension dimension;
        final Set<Point> original;
        /**
         * For the tiles of the original map which were only partly present, which blocks were not.
         */
        final Map<Point, boolean[]> notPresent;
        final ProceduralTerrain targetTerrainGenerator, surface;
        final SimplexNoise irregularityNoise;
        private final java.util.concurrent.ConcurrentHashMap<Long, float[][]> coarseHeights = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * For each block of a tile, the nearest block of some kind: its coordinates, and the distance to its edge
     * ({@link Float#MAX_VALUE} if there is none within reach).
     */
    private static final class NearestBlocks {
        final int[] x = new int[TILE_SIZE * TILE_SIZE], y = new int[TILE_SIZE * TILE_SIZE];
        final float[] distance = new float[TILE_SIZE * TILE_SIZE];
    }

    /**
     * Where the transition zone lies relative to the current edge of the map.
     */
    public enum Placement {
        INSIDE("Inside the map (fade the existing edge)", 0.0f),
        CENTRED("Across the edge (half inside, half outside)", 0.5f),
        OUTSIDE("Outside the map (extend the map)", 1.0f);

        Placement(String displayName, float outsideFraction) {
            this.displayName = displayName;
            this.outsideFraction = outsideFraction;
        }

        /**
         * The fraction of the transition zone which lies outside the current edge of the map.
         */
        public float getOutsideFraction() {
            return outsideFraction;
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
        final float outsideFraction;
    }

    /**
     * The shape of the transition.
     */
    public enum Curve {
        LINEAR("Linear"),
        SMOOTH("Smooth (S-curve)"),
        GENTLE_START("Gentle near the map"),
        GENTLE_END("Gentle near the target");

        Curve(String displayName) {
            this.displayName = displayName;
        }

        /**
         * @param t The position in the transition zone, from zero (fully target) to one (fully original).
         * @return The weight of the original terrain.
         */
        public float apply(float t) {
            switch (this) {
                case LINEAR:
                    return t;
                case SMOOTH:
                    return t * t * (3 - 2 * t);
                case GENTLE_START:
                    // Changes slowly near the original map
                    return 1 - (1 - t) * (1 - t);
                case GENTLE_END:
                    // Changes slowly near the target
                    return t * t;
                default:
                    throw new InternalError();
            }
        }

        @Override
        public String toString() {
            return displayName;
        }

        private final String displayName;
    }
}
