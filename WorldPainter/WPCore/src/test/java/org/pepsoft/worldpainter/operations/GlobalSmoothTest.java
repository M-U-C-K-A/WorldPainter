package org.pepsoft.worldpainter.operations;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.HalfBlocks;

import java.awt.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class GlobalSmoothTest {
    /**
     * A step along the edge between two columns of tiles is smoothed the same on both sides and in every row, also
     * where the rows of tiles meet: each column is smoothed from the heights as they were, not as already smoothed.
     */
    @Test
    public void testStepBetweenTilesIsSmoothedEvenly() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2 * TILE_SIZE, 2 * TILE_SIZE), 60);
        for (int x = 0; x < 2 * TILE_SIZE; x++) {
            for (int y = 0; y < 2 * TILE_SIZE; y++) {
                dimension.setHeightAt(x, y, (x < TILE_SIZE) ? 60 : 80);
            }
        }
        GlobalSmooth.smooth(dimension, 4, null, false, null);
        final float left = dimension.getHeightAt(TILE_SIZE - 1, 10), right = dimension.getHeightAt(TILE_SIZE, 10);
        assertTrue("left " + left, (left > 60) && (left < 70));
        assertTrue("right " + right, (right > 70) && (right < 80));
        assertEquals(140, left + right, 0.01f);
        for (int y = 0; y < 2 * TILE_SIZE; y++) {
            assertEquals("row " + y, left, dimension.getHeightAt(TILE_SIZE - 1, y), 0.001f);
            assertEquals("row " + y, right, dimension.getHeightAt(TILE_SIZE, y), 0.001f);
        }
        // Far from the step the land is flat, and stays as it was
        assertEquals(60, dimension.getHeightAt(40, 40), 0.001f);
        assertEquals(80, dimension.getHeightAt(220, 200), 0.001f);
    }

    /**
     * The same across the edge between two rows of tiles, which are smoothed one after the other.
     */
    @Test
    public void testStepBetweenRowsOfTilesIsSmoothedEvenly() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2 * TILE_SIZE, 2 * TILE_SIZE), 60);
        for (int x = 0; x < 2 * TILE_SIZE; x++) {
            for (int y = 0; y < 2 * TILE_SIZE; y++) {
                dimension.setHeightAt(x, y, (y < TILE_SIZE) ? 60 : 80);
            }
        }
        GlobalSmooth.smooth(dimension, 6, null, false, null);
        final float above = dimension.getHeightAt(10, TILE_SIZE - 1), below = dimension.getHeightAt(10, TILE_SIZE);
        assertEquals(140, above + below, 0.01f);
        assertTrue("above " + above, above > 60);
        for (int x = 0; x < 2 * TILE_SIZE; x++) {
            assertEquals("column " + x, above, dimension.getHeightAt(x, TILE_SIZE - 1), 0.001f);
            assertEquals("column " + x, below, dimension.getHeightAt(x, TILE_SIZE), 0.001f);
        }
    }

    /**
     * Only where the filter lets it, and the smoothed land is marked for half blocks when asked.
     */
    @Test
    public void testFilterAndHalfBlocks() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 60);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                dimension.setHeightAt(x, y, ((x / 4) % 2 == 0) ? 60 : 64);
            }
        }
        // Only the left half of the tile
        final Filter leftHalf = new Filter() {
            @Override
            public float modifyStrength(int x, int y, float strength) {
                return (x < TILE_SIZE / 2) ? strength : 0.0f;
            }

            @Override
            public Filter and(Filter filter) {
                throw new UnsupportedOperationException();
            }
        };
        GlobalSmooth.smooth(dimension, 3, leftHalf, true, null);
        final float smoothed = dimension.getHeightAt(20, 50);
        assertTrue("smoothed " + smoothed, (smoothed > 60) && (smoothed < 64));
        assertEquals(64, dimension.getHeightAt(100, 50), 0.001f);
        assertTrue(dimension.getBitLayerValueAt(HalfBlocks.INSTANCE, 20, 50));
        assertFalse(dimension.getBitLayerValueAt(HalfBlocks.INSTANCE, 100, 50));
    }
}
