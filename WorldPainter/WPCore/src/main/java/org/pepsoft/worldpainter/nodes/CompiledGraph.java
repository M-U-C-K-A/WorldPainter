package org.pepsoft.worldpainter.nodes;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A compiled, thread safe evaluator for a {@link NodeGraph}. The values are deterministic functions of the world
 * coordinates and the seed, so arbitrarily large areas can be generated tile by tile, in parallel, without seams.
 */
public final class CompiledGraph {
    CompiledGraph(NodeGraph graph, Rectangle mapBounds) {
        final OutputNode outputNode = graph.getOutputNode();
        minHeight = (float) outputNode.getDouble(OutputNode.MIN_HEIGHT);
        maxHeight = (float) outputNode.getDouble(OutputNode.MAX_HEIGHT);
        context = new CompileContext(graph.getSeed(), minHeight, maxHeight, mapBounds);
        for (Node node: graph.getNodes()) {
            compile(graph, node, new HashSet<>());
        }
        output = compiled.get(outputNode.getId());
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
            final Node input = graph.getInput(node, i);
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
}
