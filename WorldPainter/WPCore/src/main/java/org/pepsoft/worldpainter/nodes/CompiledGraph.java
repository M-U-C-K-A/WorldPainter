package org.pepsoft.worldpainter.nodes;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A compiled, thread safe evaluator for a {@link NodeGraph}. The values are deterministic functions of the world
 * coordinates and the seed, so arbitrarily large areas can be generated tile by tile, in parallel, without seams.
 */
public final class CompiledGraph {
    CompiledGraph(NodeGraph graph, Rectangle mapBounds, MapSource mapSource) {
        final OutputNode outputNode = graph.getOutputNode();
        minHeight = (float) outputNode.getDouble(OutputNode.MIN_HEIGHT);
        maxHeight = (float) outputNode.getDouble(OutputNode.MAX_HEIGHT);
        context = new CompileContext(graph.getSeed(), minHeight, maxHeight, mapBounds, mapSource);
        terrainNode = graph.getInput(outputNode, 0);
        for (Node node: graph.getNodes()) {
            compile(graph, node, new HashSet<>());
        }
        output = compiled.get(outputNode.getId());
        outputNodeId = outputNode.getId();
        final List<Painter> painters = new ArrayList<>();
        final List<WaterBody> waterBodies = new ArrayList<>();
        final List<FloatingIslandsNode.Compiled> floatingIslands = new ArrayList<>();
        for (Node node: graph.getNodes()) {
            if (node instanceof FloatingIslandsNode) {
                final FloatingIslandsNode.Compiled islands = (FloatingIslandsNode.Compiled) compiled.get(node.getId());
                islands.setCompiledGraph(this);
                floatingIslands.add(islands);
            } else if (node instanceof PainterNode) {
                painters.add(new Painter(node.getId(), ((PainterNode) node).getTarget(), (node instanceof FloraPainterNode) && ((FloraPainterNode) node).isOnlyOnLand(), compiled.get(node.getId())));
            } else if ((node instanceof WaterNode) && (terrainNode != null) && graph.dependsOn(terrainNode, node)) {
                // Only water which is carved into the final terrain is filled; it also paints its bed and biome
                final WaterNode waterNode = (WaterNode) node;
                final WaterNode.CompiledWater water = (WaterNode.CompiledWater) compiled.get(node.getId());
                waterBodies.add(new WaterBody(node.getId(), water));
                if (waterNode.getBedTerrain() != null) {
                    painters.add(new Painter(node.getId(), waterNode.getBedTerrain(), false, water.getStrengths()));
                }
                if (waterNode.getBiome() != null) {
                    painters.add(new Painter(node.getId(), waterNode.getBiome(), false, water.getStrengths()));
                }
            }
        }
        this.painters = Collections.unmodifiableList(painters);
        this.waterBodies = Collections.unmodifiableList(waterBodies);
        this.floatingIslands = Collections.unmodifiableList(floatingIslands);
    }

    /**
     * The floating islands of the graph.
     */
    public List<FloatingIslandsNode.Compiled> getFloatingIslands() {
        return floatingIslands;
    }

    /**
     * The ID of the output node, for {@link #evaluate(Evaluation, int, Region)}.
     */
    public int getOutputNodeId() {
        return outputNodeId;
    }

    /**
     * The rivers and lakes carved into the final terrain.
     */
    public List<WaterBody> getWaterBodies() {
        return waterBodies;
    }

    /**
     * The surface painters of the graph, in the order of the graph, including those for the beds and biomes of the
     * {@link #getWaterBodies() water bodies}.
     */
    public List<Painter> getPainters() {
        return painters;
    }

    /**
     * The height in blocks corresponding to a value of zero.
     */
    public float getMinHeight() {
        return minHeight;
    }

    /**
     * The height in blocks corresponding to a value of one.
     */
    public float getMaxHeight() {
        return maxHeight;
    }

    /**
     * Convert a value to a height in blocks.
     */
    public float toHeight(float value) {
        return minHeight + value * (maxHeight - minHeight);
    }

    /**
     * Create a new evaluation, which can be used to evaluate several nodes of the graph for the same regions while
     * sharing intermediate results. Evaluations are not thread safe.
     */
    public Evaluation createEvaluation() {
        return new Evaluation();
    }

    /**
     * Evaluate the output of the graph for a region.
     *
     * @return The values in row major order, nominally between zero and one.
     * @throws EvaluationCancelledException If the current thread is interrupted.
     */
    public float[] evaluate(Region region) {
        return createEvaluation().evaluate(output, region);
    }

    /**
     * Evaluate the output of the graph for a region and convert the values to heights in blocks.
     *
     * @return The heights in row major order.
     * @throws EvaluationCancelledException If the current thread is interrupted.
     */
    public float[] evaluateHeights(Region region) {
        final float[] values = evaluate(region);
        for (int i = 0; i < values.length; i++) {
            values[i] = toHeight(values[i]);
        }
        return values;
    }

    /**
     * Evaluate one particular node of the graph for a region. For the output node this is the value of its input.
     *
     * @return The values in row major order, or {@code null} if the node is not part of the compiled graph.
     * @throws EvaluationCancelledException If the current thread is interrupted.
     */
    public float[] evaluate(Evaluation evaluation, int nodeId, Region region) {
        final CompiledNode node = compiled.get(nodeId);
        return (node != null) ? evaluation.evaluate(node, region).clone() : null;
    }

    private CompiledNode compile(NodeGraph graph, Node node, Set<Integer> inProgress) {
        CompiledNode result = compiled.get(node.getId());
        if (result != null) {
            return result;
        }
        if (! inProgress.add(node.getId())) {
            // Guard against loops in corrupt graphs by treating the offending input as unconnected
            return null;
        }
        final int inputCount = node.getInputNames().size();
        final CompiledNode[] inputs = new CompiledNode[inputCount];
        for (int i = 0; i < inputCount; i++) {
            Node input = graph.getInput(node, i);
            if ((input == null) && node.isTerrainInput(i) && (terrainNode != null) && (! graph.dependsOn(terrainNode, node))) {
                // Unconnected terrain inputs receive the final terrain, unless the node is itself part of it
                input = terrainNode;
            }
            if (input != null) {
                inputs[i] = compile(graph, input, inProgress);
            }
        }
        inProgress.remove(node.getId());
        result = node.compile(context, inputs);
        compiled.put(node.getId(), result);
        return result;
    }

    private final float minHeight, maxHeight;
    private final CompileContext context;
    private final Map<Integer, CompiledNode> compiled = new HashMap<>();
    private final CompiledNode output;
    private final Node terrainNode;
    private final int outputNodeId;
    private final List<Painter> painters;
    private final List<WaterBody> waterBodies;
    private final List<FloatingIslandsNode.Compiled> floatingIslands;
}
