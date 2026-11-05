package org.pepsoft.worldpainter.nodes;

import org.junit.Test;
import org.pepsoft.util.undo.UndoManager;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayer;
import org.pepsoft.worldpainter.layers.tunnel.TunnelLayerHelper;

import java.awt.*;
import java.beans.PropertyChangeListener;

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

    /**
     * Specks of a few columns around the islands are taken away and pinholes through them are filled, but the islands
     * themselves and small islands of their own stay.
     */
    @Test
    public void testSpecksAndPinholesAreTidiedAway() {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2 * TILE_SIZE, 2 * TILE_SIZE), 62);
        final TunnelLayer layer = new TunnelLayer("Islands", TunnelLayer.LayerMode.FLOATING, Color.YELLOW, dimension.getWorld().getPlatform());
        // A large island across the tiles, with a pinhole in it; a speck of three columns; and a small island of 15 x 15
        for (int x = 100; x < 160; x++) {
            for (int y = 100; y < 160; y++) {
                dimension.setBitLayerValueAt(layer, x, y, ! ((x >= 120) && (x < 123) && (y >= 120) && (y < 123)));
            }
        }
        for (int x = 20; x < 23; x++) {
            dimension.setBitLayerValueAt(layer, x, 20, true);
        }
        for (int x = 200; x < 215; x++) {
            for (int y = 30; y < 45; y++) {
                dimension.setBitLayerValueAt(layer, x, y, true);
            }
        }
        NodeGraphOperation.tidyIslands(dimension, layer, dimension.getTiles(), NodeGraphOperation.MIN_ISLAND_AREA);
        assertFalse(dimension.getBitLayerValueAt(layer, 21, 20));
        assertTrue(dimension.getBitLayerValueAt(layer, 121, 121));
        assertTrue(dimension.getBitLayerValueAt(layer, 130, 130));
        assertTrue(dimension.getBitLayerValueAt(layer, 207, 37));
        assertFalse(dimension.getBitLayerValueAt(layer, 50, 50));
        int count = 0;
        for (int x = 0; x < 2 * TILE_SIZE; x++) {
            for (int y = 0; y < 2 * TILE_SIZE; y++) {
                if (dimension.getBitLayerValueAt(layer, x, y)) {
                    count++;
                }
            }
        }
        assertEquals(60 * 60 + 15 * 15, count);
    }

    /**
     * Undo takes the islands off the map, which is how the editor knows to take their layer out of the layer panels
     * again, and redo puts them back.
     */
    @Test
    public void testUndoTakesTheIslandsOffTheMap() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 62);
        final UndoManager undoManager = new UndoManager(5);
        undoManager.setStopAtClasses(PropertyChangeListener.class, Tile.Listener.class, Biome.class);
        dimension.registerUndoManager(undoManager);
        dimension.armSavePoint();
        final NodeGraph graph = new NodeGraph();
        final ConstantNode ground = graph.add(new ConstantNode());
        ground.set(ConstantNode.VALUE, 0.1);
        graph.connect(ground, graph.getOutputNode(), 0);
        final GradientNode disc = graph.add(new GradientNode());
        disc.set(GradientNode.FIT_TO_MAP, false);
        disc.set(GradientNode.CENTRE_X, 256);
        disc.set(GradientNode.CENTRE_Y, 256);
        disc.set(GradientNode.SIZE, 160);
        disc.set(GradientNode.SMOOTH, false);
        graph.connect(disc, graph.add(new FloatingIslandsNode()), 0);
        final NodeGraphOperation operation = new NodeGraphOperation(graph);
        operation.apply(dimension, null);
        dimension.armSavePoint();
        final CustomLayer layer = operation.getCreatedLayers().stream().filter(created -> created instanceof TunnelLayer).findFirst().orElseThrow();
        assertTrue(dimension.getAllLayers(false).contains(layer));

        assertTrue(undoManager.undo());
        assertFalse(dimension.getAllLayers(false).contains(layer));

        assertTrue(undoManager.redo());
        assertTrue(dimension.getAllLayers(false).contains(layer));
    }
}
