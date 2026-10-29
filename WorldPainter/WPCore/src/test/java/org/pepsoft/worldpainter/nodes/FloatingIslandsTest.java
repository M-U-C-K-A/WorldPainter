package org.pepsoft.worldpainter.nodes;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayerHelper;

import java.awt.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.Dimension.Role.FLOATING_FLOOR;

public class FloatingIslandsTest {
    @Test
    public void testFloatingIslandsBecomeAFloatingLayer() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 62);
        final NodeGraph graph = new NodeGraph();
        final ConstantNode ground = graph.add(new ConstantNode());
        ground.set(ConstantNode.VALUE, 0.1);
        graph.connect(ground, graph.getOutputNode(), 0);
        // One round island in the middle of the map
        final GradientNode disc = graph.add(new GradientNode());
        disc.set(GradientNode.FIT_TO_MAP, false);
        disc.set(GradientNode.CENTRE_X, 256);
        disc.set(GradientNode.CENTRE_Y, 256);
        disc.set(GradientNode.SIZE, 160);
        disc.set(GradientNode.SMOOTH, false);
        final FloatingIslandsNode islands = graph.add(new FloatingIslandsNode());
        islands.set(FloatingIslandsNode.TOP_LOW, 180);
        islands.set(FloatingIslandsNode.TOP_HIGH, 180);
        islands.set(FloatingIslandsNode.THICKNESS, 30);
        graph.connect(disc, islands, 0);
        final NodeGraphOperation operation = new NodeGraphOperation(graph);
        operation.apply(dimension, null);

        TunnelLayer layer = null;
        for (CustomLayer created: operation.getCreatedLayers()) {
            if (created instanceof TunnelLayer) {
                layer = (TunnelLayer) created;
            }
        }
        assertNotNull(layer);
        assertEquals(TunnelLayer.LayerMode.FLOATING, layer.getLayerMode());
        assertTrue(dimension.getBitLayerValueAt(layer, 256, 256));
        assertFalse(dimension.getBitLayerValueAt(layer, 10, 10));
        final Dimension floor = dimension.getWorld().getDimension(new Dimension.Anchor(0, FLOATING_FLOOR, false, layer.getFloorDimensionId()));
        assertNotNull(floor);
        assertEquals(180, floor.getHeightAt(256, 256), 0.01f);
        // The underside: full depth in the middle, shallower near the edge
        final TunnelLayerHelper helper = layer.getHelper(dimension);
        final int middle = helper.calculateBottomLevel(256, 256, -64, 319, 180, 70);
        final int edge = helper.calculateBottomLevel(256, 256, -64, 319, 180, 4);
        assertTrue("middle " + middle + " edge " + edge, middle < edge);
        assertTrue("middle " + middle, middle < 180 - 15);
    }
}
