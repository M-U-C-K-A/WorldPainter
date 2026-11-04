package org.pepsoft.worldpainter.layers.bo2;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.objects.WPObject;

import java.awt.*;
import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.*;

public class TreePackTest {
    /**
     * Each tree stands on its trunk in the middle of its object, so that it turns around it, and has leaves (except dead
     * trees).
     */
    @Test
    public void testTreesStandOnTheirTrunk() {
        for (TreePack.Kind kind: TreePack.Kind.values()) {
            final Random random = new Random(kind.ordinal());
            for (int variant = 0; variant < 4; variant++) {
                final WPObject tree = TreePack.create(kind, variant, random);
                final javax.vecmath.Point3i size = tree.getDimensions();
                assertEquals(size.x, size.y);
                final javax.vecmath.Point3i offset = tree.getAttribute(WPObject.ATTRIBUTE_OFFSET);
                assertEquals(-(size.x - 1) / 2, offset.x);
                final Material base = blockAt(tree, -offset.x, -offset.y, 0);
                assertNotNull(kind + " " + variant, base);
                assertTrue(kind + " " + variant, base.name.endsWith("_log"));
                int leaves = 0;
                for (int x = 0; x < size.x; x++) {
                    for (int y = 0; y < size.y; y++) {
                        for (int z = 0; z < size.z; z++) {
                            final Material material = blockAt(tree, x, y, z);
                            if ((material != null) && material.name.endsWith("_leaves")) {
                                leaves++;
                            }
                        }
                    }
                }
                assertTrue(kind + " " + variant, (kind == TreePack.Kind.DEAD) ? (leaves == 0) : (leaves > 10));
                assertEquals((Integer) TreePack.VERSION, tree.getAttribute(TreePack.ATTRIBUTE_VERSION));
            }
        }
    }

    /**
     * The trees of a world painted with an earlier version of the pack are made again when it is loaded.
     */
    @Test
    public void testOldTreesAreUpgraded() {
        final Bo2Layer current = TreePack.createLayer(TreePack.Kind.BIG_OAK);
        assertNull(TreePack.upgrade(current));
        final GenericObject old = new GenericObject("Big oak 1", 1, 1, 1, new Material[] {Material.get("minecraft:oak_log")});
        final Bo2Layer earlier = new Bo2Layer(new Bo2ObjectTube("Big oaks", Collections.singletonList(old)), "Big oaks (WorldPainter tree pack)", Color.GREEN);
        final Bo2ObjectProvider upgraded = TreePack.upgrade(earlier);
        assertNotNull(upgraded);
        assertEquals(8, upgraded.getAllObjects().size());
        final Bo2Layer someoneElses = new Bo2Layer(new Bo2ObjectTube("Big oaks", Collections.singletonList(old)), "My own oaks", Color.GREEN);
        assertNull(TreePack.upgrade(someoneElses));
    }

    private static Material blockAt(WPObject tree, int x, int y, int z) {
        if ((x < 0) || (y < 0) || (x >= tree.getDimensions().x) || (y >= tree.getDimensions().y)) {
            return null;
        }
        final Material material = tree.getMaterial(x, y, z);
        return ((material == null) || material.air) ? null : material;
    }
}
