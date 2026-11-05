package org.pepsoft.worldpainter.paths;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.Biome;

import java.awt.Point;
import java.util.List;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class RiverTest {
    /**
     * A river dug across a hillside flows downhill, its surface never rising, with water in its bed and the river
     * biome, and the land beyond its banks left alone.
     */
    @Test
    public void testRiverFlowsDownhill() {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(1, Terrain.GRASS, -64, 320, 64, 0, false, false);
        final Dimension dimension = new Dimension(world, "Surface", 1, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        for (int tileX = 0; tileX < 2; tileX++) {
            final Tile tile = tileFactory.createTile(tileX, 0);
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    // Falling towards the east, with a bump half way
                    final int worldX = tileX * 128 + x;
                    tile.setHeight(x, y, 100 - worldX * 0.12f + 3 * (float) Math.exp(-Math.pow((worldX - 128) / 12.0, 2)));
                    tile.setWaterLevel(x, y, 0);
                }
            }
            dimension.addTile(tile);
        }
        final PathSettings settings = new PathSettings();
        settings.setRiver(true);
        settings.setRiverDepth(3);
        settings.setDynamicWidth(false);
        // Clicked from the low end to the high end: it still flows downhill
        final PathPlan plan = PathGenerator.plan(dimension, List.of(new Point(230, 64), new Point(20, 64)), settings);
        final float[] surface = plan.getProfile();
        final boolean reversed = plan.getTerrain()[0] < plan.getTerrain()[surface.length - 1];
        for (int k = 1; k < surface.length; k++) {
            final int i = reversed ? (surface.length - 1 - k) : k, before = reversed ? (i + 1) : (i - 1);
            assertTrue("rises at " + i, surface[i] <= surface[before] + 1e-4f);
            assertTrue("above the land at " + i, surface[i] < plan.getTerrain()[i]);
        }
        PathGenerator.apply(dimension, plan, settings, 1);
        // In the middle of the river: water above the bed, and the river biome
        for (int x = 40; x <= 210; x += 34) {
            final float bed = dimension.getHeightAt(x, 64);
            final int water = dimension.getWaterLevelAt(x, 64);
            assertTrue("no water at " + x, water > bed);
            assertEquals(7, dimension.getLayerValueAt(Biome.INSTANCE, x, 64));
        }
        // Well away from it the land is as it was
        assertEquals(100 - 128 * 0.12f + 3, dimension.getHeightAt(128, 100), 0.01f);
        assertEquals(0, dimension.getWaterLevelAt(128, 100));
    }
}
