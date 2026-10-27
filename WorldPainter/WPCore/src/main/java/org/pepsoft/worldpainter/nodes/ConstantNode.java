package org.pepsoft.worldpainter.nodes;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of a single value everywhere.
 */
public class ConstantNode extends Node {
    @Override
    public String getName() {
        return "Constant";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.GENERATOR;
    }

    @Override
    public List<String> getInputNames() {
        return emptyList();
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(VALUE);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final float value = (float) getDouble(VALUE);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                return value;
            }
        };
    }

    public static final NumberParameter VALUE = new NumberParameter("value", "Value", "The value (0 = the output's minimum height, 1 = its maximum height)", 0.5, -1, 2, 0.01, false, null);

    private static final long serialVersionUID = 1L;
}
