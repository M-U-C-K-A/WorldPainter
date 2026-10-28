package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves lakes into the terrain flowing through it: basins with irregular shores, scattered over a grid of cells, each
 * filled with water up to just below the lowest point of its rim, so that the water never stands against an invisible
 * wall. Where each lake goes and how high its water is only depends on the location of its cell, which keeps the lakes
 * seamless.
 */
public class LakeNode extends WaterNode {
    @Override
    public String getName() {
        return "Lakes";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SPACING, FREQUENCY, SIZE, DEPTH, SHORE, MAX_HEIGHT, BED, VARIANT);
    }

    @Override
    Terrain getBedTerrain() {
        return get(BED);
    }

    @Override
    MinecraftBiome getBiome() {
        return null;
    }

    @Override
    void prepare(CompileContext context, CompiledNode input) {
        seed = seed(context, getInt(VARIANT));
        spacing = getDouble(SPACING);
        frequency = getDouble(FREQUENCY);
        size = getDouble(SIZE);
        depth = getDouble(DEPTH);
        shoreSlope = getDouble(SHORE);
        maxHeight = getDouble(MAX_HEIGHT);
        shoreNoise = new SimplexNoise(seed + 7);
        // The water level is taken from the terrain smoothed over the lake, so that small bumps don't matter
        final BlurNode blur = new BlurNode();
        blur.set(BlurNode.RADIUS, Math.max(16, size / 3));
        smoothed = blur.compile(context, new CompiledNode[] {input});
        minHeight = context.getMinHeight();
        verticalScale = context.getVerticalScale();
    }

    @Override
    void carve(Evaluation evaluation, Region region, float[] heights, float[] levels, float[] masks) {
        final List<Lake> lakes = findLakes(evaluation, region);
        if (lakes.isEmpty()) {
            return;
        }
        final int width = region.getWidth(), height = region.getHeight();
        for (int j = 0; j < height; j++) {
            if ((j & 0xf) == 0) {
                evaluation.checkCancelled();
            }
            final double y = region.y(j);
            for (int i = 0; i < width; i++) {
                final int index = i + j * width;
                final double x = region.x(i);
                final double terrain = heights[index];
                double carved = terrain;
                for (Lake lake: lakes) {
                    final double dx = x - lake.x, dy = y - lake.y;
                    final double reach = lake.radius * (1 + SHORE_WIDTH) * 1.3;
                    if ((Math.abs(dx) > reach) || (Math.abs(dy) > reach)) {
                        continue;
                    }
                    // An irregular shore line
                    final double wobble = 1 + 0.22 * shoreNoise.noise(x / (lake.radius * 0.6), y / (lake.radius * 0.6));
                    final double distance = Math.sqrt(dx * dx + dy * dy) / wobble;
                    final double candidate;
                    if (distance < lake.radius) {
                        final double t = distance / lake.radius;
                        candidate = Math.min(terrain, lake.level - Math.max(1, depth) * (1 - t * t) - 0.5);
                    } else {
                        // Shores slope up from the water and blend back into the terrain further out
                        final double shore = Math.min(terrain, lake.level + shoreSlope * (distance - lake.radius));
                        final double shoreWidth = lake.radius * SHORE_WIDTH;
                        final double blend = 1 - HeightMaskNode.rise(distance, lake.radius + 0.5 * shoreWidth, lake.radius + shoreWidth);
                        candidate = terrain + (shore - terrain) * blend;
                    }
                    if (candidate < carved) {
                        carved = candidate;
                    }
                    if ((distance < lake.radius) && (candidate < lake.level) && (! (levels[index] >= lake.level))) {
                        levels[index] = (float) lake.level;
                    }
                    masks[index] = Math.max(masks[index], (float) (1 - HeightMaskNode.rise(distance, lake.radius * 0.95, lake.radius * 1.05)));
                }
                heights[index] = (float) carved;
            }
        }
    }

    /**
     * Find the lakes which may affect a region. Whether a cell has a lake, and its size and level, only depend on the
     * cell, so every region finds the same lakes.
     */
    private List<Lake> findLakes(Evaluation evaluation, Region region) {
        final double reach = size * MAX_SIZE_FACTOR * (1 + SHORE_WIDTH) * 1.3;
        final long cellX1 = (long) Math.floor((region.getX0() - reach) / spacing), cellX2 = (long) Math.floor((region.x(region.getWidth() - 1) + reach) / spacing);
        final long cellY1 = (long) Math.floor((region.getY0() - reach) / spacing), cellY2 = (long) Math.floor((region.y(region.getHeight() - 1) + reach) / spacing);
        final List<Lake> lakes = new ArrayList<>();
        for (long cellX = cellX1; cellX <= cellX2; cellX++) {
            for (long cellY = cellY1; cellY <= cellY2; cellY++) {
                final int salt = (int) (cellX * 0x1F1F1F1F + cellY * 0x2E2E2E2E);
                if (SurfaceRules.random((int) cellX, (int) cellY, (int) seed ^ salt) >= frequency) {
                    continue;
                }
                final double x = (cellX + 0.15 + 0.7 * SurfaceRules.random((int) cellX, (int) cellY, (int) seed + 1)) * spacing;
                final double y = (cellY + 0.15 + 0.7 * SurfaceRules.random((int) cellX, (int) cellY, (int) seed + 2)) * spacing;
                final double radius = size * (MIN_SIZE_FACTOR + (MAX_SIZE_FACTOR - MIN_SIZE_FACTOR) * SurfaceRules.random((int) cellX, (int) cellY, (int) seed + 3));
                // The water must be lower than the rim all around
                double lowest = smoothedHeight(evaluation, region, x, y);
                for (int k = 0; k < RIM_SAMPLES; k++) {
                    final double angle = 2 * Math.PI * k / RIM_SAMPLES;
                    lowest = Math.min(lowest, smoothedHeight(evaluation, region, x + Math.cos(angle) * radius * 1.15, y + Math.sin(angle) * radius * 1.15));
                }
                // (The tolerance absorbs rounding errors of the smoothing, which would otherwise lower whole lakes a block)
                final double level = Math.floor(lowest + 0.001) - 1;
                if (level <= maxHeight) {
                    lakes.add(new Lake(x, y, radius, level));
                }
            }
        }
        return lakes;
    }

    private double smoothedHeight(Evaluation evaluation, Region region, double x, double y) {
        return minHeight + evaluation.evaluate(smoothed, new Region(x, y, region.getStep(), 1, 1))[0] * verticalScale;
    }

    public static final NumberParameter SPACING = new NumberParameter("spacing", "Spacing", "The size of the cells over which the lakes are scattered", 700, 100, 8000, 1, true, "blocks");
    public static final NumberParameter FREQUENCY = new NumberParameter("frequency", "Frequency", "The fraction of the cells that have a lake", 0.35, 0, 1, 0.01, false, null);
    public static final NumberParameter SIZE = new NumberParameter("size", "Size", "The typical radius of the lakes", 90, 8, 1024, 1, true, "blocks");
    public static final NumberParameter DEPTH = new NumberParameter("depth", "Depth", "The depth of the water in the middle", 8, 1, 64, 1, false, "blocks");
    public static final NumberParameter SHORE = new NumberParameter("shore", "Shore slope", "How steeply the shores rise from the water (blocks up per block across)", 0.4, 0.05, 3, 0.05, true, null);
    public static final NumberParameter MAX_HEIGHT = new NumberParameter("maxHeight", "Up to height", "No lakes are made higher than this", 220, -2048, 4096, 1, false, "blocks");
    public static final ChoiceParameter<Terrain> BED = new ChoiceParameter<>("bed", "Bed", "The terrain type under the water", Terrain.SAND, getBedChoices());

    private static final double MIN_SIZE_FACTOR = 0.7, MAX_SIZE_FACTOR = 1.3, SHORE_WIDTH = 0.8;
    private static final int RIM_SAMPLES = 8;

    private transient long seed;
    private transient double spacing, frequency, size, depth, shoreSlope, maxHeight, minHeight, verticalScale;
    private transient SimplexNoise shoreNoise;
    private transient CompiledNode smoothed;

    private static final long serialVersionUID = 1L;

    private static final class Lake {
        Lake(double x, double y, double radius, double level) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.level = level;
        }

        final double x, y, radius, level;
    }
}
