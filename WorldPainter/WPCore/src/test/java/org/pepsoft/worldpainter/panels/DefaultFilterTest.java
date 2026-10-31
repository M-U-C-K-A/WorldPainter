package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.selection.SelectionBlock;
import org.pepsoft.worldpainter.selection.SelectionChunk;

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
}
