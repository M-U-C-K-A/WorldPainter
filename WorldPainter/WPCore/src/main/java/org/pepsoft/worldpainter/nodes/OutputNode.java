package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * The final node of a {@link NodeGraph}, which maps the value of its input to the height of the terrain in blocks:
 * zero to the minimum height and one to the maximum height.
 */
public class OutputNode extends Node {
    @Override
    public String getName() {
        return "Output";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.OUTPUT;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public boolean hasOutput() {
        return false;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(MIN_HEIGHT, MAX_HEIGHT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        return new CompiledNode(inputs, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                return input(evaluation, 0, region).clone();
            }
        };
    }

    public static final NumberParameter MIN_HEIGHT = new NumberParameter("minHeight", "Height at 0", "The terrain height for a value of zero (black)", 40, -2048, 4096, 1, false, "blocks");
    public static final NumberParameter MAX_HEIGHT = new NumberParameter("maxHeight", "Height at 1", "The terrain height for a value of one (white)", 200, -2048, 4096, 1, false, "blocks");

    private static final List<String> INPUT_NAMES = List.of("Height");
    private static final long serialVersionUID = 1L;
}
