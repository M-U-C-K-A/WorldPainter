package org.pepsoft.worldpainter.operations;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.FloodWithLava;

import java.awt.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class WaterSettlingTest {
    /**
     * An island in the sea which is lowered under it fills with the sea, as in Minecraft, instead of staying a dry hole
     * with walls of water around it; a hollow in the land, with no water next to it, stays dry.
     */
    @Test
    public void testLoweredIslandFills() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 50);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                final boolean island = (x >= 20) && (x < 40) && (y >= 20) && (y < 40);
                final boolean hill = (x >= 80) && (x < 120) && (y >= 80) && (y < 120);
                dimension.setHeightAt(x, y, hill ? 70 : 50);
                // The sea at 62, but not over the island, which was land
                dimension.setWaterLevelAt(x, y, (island || hill) ? 0 : 62);
            }
        }
        // A hollow in the hill, below the sea but not next to it
        for (int x = 95; x < 105; x++) {
            for (int y = 95; y < 105; y++) {
                dimension.setHeightAt(x, y, 40);
            }
        }
        final int filled = WaterSettling.settle(dimension, new Rectangle(0, 0, TILE_SIZE, TILE_SIZE));
        assertEquals(20 * 20, filled);
        assertEquals("the middle of the island", 62, dimension.getWaterLevelAt(30, 30));
        assertEquals("the hollow in the hill", 0, dimension.getWaterLevelAt(100, 100));
        assertEquals("the hill", 0, dimension.getWaterLevelAt(85, 85));
    }

    /**
     * Lava flows in as lava, and only within the area.
     */
    @Test
    public void testLavaAndArea() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 50);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                dimension.setWaterLevelAt(x, y, (x < 10) ? 55 : 0);
                dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, x < 10);
            }
        }
        WaterSettling.settle(dimension, new Rectangle(10, 0, 20, TILE_SIZE));
        assertEquals(55, dimension.getWaterLevelAt(20, 50));
        assertTrue(dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, 20, 50));
        assertEquals("outside the area", 0, dimension.getWaterLevelAt(40, 50));
    }
}
