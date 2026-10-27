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
 * Judges how well the terrain Minecraft generates around a map, for a particular world seed, matches the biomes along
 * the edges of the map, so that a seed can be found for which the map blends in with its surroundings (for instance a
 * desert map surrounded by desert rather than by ocean).
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
                    for (int distance: OUTSIDE_DISTANCES) {
                        allSamples.add(new Sample(insideX + direction[0] * (distance + INSET), insideY + direction[1] * (distance + INSET), expected));
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
        float total = 0;
        for (int i = 0; i < samples.length; i++) {
            final Sample sample = samples[i];
            final VanillaBiomes.Category actual = VanillaBiomes.getCategory(source.getSurfaceBiomeAtBlock(sample.x, sample.z));
            total += sample.expected.similarity(actual);
            // Early exit: even if all remaining samples match perfectly, the threshold cannot be beaten
            if ((i % 4 == 3) && ((total + (samples.length - i - 1)) / samples.length <= threshold)) {
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

    private static final int SPACING = 16, INSET = 6, MAX_SAMPLES = 192;
    private static final int[] OUTSIDE_DISTANCES = {32, 96};

    private static final class Sample {
        Sample(int x, int z, VanillaBiomes.Category expected) {
            this.x = x;
            this.z = z;
            this.expected = expected;
        }

        final int x, z;
        final VanillaBiomes.Category expected;
    }
}
