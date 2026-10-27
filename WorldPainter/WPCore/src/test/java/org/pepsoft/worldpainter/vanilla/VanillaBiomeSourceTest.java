package org.pepsoft.worldpainter.vanilla;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VanillaBiomeSourceTest {
    /**
     * Reference values computed with cubiomes (MC_1_21_WD): seed, large biomes, biome x, biome z, biome at biome
     * y 15, surface biome.
     */
    private static final long[][] REFERENCE = {
            {0L, 0, -2000, -1466, 23, 23},
            {0L, 0, 716, -1466, 30, 30},
            {1L, 0, -642, -1466, 155, 155},
            {42L, 0, -2000, -1466, 4, 4},
            {42L, 0, 716, -1466, 155, 155},
            {-123456789L, 0, -642, -1466, 4, 4},
            {1311768467294899695L, 0, -2000, -1466, 27, 27},
            {1311768467294899695L, 0, 716, -1466, 21, 21},
            {8675309L, 0, -642, -1466, 44, 44},
            {-1L, 0, -2000, -1466, 16, 16},
            {-1L, 0, 716, -1466, 4, 4},
            {3141592653L, 0, -642, -1466, 38, 38},
            {0L, 1, -2000, -1466, 4, 4},
            {0L, 1, 716, -1466, 0, 0},
            {1L, 1, -642, -1466, 1, 1},
            {42L, 1, -2000, -1466, 2, 2},
            {42L, 1, 716, -1466, 2, 2},
            {-123456789L, 1, -642, -1466, 23, 23},
            {1311768467294899695L, 1, -2000, -1466, 27, 27},
            {1311768467294899695L, 1, 716, -1466, 4, 4},
            {8675309L, 1, -642, -1466, 4, 4},
            {-1L, 1, -2000, -1466, 1, 1},
            {-1L, 1, 716, -1466, 24, 24},
            {3141592653L, 1, -642, -1466, 16, 16}
    };

    @Test
    public void testMatchesCubiomes() {
        for (long[] row: REFERENCE) {
            final VanillaBiomeSource source = new VanillaBiomeSource(row[0], row[1] == 1);
            assertEquals("seed " + row[0] + " at " + row[2] + "," + row[3], (int) row[4], source.getBiome((int) row[2], 15, (int) row[3]));
            assertEquals("surface, seed " + row[0] + " at " + row[2] + "," + row[3], (int) row[5], source.getSurfaceBiome((int) row[2], (int) row[3]));
        }
    }

    @Test
    public void testBiomeInfo() {
        assertEquals("Snowy Plains", VanillaBiomes.getDisplayName(12));
        assertEquals(VanillaBiomes.Category.OCEAN, VanillaBiomes.getCategory(24));
        assertEquals(VanillaBiomes.Category.SWAMP, VanillaBiomes.getCategory(184));
        assertEquals(VanillaBiomes.Category.FOREST, VanillaBiomes.getCategory(185));
        assertTrue(VanillaBiomes.Category.DESERT.similarity(VanillaBiomes.Category.BADLANDS) > VanillaBiomes.Category.DESERT.similarity(VanillaBiomes.Category.OCEAN));
    }
}
