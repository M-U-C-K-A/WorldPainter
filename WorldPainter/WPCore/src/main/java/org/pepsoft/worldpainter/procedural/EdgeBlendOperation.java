package org.pepsoft.worldpainter.procedural;

import org.pepsoft.worldpainter.layers.Biome;
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

        // Find the existing tiles which are close enough to the edge to be affected
        final List<Tile> edgeTiles = new ArrayList<>();
        if (insideWidth > 0) {
            for (Point coords: original) {
                if (hasMissingNeighbour(coords, original, insideRange)) {
                    edgeTiles.add(dimension.getTileForEditing(coords));
                }
            }
        }

        // Process the new tiles first, since they need to sample the unmodified edge of the original map
        final Context context = new Context(dimension, original, targetTerrainGenerator, irregularityNoise);
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
        final List<Rectangle> missing = new ArrayList<>();
        final int range = (width + TILE_SIZE - 1) >> TILE_SIZE_BITS;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (! context.original.contains(new Point(tile.getX() + dx, tile.getY() + dy))) {
                    missing.add(new Rectangle((tile.getX() + dx) << TILE_SIZE_BITS, (tile.getY() + dy) << TILE_SIZE_BITS, TILE_SIZE, TILE_SIZE));
                }
            }
        }
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        final float[] targetWeights = new float[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                final int worldX = x0 + x, worldY = y0 + y;
                final float current = tile.getHeight(x, y);
                final float distance = distanceToNearest(worldX, worldY, missing, null);
                final float weight = originalWeight(distance + irregularity(context, worldX, worldY));
                if (weight >= 1.0f) {
                    heights[i] = current;
                    continue;
                }
                final float targetHeight = context.targetHeight(worldX, worldY);
                final float height = Math.max(minHeight, Math.min(maxHeight, targetHeight + (current - targetHeight) * weight));
                heights[i] = height;
                targetWeights[i] = 1 - weight;
                tile.setHeight(x, y, height);
            }
        }
        applyTerrainAndWater(context, tile, heights, targetWeights, false);
    }

    private void processOutsideTile(Context context, Tile tile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int minHeight = tile.getMinHeight(), maxHeight = tile.getMaxHeight() - 1;
        final List<Rectangle> originals = new ArrayList<>();
        final int range = (width + TILE_SIZE - 1) >> TILE_SIZE_BITS;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                if (context.original.contains(new Point(tile.getX() + dx, tile.getY() + dy))) {
                    originals.add(new Rectangle((tile.getX() + dx) << TILE_SIZE_BITS, (tile.getY() + dy) << TILE_SIZE_BITS, TILE_SIZE, TILE_SIZE));
                }
            }
        }
        final float[] heights = new float[TILE_SIZE * TILE_SIZE];
        final float[] targetWeights = new float[TILE_SIZE * TILE_SIZE];
        final Rectangle[] nearest = new Rectangle[1];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                final int worldX = x0 + x, worldY = y0 + y;
                final float distance = distanceToNearest(worldX, worldY, originals, nearest);
                final float weight = originalWeight(-distance + irregularity(context, worldX, worldY));
                final float targetHeight = context.targetHeight(worldX, worldY);
                float height;
                if ((nearest[0] != null) && (weight > 0)) {
                    final float edgeHeight = sampleEdgeHeight(context, nearest[0], worldX, worldY, distance);
                    height = targetHeight + (edgeHeight - targetHeight) * weight;
                } else {
                    height = targetHeight;
                }
                height = Math.max(minHeight, Math.min(maxHeight, height));
                heights[i] = height;
                targetWeights[i] = 1 - weight;
                tile.setHeight(x, y, height);
            }
        }
        applyTerrainAndWater(context, tile, heights, targetWeights, true);
    }

    /**
     * Set the terrain type and water level in the transition zone.
     *
     * @param newTile Whether the tile was added by this operation, in which case the terrain and water are determined
     *                everywhere (rather than continuing the terrain of the edge, which would produce streaks).
     */
    private void applyTerrainAndWater(Context context, Tile tile, float[] heights, float[] targetWeights, boolean newTile) {
        final int x0 = tile.getX() << TILE_SIZE_BITS, y0 = tile.getY() << TILE_SIZE_BITS;
        final int noWater = tile.getMinHeight();
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                final int i = x | (y << TILE_SIZE_BITS);
                if (newTile || (targetWeights[i] >= 0.5f)) {
                    if (applyTerrain) {
                        final float slope = ProceduralTerrain.slope(heights[clampedIndex(x - 1, y)], heights[clampedIndex(x + 1, y)], heights[clampedIndex(x, y - 1)], heights[clampedIndex(x, y + 1)]);
                        if (context.vanillaBiomes != null) {
                            final int biome = context.vanillaBiome(x0 + x, y0 + y);
                            tile.setTerrain(x, y, vanillaTerrain(VanillaBiomes.getCategory(biome), heights[i], slope));
                            tile.setLayerValue(Biome.INSTANCE, x, y, VanillaBiomes.toWorldPainterBiome(biome));
                        } else {
                            tile.setTerrain(x, y, context.surface.getTerrain(x0 + x, y0 + y, heights[i], slope));
                        }
                    }
                    if (applyWater) {
                        tile.setWaterLevel(x, y, (heights[i] < waterLevel) ? waterLevel : noWater);
                    } else if (newTile) {
                        tile.setWaterLevel(x, y, noWater);
                    }
                } else if (applyWater && (targetWeights[i] > 0) && (heights[i] < waterLevel) && (tile.getWaterLevel(x, y) < waterLevel)) {
                    // Inside the original map, but lowered below the water level
                    tile.setWaterLevel(x, y, waterLevel);
                }
            }
        }
    }

    /**
     * A terrain type which looks like what Minecraft puts on the surface of a biome.
     */
    private static Terrain vanillaTerrain(VanillaBiomes.Category category, float height, float slope) {
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
     * Sample the height of the original map near the edge closest to a location outside it. Close to the edge this is
     * the height of the nearest edge block; further away it is a Gaussian average of an area of the map around that
     * block which grows with the distance, so that features of the edge do not produce streaks.
     */
    private static float sampleEdgeHeight(Context context, Rectangle rect, int x, int y, float distance) {
        final int edgeX = Math.max(rect.x, Math.min(rect.x + rect.width - 1, x));
        final int edgeY = Math.max(rect.y, Math.min(rect.y + rect.height - 1, y));
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

    /**
     * The distance from the centre of a block to the nearest of a set of rectangles.
     *
     * @param nearest If not {@code null}, the nearest rectangle is stored in the first element.
     */
    private static float distanceToNearest(int x, int y, List<Rectangle> rects, Rectangle[] nearest) {
        float best = Float.MAX_VALUE;
        Rectangle bestRect = null;
        final float px = x + 0.5f, py = y + 0.5f;
        for (Rectangle rect: rects) {
            final float dx = Math.max(Math.max(rect.x - px, 0), px - (rect.x + rect.width));
            final float dy = Math.max(Math.max(rect.y - py, 0), py - (rect.y + rect.height));
            final float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < best) {
                best = distanceSquared;
                bestRect = rect;
            }
        }
        if (nearest != null) {
            nearest[0] = bestRect;
        }
        return (bestRect != null) ? (float) Math.sqrt(best) : Float.MAX_VALUE;
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

    /**
     * The water level of Minecraft's oceans (the top of the water is at 62).
     */
    private static final int VANILLA_SEA_LEVEL = 63;
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
        Context(Dimension dimension, Set<Point> original, ProceduralTerrain targetTerrainGenerator, SimplexNoise irregularityNoise) {
            this.dimension = dimension;
            this.original = original;
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
                    float height = source.getApproximateSurfaceHeight(quartX0 + qx, quartY0 + qy);
                    // Keep land above the sea and water below it, as Minecraft's own surface does
                    switch (VanillaBiomes.getCategory(biome)) {
                        case OCEAN:
                            height = Math.min(height, VANILLA_SEA_LEVEL - 6);
                            break;
                        case RIVER:
                            height = Math.min(height, VANILLA_SEA_LEVEL - 3);
                            break;
                        case BEACH:
                            height = Math.max(VANILLA_SEA_LEVEL, Math.min(height, VANILLA_SEA_LEVEL + 4));
                            break;
                        default:
                            height = Math.max(height, VANILLA_SEA_LEVEL + 2);
                            break;
                    }
                    heights[i] = height;
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
            mips[0] = new float[cells * cells];
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    mips[0][((y >> COARSE_CELL_BITS) * cells) + (x >> COARSE_CELL_BITS)] += tile.getHeight(x, y) / (cellSize * cellSize);
                }
            }
            for (int level = 1; level < MIP_LEVELS; level++) {
                final int previousCells = cells;
                cells >>= 1;
                final float[] previous = mips[level - 1], current = new float[cells * cells];
                for (int cx = 0; cx < cells; cx++) {
                    for (int cy = 0; cy < cells; cy++) {
                        current[cy * cells + cx] = (previous[(2 * cy) * previousCells + 2 * cx] + previous[(2 * cy) * previousCells + 2 * cx + 1]
                                + previous[(2 * cy + 1) * previousCells + 2 * cx] + previous[(2 * cy + 1) * previousCells + 2 * cx + 1]) / 4;
                    }
                }
                mips[level] = current;
            }
            return mips;
        }

        final Dimension dimension;
        final Set<Point> original;
        final ProceduralTerrain targetTerrainGenerator, surface;
        final SimplexNoise irregularityNoise;
        private final java.util.concurrent.ConcurrentHashMap<Long, float[][]> coarseHeights = new java.util.concurrent.ConcurrentHashMap<>();
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
