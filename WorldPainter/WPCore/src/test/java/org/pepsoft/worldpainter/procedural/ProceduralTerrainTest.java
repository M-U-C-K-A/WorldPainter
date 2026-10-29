package org.pepsoft.worldpainter.procedural;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;

import java.awt.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class ProceduralTerrainTest {
    @Test
    public void testPresetsAreDeterministicAndInRange() {
        for (ProceduralTerrainSettings.Preset preset: ProceduralTerrainSettings.Preset.values()) {
            final ProceduralTerrain terrain1 = new ProceduralTerrain(ProceduralTerrainSettings.create(preset, 12345L));
            final ProceduralTerrain terrain2 = new ProceduralTerrain(ProceduralTerrainSettings.create(preset, 12345L));
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (int x = -5000; x < 5000; x += 37) {
                for (int y = -5000; y < 5000; y += 41) {
                    final float h = terrain1.getHeight(x, y);
                    assertFalse(preset + " produced NaN", Float.isNaN(h));
                    assertEquals(h, terrain2.getHeight(x, y), 0.0f);
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                }
            }
            assertTrue(preset + " min " + min, min > -64);
            assertTrue(preset + " max " + max, max < 320);
            assertTrue(preset + " is flat: " + min + " - " + max, max - min > 4);
        }
    }

    @Test
    public void testDifferentSeedsDiffer() {
        final ProceduralTerrain terrain1 = new ProceduralTerrain(ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.ALPINE, 1L));
        final ProceduralTerrain terrain2 = new ProceduralTerrain(ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.ALPINE, 2L));
        int different = 0;
        for (int x = 0; x < 2000; x += 100) {
            if (Math.abs(terrain1.getHeight(x, x) - terrain2.getHeight(x, x)) > 0.5f) {
                different++;
            }
        }
        assertTrue(different > 10);
    }

    @Test
    public void testApplyIsSeamlessAcrossTiles() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 62);
        final ProceduralTerrainSettings settings = ProceduralTerrainSettings.create(ProceduralTerrainSettings.Preset.ROLLING_HILLS, 42L);
        new ProceduralTerrainOperation(settings).apply(dimension, null);
        final ProceduralTerrain terrain = new ProceduralTerrain(settings);
        for (int x = 0; x < 4 * TILE_SIZE; x += 7) {
            for (int y = 0; y < 4 * TILE_SIZE; y += 11) {
                // Heights are stored with 1/256 precision
                assertEquals(terrain.getHeight(x, y), dimension.getHeightAt(x, y), 0.01f);
            }
        }
        // No discontinuity at tile boundaries
        for (int y = 0; y < 4 * TILE_SIZE; y++) {
            assertTrue(Math.abs(dimension.getHeightAt(TILE_SIZE - 1, y) - dimension.getHeightAt(TILE_SIZE, y)) < 3);
        }
    }

    @Test
    public void testEdgeBlendOutside() throws Exception {
        // A 2x2 tile map at height 150, which should be extended with a gradual slope down to 66
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2 * TILE_SIZE, 2 * TILE_SIZE), 150);
        final EdgeBlendOperation operation = new EdgeBlendOperation();
        operation.setWidth(200);
        operation.setPlacement(EdgeBlendOperation.Placement.OUTSIDE);
        operation.setIrregularity(0);
        operation.setTargetHeight(66);
        operation.apply(dimension, null);

        assertTrue("Tiles should have been added", dimension.getTileCount() > 4);
        // Original map unchanged
        assertEquals(150, dimension.getHeightAt(100, 100), 0.01f);
        assertEquals(150, dimension.getHeightAt(2 * TILE_SIZE - 1, 100), 0.01f);
        // Continuous at the edge
        assertTrue(Math.abs(dimension.getHeightAt(2 * TILE_SIZE, 100) - 150) < 2);
        // Monotonically decreasing down to the target over the transition
        float previous = Float.MAX_VALUE;
        for (int x = 2 * TILE_SIZE; x < 2 * TILE_SIZE + 200; x++) {
            final float h = dimension.getHeightAt(x, 100);
            assertTrue("Not decreasing at " + x, h <= previous + 0.01f);
            // No cliffs: the slope stays well below a vertical wall
            assertTrue("Too steep at " + x + ": " + (previous - h), (previous == Float.MAX_VALUE) || (previous - h < 3));
            previous = h;
        }
        assertEquals(66, dimension.getHeightAt(2 * TILE_SIZE + 210, 100), 0.5f);
    }

    @Test
    public void testEdgeBlendInside() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 150);
        final EdgeBlendOperation operation = new EdgeBlendOperation();
        operation.setWidth(100);
        operation.setPlacement(EdgeBlendOperation.Placement.INSIDE);
        operation.setIrregularity(0);
        operation.setTargetHeight(66);
        operation.apply(dimension, null);

        assertEquals("No tiles should have been added", 16, dimension.getTileCount());
        // At the very edge the terrain is (almost) at the target height
        assertTrue(Math.abs(dimension.getHeightAt(0, 2 * TILE_SIZE) - 66) < 2);
        // Beyond the transition zone the terrain is untouched
        assertEquals(150, dimension.getHeightAt(2 * TILE_SIZE, 2 * TILE_SIZE), 0.01f);
        assertEquals(150, dimension.getHeightAt(101, 2 * TILE_SIZE), 0.01f);
        // In between it is in between
        final float halfway = dimension.getHeightAt(50, 2 * TILE_SIZE);
        assertTrue(halfway > 80 && halfway < 140);
    }

    @Test
    public void testEdgeBlendCircularMap() throws Exception {
        // A circular map: the blocks of its tiles outside the circle are not present, and are filled in by the blend
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 150);
        for (int x = 0; x < 4 * TILE_SIZE; x++) {
            for (int y = 0; y < 4 * TILE_SIZE; y++) {
                if (Math.hypot(x + 0.5 - 256, y + 0.5 - 256) > 256) {
                    // (The way the editor marks the blocks outside the circle of a round map)
                    dimension.setBitLayerValueAt(org.pepsoft.worldpainter.layers.NotPresentBlock.INSTANCE, x, y, true);
                }
            }
        }
        final EdgeBlendOperation operation = new EdgeBlendOperation();
        operation.setWidth(100);
        operation.setPlacement(EdgeBlendOperation.Placement.OUTSIDE);
        operation.setIrregularity(0);
        operation.setTargetHeight(66);
        operation.apply(dimension, null);

        // Inside the circle the map is unchanged
        assertEquals(150, dimension.getHeightAt(256, 256), 0.01f);
        assertEquals(150, dimension.getHeightAt(256 - 175, 256 - 175), 0.01f);
        // The corners of the tiles, outside the circle, are filled in and present now
        assertFalse(dimension.getBitLayerValueAt(org.pepsoft.worldpainter.layers.NotPresentBlock.INSTANCE, 5, 5));
        // Going out from the circle along the diagonal, the land goes down towards the target without a cliff
        float previous = Float.MAX_VALUE;
        for (int d = 182; d < 256; d++) {
            final float h = dimension.getHeightAt(256 - d, 256 - d);
            assertTrue("Not decreasing at " + d, h <= previous + 0.01f);
            assertTrue("Too steep at " + d + ": " + (previous - h), (previous == Float.MAX_VALUE) || (previous - h < 4));
            previous = h;
        }
        // Just outside the circle the land is still close to the map, further out close to the target
        assertTrue(dimension.getHeightAt(256 - 184, 256 - 184) > 140);
        assertTrue(dimension.getHeightAt(1, 1) < 100);
    }
}
