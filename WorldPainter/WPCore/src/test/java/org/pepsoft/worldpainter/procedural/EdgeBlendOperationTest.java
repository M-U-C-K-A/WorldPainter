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
}
