package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.PerlinNoise;

import java.util.Random;

import static org.junit.Assert.assertSame;
import static org.pepsoft.worldpainter.Constants.SMALL_BLOBS;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;

public class StoneMixTest {
    /**
     * The Stone Mix terrain skips the noise where it cannot reach the threshold of a patch; check that it still gives
     * the same blocks as asking the noise everywhere. Below zero it picks stone or deepslate at random, so only the
     * blocks above zero are compared.
     */
    @Test
    public void testSameAsAskingTheNoiseEverywhere() {
        final Random random = new Random(3);
        for (int round = 0; round < 3; round++) {
            final long seed = random.nextLong();
            final PerlinNoise granite = new PerlinNoise(seed + 145827825), diorite = new PerlinNoise(seed + 59606124), andesite = new PerlinNoise(seed + 87772192);
            for (int column = 0; column < 2000; column++) {
                final int x = random.nextInt(20000) - 10000, y = random.nextInt(20000) - 10000;
                for (int z = 0; z < 200; z++) {
                    final Material expected;
                    if (granite.getPerlinNoise(x / SMALL_BLOBS, y / SMALL_BLOBS, z / SMALL_BLOBS) > Terrain.GRANITE_CHANCE) {
                        expected = Material.GRANITE;
                    } else if (diorite.getPerlinNoise(x / SMALL_BLOBS, y / SMALL_BLOBS, z / SMALL_BLOBS) > Terrain.DIORITE_CHANCE) {
                        expected = Material.DIORITE;
                    } else if (andesite.getPerlinNoise(x / SMALL_BLOBS, y / SMALL_BLOBS, z / SMALL_BLOBS) > Terrain.ANDESITE_CHANCE) {
                        expected = Material.ANDESITE;
                    } else {
                        expected = Material.STONE;
                    }
                    assertSame(expected, Terrain.STONE_MIX.getMaterial(JAVA_ANVIL_1_20_5, seed, x, y, z, 200));
                }
            }
        }
    }
}
