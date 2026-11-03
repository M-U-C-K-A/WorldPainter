package org.pepsoft.worldpainter.nodes;

import org.junit.Test;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

public class LandformNodeTest {
    @Test
    public void testLandformsAreDeterministicAndSeamless() throws Exception {
        final Region whole = new Region(-512, -512, 8, 128, 128);
        final Region left = new Region(-512, -512, 8, 64, 128), right = new Region(0, -512, 8, 64, 128);
        for (NodeType type: NodeType.getAll()) {
            if (type.getCategory() != NodeCategory.LANDFORM) {
                continue;
            }
            final NodeGraph graph = graph(type.create());
            final float[] values = graph.compile(BOUNDS).evaluate(whole);
            assertArrayEquals(type.getName(), values, graph.compile(BOUNDS).evaluate(whole), 0.0f);
            assertArrayEquals(type.getName(), values, serialiseAndDeserialise(graph).compile(BOUNDS).evaluate(whole), 0.0f);
            // The halves must match the whole exactly, also where the erosion looks at neighbouring samples
            final float[] leftValues = graph.compile(BOUNDS).evaluate(left), rightValues = graph.compile(BOUNDS).evaluate(right);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 64; x++) {
                    assertEquals(type.getName(), values[x + y * 128], leftValues[x + y * 64], 0.0f);
                    assertEquals(type.getName(), values[x + 64 + y * 128], rightValues[x + y * 64], 0.0f);
                }
            }
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float value: values) {
                assertFalse(type.getName() + " produced NaN", Float.isNaN(value));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            assertTrue(type.getName() + " is flat: " + min + " - " + max, max - min > 0.1f);
            assertTrue(type.getName() + " goes beyond the range of the output: " + min + " - " + max, (min > -0.2f) && (max < 1.3f));
        }
    }

    @Test
    public void testIslandIsSurroundedBySea() {
        final IslandNode island = new IslandNode();
        final CompiledGraph compiled = graph(island).compile(BOUNDS);
        final float coast = (float) island.getDouble(IslandNode.COAST);
        // The edges of the map are below the coast, the middle above it
        final float[] edges = compiled.evaluate(new Region(-1024, -1024, 16, 128, 2));
        for (float value: edges) {
            assertTrue("Edge at " + value, value < coast);
        }
        final float[] middle = compiled.evaluate(new Region(-64, -64, 16, 8, 8));
        float sum = 0;
        for (float value: middle) {
            sum += value;
        }
        assertTrue("Middle at " + (sum / middle.length), sum / middle.length > coast + 0.1f);
    }

    private static NodeGraph graph(Node landform) {
        final NodeGraph graph = new NodeGraph();
        graph.setSeed(12345L);
        graph.add(landform);
        graph.connect(landform, graph.getOutputNode(), 0);
        return graph;
    }

    private static NodeGraph serialiseAndDeserialise(NodeGraph graph) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(graph);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (NodeGraph) in.readObject();
        }
    }

    private static final Rectangle BOUNDS = new Rectangle(-1024, -1024, 2048, 2048);
}
