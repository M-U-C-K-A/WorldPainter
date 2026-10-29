package org.pepsoft.worldpainter.nodes;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.MaterialGroup;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.CustomLayer;
import org.pepsoft.worldpainter.layers.bo2.TreePack;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class TreesAndMaterialsTest {
    @Test
    public void testTreePackObjectsHaveWoodAndLeaves() {
        for (TreePack.Kind kind: TreePack.Kind.values()) {
            final Bo2Layer layer = TreePack.createLayer(kind);
            assertEquals(kind.getLayerName(), layer.getName());
            final java.util.List<WPObject> objects = layer.getObjectProvider().getAllObjects();
            assertFalse(objects.isEmpty());
            for (WPObject object: objects) {
                final Point3i size = object.getDimensions();
                int wood = 0, leaves = 0;
                for (int x = 0; x < size.x; x++) {
                    for (int y = 0; y < size.y; y++) {
                        for (int z = 0; z < size.z; z++) {
                            if (object.getMask(x, y, z)) {
                                final String name = object.getMaterial(x, y, z).name;
                                if (name.endsWith("_log")) {
                                    wood++;
                                } else if (name.endsWith("_leaves")) {
                                    leaves++;
                                }
                            }
                        }
                    }
                }
                assertTrue(object.getName() + " has no wood", wood > 0);
                assertTrue(object.getName() + " has no leaves", (leaves > 0) || (kind == TreePack.Kind.DEAD));
            }
        }
    }

    @Test
    public void testPlantPresetsHavePlants() {
        for (org.pepsoft.worldpainter.layers.plants.PlantLayerPresets.Preset preset: org.pepsoft.worldpainter.layers.plants.PlantLayerPresets.Preset.values()) {
            final org.pepsoft.worldpainter.layers.plants.PlantLayer layer = org.pepsoft.worldpainter.layers.plants.PlantLayerPresets.create(preset);
            assertEquals(preset.getLayerName(), layer.getName());
            assertFalse(preset + " has no plants", layer.getConfiguredPlants().isEmpty());
        }
    }

    @Test
    public void testMaterialGroupsInstall() {
        for (MaterialGroup group: MaterialGroup.values()) {
            assertNotNull(group.newMaterial().getMaterial(0, 1, 2, 64));
        }
        final Terrain terrain = MaterialGroup.RED_CLIFFS.install();
        assertNotNull(terrain);
        assertTrue(terrain.isCustom());
        // Installing again reuses the same custom terrain
        assertSame(terrain, MaterialGroup.RED_CLIFFS.install());
    }

    @Test
    public void testPaintingTreesAndMixes() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 2 * TILE_SIZE, 2 * TILE_SIZE), 62);
        final NodeGraph graph = new NodeGraph();
        final ConstantNode constant = graph.add(new ConstantNode());
        constant.set(ConstantNode.VALUE, 0.5);
        graph.connect(constant, graph.getOutputNode(), 0);
        graph.getOutputNode().set(OutputNode.MIN_HEIGHT, 70);
        graph.getOutputNode().set(OutputNode.MAX_HEIGHT, 130);
        graph.add(new MaterialPainterNode(MaterialGroup.FOREST_FLOOR));
        graph.add(new FloraPainterNode(Vegetation.TALL_SPRUCES)).set(FloraPainterNode.DENSITY, 1.0);
        final NodeGraphOperation operation = new NodeGraphOperation(graph);
        operation.apply(dimension, null);
        assertTrue(dimension.getTerrainAt(10, 10).isCustom());
        assertEquals("Forest floor", Terrain.getCustomMaterial(dimension.getTerrainAt(10, 10).getCustomTerrainIndex()).getName());
        Bo2Layer trees = null;
        for (CustomLayer layer: operation.getCreatedLayers()) {
            if (layer instanceof Bo2Layer) {
                trees = (Bo2Layer) layer;
            }
        }
        assertNotNull(trees);
        assertTrue(dimension.getLayerValueAt(trees, 10, 10) > 0);
    }
}
