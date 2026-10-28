package org.pepsoft.worldpainter.nodes;

import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

public class RegionsAndVolcanoTest {
    @Test
    public void testRegionTypesSplitTheMap() {
        final int count = 4;
        final Region region = new Region(-3000, -3000, 23, 260, 260);
        final float[] total = new float[region.size()];
        final int[] dominant = new int[count];
        for (int type = 0; type < count; type++) {
            final NodeGraph graph = new NodeGraph();
            graph.setSeed(99);
            final RegionsNode node = graph.add(new RegionsNode());
            node.set(RegionsNode.COUNT, count);
            node.set(RegionsNode.TYPE, type);
            graph.connect(node, graph.getOutputNode(), 0);
            final float[] values = graph.compile(null).evaluate(region);
            for (int i = 0; i < values.length; i++) {
                total[i] += values[i];
                if (values[i] > 0.5f) {
                    dominant[type]++;
                }
            }
        }
        // Every location belongs to (about) one region type, and every type occurs
        for (float value: total) {
            assertEquals(1.0f, value, 0.51f);
        }
        for (int type = 0; type < count; type++) {
            assertTrue("type " + type + " does not occur", dominant[type] > 0);
        }
    }

    @Test
    public void testVolcanoHasACrater() {
        final NodeGraph graph = new NodeGraph();
        final VolcanoNode volcano = graph.add(new VolcanoNode());
        volcano.set(VolcanoNode.RADIUS, 400);
        graph.connect(volcano, graph.getOutputNode(), 0);
        final Rectangle bounds = new Rectangle(-1000, -1000, 2000, 2000);
        final CompiledGraph compiled = graph.compile(bounds, null);
        final float centre = compiled.evaluate(new Region(0, 0, 1, 1, 1))[0];
        final float rim = compiled.evaluate(new Region(400 * 0.12 * 1.05, 0, 1, 1, 1))[0];
        final float flank = compiled.evaluate(new Region(200, 0, 1, 1, 1))[0];
        final float outside = compiled.evaluate(new Region(900, 0, 1, 1, 1))[0];
        assertTrue("crater " + centre + " rim " + rim, centre < rim);
        assertTrue("rim " + rim + " flank " + flank, rim > flank);
        assertEquals(0, outside, 0.0001f);
    }
}
