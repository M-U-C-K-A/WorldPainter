package org.pepsoft.worldpainter.procedural;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.layers.NotPresent;
import org.pepsoft.worldpainter.vanilla.VanillaBiomeSource;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

public class EdgeBlendOperationTest {
    /**
     * Blending into the seed changes only the transition: the chunks of the added tiles beyond it are left to Minecraft.
     */
    @Test
    public void testOnlyTheTransitionIsAddedWhenBlendingIntoTheSeed() throws Exception {
        final World2 world = new World2(JAVA_ANVIL_1_20_5, -64, 320);
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(1, Terrain.GRASS, -64, 320, 70, 62, false, false);
        final Dimension dimension = new Dimension(world, "Surface", 1, tileFactory, NORMAL_DETAIL);
        world.addDimension(dimension);
        dimension.addTile(tileFactory.createTile(0, 0));
        final EdgeBlendOperation operation = new EdgeBlendOperation();
        operation.setWidth(48);
        operation.setPlacement(EdgeBlendOperation.Placement.OUTSIDE);
        operation.setIrregularity(0.0f);
        operation.setVanillaTarget(new VanillaBiomeSource(12345L, false));
        operation.setSeed(12345L);
        operation.apply(dimension, null);
        assertEquals(9, dimension.getTileCount());
        // Next to the map: part of the transition
        assertFalse(dimension.getBitLayerValueAt(NotPresent.INSTANCE, -8, 64));
        assertFalse(dimension.getBitLayerValueAt(NotPresent.INSTANCE, 64, 135));
        // Wholly beyond it: left to Minecraft
        assertTrue(dimension.getBitLayerValueAt(NotPresent.INSTANCE, -100, 64));
        assertTrue(dimension.getBitLayerValueAt(NotPresent.INSTANCE, 64, 230));
        // The map itself is untouched
        assertFalse(dimension.getBitLayerValueAt(NotPresent.INSTANCE, 64, 64));
    }

    /**
     * Blending again starts from the original edge of the map, replacing the previous blend, instead of extending the
     * map further each time; unless asked to extend it.
     */
    @Test
    public void testBlendingAgainReplacesThePreviousBlend() throws Exception {
        final org.pepsoft.worldpainter.Dimension dimension = org.pepsoft.worldpainter.TestData.createDimension(new java.awt.Rectangle(0, 0, 256, 128), 140);
        final EdgeBlendOperation first = new EdgeBlendOperation();
        first.setWidth(100);
        first.setPlacement(EdgeBlendOperation.Placement.OUTSIDE);
        first.setIrregularity(0);
        first.setTargetHeight(62);
        first.apply(dimension, null);
        final int tiles = dimension.getTileCount();
        final float transition = dimension.getHeightAt(300, 60);
        final EdgeBlendOperation second = new EdgeBlendOperation();
        second.setWidth(100);
        second.setPlacement(EdgeBlendOperation.Placement.OUTSIDE);
        second.setIrregularity(0);
        second.setTargetHeight(80);
        second.apply(dimension, null);
        assertEquals("no more tiles", tiles, dimension.getTileCount());
        assertTrue("blended into the new target from the original edge", dimension.getHeightAt(300, 60) > transition);
        first.apply(dimension, null);
        assertEquals("as the first time", transition, dimension.getHeightAt(300, 60), 0.01f);
        // Extending instead
        first.setRestartFromBase(false);
        first.apply(dimension, null);
        assertTrue("extended", dimension.getTileCount() > tiles);
    }
}
