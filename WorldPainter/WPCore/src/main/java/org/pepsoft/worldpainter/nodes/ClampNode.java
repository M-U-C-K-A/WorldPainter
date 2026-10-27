package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A filter which limits values to a range.
 */
public class ClampNode extends Node {
    @Override
    public String getName() {
        return "Clamp";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.FILTER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(MIN, MAX);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final float min = (float) getDouble(MIN), max = (float) getDouble(MAX);
        return new CompiledNode.Filter(inputs[0]) {
            @Override
            float apply(float value) {
                return Math.max(min, Math.min(max, value));
            }
        };
    }

    public static final NumberParameter MIN = new NumberParameter("min", "Minimum", "Lower values are raised to this value", 0, -1, 2, 0.01, false, null);
    public static final NumberParameter MAX = new NumberParameter("max", "Maximum", "Higher values are lowered to this value", 1, -1, 2, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final long serialVersionUID = 1L;
}
