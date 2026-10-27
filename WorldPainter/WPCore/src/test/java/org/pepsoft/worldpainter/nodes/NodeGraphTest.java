package org.pepsoft.worldpainter.nodes;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class NodeGraphTest {
    @Test
    public void testExamplesAreDeterministic() throws Exception {
        final Region region = new Region(-300, 200, 2, 96, 80);
        for (ExampleGraphs.Example example: ExampleGraphs.Example.values()) {
            final NodeGraph graph = createExample(example);
            final float[] expected = graph.compile(BOUNDS).evaluate(region);
            assertArrayEquals(example.toString(), expected, graph.compile(BOUNDS).evaluate(region), 0.0f);
            assertArrayEquals(example.toString(), expected, graph.clone().compile(BOUNDS).evaluate(region), 0.0f);
            assertArrayEquals(example.toString(), expected, serialiseAndDeserialise(graph).compile(BOUNDS).evaluate(region), 0.0f);
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float value: expected) {
                assertFalse(example + " produced NaN", Float.isNaN(value));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            assertTrue(example + " is flat: " + min + " - " + max, max - min > 0.05f);
        }
    }

    @Test
    public void testDifferentSeedsDiffer() {
        final Region region = new Region(0, 0, 8, 32, 32);
        final NodeGraph graph1 = ExampleGraphs.create(ExampleGraphs.Example.ROLLING_HILLS, 1L, 62, -64, 320);
        final NodeGraph graph2 = ExampleGraphs.create(ExampleGraphs.Example.ROLLING_HILLS, 2L, 62, -64, 320);
        final float[] values1 = graph1.compile(null).evaluate(region), values2 = graph2.compile(null).evaluate(region);
        int different = 0;
        for (int i = 0; i < values1.length; i++) {
            if (Math.abs(values1[i] - values2[i]) > 0.01f) {
                different++;
            }
        }
        assertTrue(different > values1.length / 2);
    }

    /**
     * Evaluating tiles separately must give exactly the same result as evaluating the whole area at once, including
     * for nodes which look at neighbouring samples (blur and erosion).
     */
    @Test
    public void testSeamlessAcrossTiles() {
        for (NodeGraph graph: new NodeGraph[] {createExample(ExampleGraphs.Example.ARCHIPELAGO), createExample(ExampleGraphs.Example.MESA), createExample(ExampleGraphs.Example.ERODED_MOUNTAINS), createBlurred()}) {
            final CompiledGraph compiled = graph.compile(BOUNDS);
            final int size = 2 * TILE_SIZE, x0 = -TILE_SIZE, y0 = 3 * TILE_SIZE;
            final float[] whole = compiled.evaluate(new Region(x0, y0, 1, size, size));
            for (int tileX = 0; tileX < 2; tileX++) {
                for (int tileY = 0; tileY < 2; tileY++) {
                    final float[] tile = compiled.evaluate(new Region(x0 + tileX * TILE_SIZE, y0 + tileY * TILE_SIZE, 1, TILE_SIZE, TILE_SIZE));
                    for (int y = 0; y < TILE_SIZE; y++) {
                        for (int x = 0; x < TILE_SIZE; x++) {
                            final float expected = whole[(tileX * TILE_SIZE + x) + (tileY * TILE_SIZE + y) * size];
                            if (expected != tile[x + y * TILE_SIZE]) {
                                fail(graph.getName() + ": tile " + tileX + "," + tileY + " differs at " + x + "," + y + ": " + tile[x + y * TILE_SIZE] + " instead of " + expected);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testCombiners() {
        assertEquals(0.9f, combine(CombineNode.Mode.ADD, 1.0), 1e-6f);
        assertEquals(0.6f, combine(CombineNode.Mode.ADD, 0.5), 1e-6f);
        assertEquals(-0.3f, combine(CombineNode.Mode.SUBTRACT, 1.0), 1e-6f);
        assertEquals(0.18f, combine(CombineNode.Mode.MULTIPLY, 1.0), 1e-6f);
        assertEquals(0.6f, combine(CombineNode.Mode.MAX, 1.0), 1e-6f);
        assertEquals(0.3f, combine(CombineNode.Mode.MIN, 1.0), 1e-6f);

        // Unconnected inputs are neutral
        final NodeGraph graph = new NodeGraph();
        final ConstantNode a = graph.add(constant(0.3));
        final CombineNode multiply = graph.add(new CombineNode(CombineNode.Mode.MULTIPLY));
        graph.connect(a, multiply, 0);
        graph.connect(multiply, graph.getOutputNode(), 0);
        assertEquals(0.3f, evaluateOne(graph), 1e-6f);

        // Blend with mask
        final NodeGraph blendGraph = new NodeGraph();
        final BlendNode blend = blendGraph.add(new BlendNode());
        blendGraph.connect(blendGraph.add(constant(0.3)), blend, 0);
        blendGraph.connect(blendGraph.add(constant(0.6)), blend, 1);
        final ConstantNode mask = blendGraph.add(constant(0.25));
        blendGraph.connect(mask, blend, 2);
        blendGraph.connect(blend, blendGraph.getOutputNode(), 0);
        assertEquals(0.375f, evaluateOne(blendGraph), 1e-6f);
        blend.set(BlendNode.INVERT_MASK, true);
        assertEquals(0.525f, evaluateOne(blendGraph), 1e-6f);
        // The mask is clamped
        mask.set(ConstantNode.VALUE, 1.5);
        blend.set(BlendNode.INVERT_MASK, false);
        assertEquals(0.6f, evaluateOne(blendGraph), 1e-6f);
    }

    @Test
    public void testFilters() {
        assertEquals(0.5f, filter(new CurveNode(), 0.5), 1e-3f);
        assertEquals(0.75f, filter(new CurveNode(0, 1, 1, 0), 0.25), 1e-3f);
        // Monotone curves do not overshoot
        assertTrue(filter(new CurveNode(0, 0, 0.5f, 0.5f, 0.6f, 1, 1, 1), 0.8) <= 1.0f);
        final ClampNode clamp = new ClampNode();
        clamp.set(ClampNode.MAX, 0.4);
        assertEquals(0.4f, filter(clamp, 0.7), 0.0f);
        assertEquals(0.2f, filter(clamp, 0.2), 0.0f);
        final TerraceNode terrace = new TerraceNode();
        terrace.set(TerraceNode.STEPS, 4);
        terrace.set(TerraceNode.SHARPNESS, 1.0);
        assertEquals(0.25f, filter(terrace, 0.3), 0.01f);
        // Blurring a constant leaves it unchanged
        assertEquals(0.7f, filter(new BlurNode(), 0.7), 1e-5f);
    }

    @Test
    public void testErosionLowersPeaks() {
        final NodeGraph graph = new NodeGraph();
        final NoiseNode noise = graph.add(new NoiseNode(NoiseNode.Kind.RIDGED));
        noise.set(NoiseNode.SCALE, 128);
        final ThermalErosionNode erosion = graph.add(new ThermalErosionNode());
        erosion.set(ThermalErosionNode.ANGLE, 20);
        graph.connect(noise, erosion, 0);
        graph.connect(erosion, graph.getOutputNode(), 0);
        final Region region = new Region(0, 0, 2, 64, 64);
        final CompiledGraph compiled = graph.compile(null);
        final Evaluation evaluation = compiled.createEvaluation();
        final float[] before = compiled.evaluate(evaluation, noise.getId(), region), after = compiled.evaluate(evaluation, erosion.getId(), region);
        float maxBefore = -Float.MAX_VALUE, maxAfter = -Float.MAX_VALUE;
        for (int i = 0; i < before.length; i++) {
            maxBefore = Math.max(maxBefore, before[i]);
            maxAfter = Math.max(maxAfter, after[i]);
        }
        assertTrue(maxAfter + " >= " + maxBefore, maxAfter < maxBefore);
    }

    @Test
    public void testLoopsAreRefused() {
        final NodeGraph graph = new NodeGraph();
        final CurveNode first = graph.add(new CurveNode()), second = graph.add(new CurveNode());
        graph.connect(first, second, 0);
        assertFalse(graph.canConnect(second, first, 0));
        assertFalse(graph.canConnect(first, first, 0));
        assertFalse(graph.canConnect(graph.getOutputNode(), first, 0));
        // Connecting to an input replaces the existing connection
        final ConstantNode constant = graph.add(constant(0.1));
        graph.connect(constant, second, 0);
        assertSame(constant, graph.getInput(second, 0));
        assertEquals(1, graph.getConnections().size());
        // Removing a node removes its connections
        graph.remove(constant);
        assertNull(graph.getInput(second, 0));
        assertTrue(graph.getConnections().isEmpty());
    }

    @Test
    public void testApplyToDimension() throws Exception {
        final Dimension dimension = TestData.createDimension(new Rectangle(0, 0, 4 * TILE_SIZE, 4 * TILE_SIZE), 62);
        final NodeGraph graph = createExample(ExampleGraphs.Example.ROLLING_HILLS);
        final NodeGraphOperation operation = new NodeGraphOperation(graph);
        operation.setApplyTheme(false);
        operation.apply(dimension, null);
        final CompiledGraph compiled = graph.compile(NodeGraphOperation.getMapBounds(dimension));
        final float[] expected = compiled.evaluateHeights(new Region(0, 0, 1, 4 * TILE_SIZE, 4 * TILE_SIZE));
        for (int x = 0; x < 4 * TILE_SIZE; x += 7) {
            for (int y = 0; y < 4 * TILE_SIZE; y += 11) {
                // Heights are stored with 1/256 precision
                assertEquals(expected[x + y * 4 * TILE_SIZE], dimension.getHeightAt(x, y), 0.01f);
            }
        }
    }

    private static NodeGraph createExample(ExampleGraphs.Example example) {
        return ExampleGraphs.create(example, 12345L, 62, -64, 320);
    }

    private static NodeGraph createBlurred() {
        final NodeGraph graph = new NodeGraph();
        graph.setName("Blurred cells");
        final CellsNode cells = graph.add(new CellsNode());
        cells.set(CellsNode.SCALE, 64);
        final BlurNode blur = graph.add(new BlurNode());
        blur.set(BlurNode.RADIUS, 40);
        graph.connect(cells, blur, 0);
        graph.connect(blur, graph.getOutputNode(), 0);
        return graph;
    }

    private static float combine(CombineNode.Mode mode, double strength) {
        final NodeGraph graph = new NodeGraph();
        final CombineNode combine = graph.add(new CombineNode(mode));
        combine.set(CombineNode.STRENGTH, strength);
        graph.connect(graph.add(constant(0.3)), combine, 0);
        graph.connect(graph.add(constant(0.6)), combine, 1);
        graph.connect(combine, graph.getOutputNode(), 0);
        return evaluateOne(graph);
    }

    private static float filter(Node filter, double input) {
        final NodeGraph graph = new NodeGraph();
        graph.add(filter);
        graph.connect(graph.add(constant(input)), filter, 0);
        graph.connect(filter, graph.getOutputNode(), 0);
        return evaluateOne(graph);
    }

    private static ConstantNode constant(double value) {
        final ConstantNode node = new ConstantNode();
        node.set(ConstantNode.VALUE, value);
        return node;
    }

    private static float evaluateOne(NodeGraph graph) {
        return graph.compile(null).evaluate(new Region(17, -5, 1, 4, 4))[5];
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
