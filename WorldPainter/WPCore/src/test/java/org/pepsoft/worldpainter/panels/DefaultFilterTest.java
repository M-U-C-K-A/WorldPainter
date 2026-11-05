package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class DefaultFilterTest {
    @Test
    public void testSelection() {
        final Dimension dimension = createDimension(70);
        dimension.setBitLayerValueAt(SelectionBlock.INSTANCE, 10, 10, true);
        dimension.setBitLayerValueAt(SelectionChunk.INSTANCE, 48, 48, true);

        final DefaultFilter inside = filter(dimension, true, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(1.0f, inside.modifyStrength(10, 10, 1.0f), 0.0f);
        assertEquals(1.0f, inside.modifyStrength(50, 50, 1.0f), 0.0f);
        assertEquals(0.0f, inside.modifyStrength(11, 10, 1.0f), 0.0f);

        final DefaultFilter outside = filter(dimension, false, true, Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(0.0f, outside.modifyStrength(10, 10, 1.0f), 0.0f);
        assertEquals(0.0f, outside.modifyStrength(50, 50, 1.0f), 0.0f);
        assertEquals(1.0f, outside.modifyStrength(11, 10, 1.0f), 0.0f);
    }

    @Test
    public void testHeights() {
        final Dimension dimension = createDimension(70);
        assertEquals(1.0f, filter(dimension, false, false, 60, 80).modifyStrength(10, 10, 1.0f), 0.0f);
        assertEquals(0.0f, filter(dimension, false, false, 80, 100).modifyStrength(10, 10, 1.0f), 0.0f);
        // The lower limit above the upper one: beyond the bars
        assertEquals(0.0f, filter(dimension, false, false, 80, 60).modifyStrength(10, 10, 1.0f), 0.0f);
        assertEquals(1.0f, filter(dimension, false, false, 100, 80).modifyStrength(10, 10, 1.0f), 0.0f);
    }

    @Test
    public void testSelectionAfterChangingDimension() {
        final Dimension first = createDimension(70), second = createDimension(70);
        second.setBitLayerValueAt(SelectionBlock.INSTANCE, 10, 10, true);
        final DefaultFilter filter = filter(first, true, false, Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertEquals(0.0f, filter.modifyStrength(10, 10, 1.0f), 0.0f);
        filter.setDimension(second);
        assertEquals(1.0f, filter.modifyStrength(10, 10, 1.0f), 0.0f);
    }

    @Test
    public void testOnlyOnAndExceptOnSeveralTerrains() {
        final Dimension dimension = createDimension(70);
        dimension.setTerrainAt(1, 1, Terrain.GRASS);
        dimension.setTerrainAt(2, 1, Terrain.STONE);
        dimension.setTerrainAt(3, 1, Terrain.SAND);
        final List<Object> grassAndStone = List.of(Terrain.GRASS, Terrain.STONE);

        final DefaultFilter onlyOn = new DefaultFilter(dimension, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false, true, grassAndStone, false, null, -1, false);
        assertEquals(1.0f, onlyOn.modifyStrength(1, 1, 1.0f), 0.0f);
        assertEquals(1.0f, onlyOn.modifyStrength(2, 1, 1.0f), 0.0f);
        assertEquals(0.0f, onlyOn.modifyStrength(3, 1, 1.0f), 0.0f);

        final DefaultFilter exceptOn = new DefaultFilter(dimension, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false, false, null, true, grassAndStone, -1, false);
        assertEquals(0.0f, exceptOn.modifyStrength(1, 1, 1.0f), 0.0f);
        assertEquals(0.0f, exceptOn.modifyStrength(2, 1, 1.0f), 0.0f);
        assertEquals(1.0f, exceptOn.modifyStrength(3, 1, 1.0f), 0.0f);

        // And they follow when the dimension changes
        final Dimension other = createDimension(70);
        other.setTerrainAt(3, 1, Terrain.STONE);
        onlyOn.setDimension(other);
        assertEquals(1.0f, onlyOn.modifyStrength(3, 1, 1.0f), 0.0f);
    }

    private static DefaultFilter filter(Dimension dimension, boolean inSelection, boolean outsideSelection, int above, int below) {
        return new DefaultFilter(dimension, inSelection, outsideSelection, above, below, false, false, null, false, null, -1, false);
    }

    private static Dimension createDimension(int height) {
        final long seed = 1;
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(seed, Terrain.GRASS, -64, 320, height, 62, false, false);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        dimension.addTile(tileFactory.createTile(0, 0));
        return dimension;
    }

    /**
     * Curvature: a valley running north to south (a V of slopes) is a hollow at its bottom, and a ridge a bump at its
     * top; flat land and even slopes are neither.
     */
    @Test
    public void testCurvature() {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(1, Terrain.GRASS, -64, 320, 64, 0, false, false);
        final Dimension dimension = new Dimension(world, "Surface", 1, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        final Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                // A valley at x = 32, a ridge at x = 96
                final float valley = Math.abs(x - 32), ridge = -Math.abs(x - 96);
                tile.setHeight(x, y, 64 + ((x < 64) ? valley : (ridge + 32)) * 0.5f);
            }
        }
        dimension.addTile(tile);
        final DefaultFilter hollows = new DefaultFilter(dimension, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false, false, null, false, null, -1, false)
                .withCurvature(DefaultFilter.CURVATURE_HOLLOWS, 1.0f);
        final DefaultFilter bumps = new DefaultFilter(dimension, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false, false, null, false, null, -1, false)
                .withCurvature(DefaultFilter.CURVATURE_BUMPS, 1.0f);
        assertEquals(1.0f, hollows.modifyStrength(32, 64, 1.0f), 0.0f);
        assertEquals(0.0f, hollows.modifyStrength(96, 64, 1.0f), 0.0f);
        assertEquals(0.0f, hollows.modifyStrength(16, 64, 1.0f), 0.0f);
        assertEquals(1.0f, bumps.modifyStrength(96, 64, 1.0f), 0.0f);
        assertEquals(0.0f, bumps.modifyStrength(32, 64, 1.0f), 0.0f);
        assertEquals(0.0f, bumps.modifyStrength(110, 64, 1.0f), 0.0f);
    }
}
