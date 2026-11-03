package org.pepsoft.worldpainter.vanilla;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;

import java.util.Random;

import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class SeedMatcherTest {
    /**
     * Seeds picked at random score around zero, so that a high score means something.
     */
    @Test
    public void testRandomSeedsScoreLow() {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(1, Terrain.GRASS, -64, 320, 70, 62, false, false);
        final Dimension dimension = new Dimension(world, "Surface", 1, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                dimension.addTile(tileFactory.createTile(x, y));
            }
        }
        final SeedMatcher matcher = new SeedMatcher(dimension, false);
        final Random random = new Random(7);
        float total = 0, highest = 0;
        for (int i = 0; i < 60; i++) {
            final float score = matcher.score(random.nextLong());
            assertTrue(score >= 0 && score <= 1);
            total += score;
            highest = Math.max(highest, score);
        }
        assertTrue("average " + (total / 60), total / 60 < 0.25f);
        assertTrue("highest " + highest, highest < 0.9f);
    }
}
