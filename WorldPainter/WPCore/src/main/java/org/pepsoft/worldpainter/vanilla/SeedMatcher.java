package org.pepsoft.worldpainter.vanilla;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.Biome;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;

/**
 * Judges how well the terrain Minecraft generates around a map, for a particular world seed, matches the edges of the
 * map, so that a seed can be found for which the map blends in with its surroundings: the biomes (a desert map
 * surrounded by desert rather than by ocean), and the height of the land (mountains next to mountains, the sea next to
 * the sea).
 *
 * <p>The score is calibrated against seeds picked at random: 0 is what any seed gets, 1 a perfect match. A high score
 * therefore means the seed matches much better than chance, rather than that some of the land happens to agree.
 *
 * <p>The edges of the map are sampled once when the matcher is created; scoring a seed is then fast and thread safe.
 */
public final class SeedMatcher {
    public SeedMatcher(Dimension dimension, boolean largeBiomes) {
        this.largeBiomes = largeBiomes;
        final Set<Point> tiles = new HashSet<>(dimension.getTileCoords());
        final List<Sample> allSamples = new ArrayList<>();
        final int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (Point coords: tiles) {
            for (int[] direction: directions) {
                if (tiles.contains(new Point(coords.x + direction[0], coords.y + direction[1]))) {
                    continue;
                }
                // This side of the tile is on the edge of the map: sample along it
                for (int along = SPACING / 2; along < TILE_SIZE; along += SPACING) {
                    final int insideX, insideY;
                    if (direction[0] == 0) {
                        insideX = (coords.x << TILE_SIZE_BITS) + along;
                        insideY = (coords.y << TILE_SIZE_BITS) + ((direction[1] < 0) ? INSET : TILE_SIZE - 1 - INSET);
                    } else {
                        insideX = (coords.x << TILE_SIZE_BITS) + ((direction[0] < 0) ? INSET : TILE_SIZE - 1 - INSET);
                        insideY = (coords.y << TILE_SIZE_BITS) + along;
                    }
                    final VanillaBiomes.Category expected = getMapCategory(dimension, insideX, insideY);
                    final float expectedHeight = Math.max(dimension.getHeightAt(insideX, insideY), dimension.getWaterLevelAt(insideX, insideY) - 12);
                    for (int distance: OUTSIDE_DISTANCES) {
                        allSamples.add(new Sample(insideX + direction[0] * (distance + INSET), insideY + direction[1] * (distance + INSET), expected, expectedHeight));
                    }
                }
            }
        }
        // Limit the number of samples, spreading them evenly, to keep scoring fast for large maps
        if (allSamples.size() > MAX_SAMPLES) {
            Collections.shuffle(allSamples, new java.util.Random(0));
            samples = allSamples.subList(0, MAX_SAMPLES).toArray(new Sample[0]);
        } else {
            samples = allSamples.toArray(new Sample[0]);
        }
        // What seeds picked at random score, as the zero of the scale
        final java.util.Random random = new java.util.Random(CALIBRATION_SEED);
        float total = 0;
        for (int i = 0; i < CALIBRATION_SEEDS; i++) {
            total += rawScore(new VanillaBiomeSource(random.nextLong(), largeBiomes), -1);
        }
        chance = (samples.length > 0) ? Math.min(0.9f, total / CALIBRATION_SEEDS) : 0;
    }

    /**
     * The number of locations which are compared for each seed.
     */
    public int getSampleCount() {
        return samples.length;
    }

    /**
     * Score a seed.
     *
     * @return How well the surroundings for the seed match the edges of the map, from 0 (not at all) to 1 (perfectly).
     */
    public float score(long seed) {
        return score(new VanillaBiomeSource(seed, largeBiomes), 0);
    }

    /**
     * Score a seed, giving up early if it cannot beat a threshold.
     *
     * @return The score from 0 to 1, or -1 if it was determined early that the score would not exceed
     * {@code threshold}.
     */
    public float score(VanillaBiomeSource source, float threshold) {
        if (samples.length == 0) {
            return 0;
        }
        final float raw = rawScore(source, (threshold > 0) ? (chance + threshold * (1 - chance)) : -1);
        return (raw < 0) ? -1 : Math.max(0, (raw - chance) / (1 - chance));
    }

    /**
     * The score before calibration: per sample, how alike the biomes are and how close the height of the land is.
     *
     * @param threshold The raw score to beat, or a negative number to always work it out completely.
     * @return The raw score from 0 to 1, or -1 if it was determined early that it would not exceed the threshold.
     */
    private float rawScore(VanillaBiomeSource source, float threshold) {
        final float[] height = new float[1];
        float total = 0;
        for (int i = 0; i < samples.length; i++) {
            final Sample sample = samples[i];
            final VanillaBiomes.Category actual = VanillaBiomes.getCategory(source.getSurfaceBiomeAndHeightAtBlock(sample.x, sample.z, height));
            final float heightMatch = Math.max(0, 1 - Math.abs(height[0] - sample.expectedHeight) / HEIGHT_TOLERANCE);
            total += BIOME_WEIGHT * sample.expected.similarity(actual) + (1 - BIOME_WEIGHT) * heightMatch;
            // Early exit: even if all remaining samples match perfectly, the threshold cannot be beaten
            if ((threshold >= 0) && (i % 4 == 3) && ((total + (samples.length - i - 1)) / samples.length <= threshold)) {
                return -1;
            }
        }
        return total / samples.length;
    }

    private static VanillaBiomes.Category getMapCategory(Dimension dimension, int x, int y) {
        int biome = dimension.getLayerValueAt(Biome.INSTANCE, x, y);
        if (biome == 255) {
            biome = dimension.getAutoBiome(x, y);
        }
        if ((biome < 0) || (biome == 255)) {
            // Unknown; guess from the water
            return (dimension.getWaterLevelAt(x, y) > dimension.getIntHeightAt(x, y)) ? VanillaBiomes.Category.OCEAN : VanillaBiomes.Category.PLAINS;
        }
        return VanillaBiomes.getCategoryOfWorldPainterBiome(biome);
    }

    private final boolean largeBiomes;
    private final Sample[] samples;
    private final float chance;

    private static final int SPACING = 16, INSET = 6, MAX_SAMPLES = 192, CALIBRATION_SEEDS = 40;
    private static final long CALIBRATION_SEED = 20261103L;
    private static final float BIOME_WEIGHT = 0.55f, HEIGHT_TOLERANCE = 24f;
    private static final int[] OUTSIDE_DISTANCES = {32, 96};

    private static final class Sample {
        Sample(int x, int z, VanillaBiomes.Category expected, float expectedHeight) {
            this.x = x;
            this.z = z;
            this.expected = expected;
            this.expectedHeight = expectedHeight;
        }

        final int x, z;
        final VanillaBiomes.Category expected;
        final float expectedHeight;
    }
}
