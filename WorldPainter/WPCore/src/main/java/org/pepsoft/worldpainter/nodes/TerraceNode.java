package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A filter which turns slopes into flat terraces separated by steep steps.
 */
public class TerraceNode extends Node {
    @Override
    public String getName() {
        return "Terrace";
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
        return List.of(STEPS, SHARPNESS, STRENGTH);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final float steps = getInt(STEPS), strength = (float) getDouble(STRENGTH);
        final double exponent = 1 + 7 * getDouble(SHARPNESS);
        return new CompiledNode.Filter(inputs[0]) {
            @Override
            float apply(float value) {
                final float k = value * steps;
                final float floor = (float) Math.floor(k);
                final float terraced = (floor + (float) Math.pow(k - floor, exponent)) / steps;
                return value + (terraced - value) * strength;
            }
        };
    }

    public static final NumberParameter STEPS = new NumberParameter("steps", "Steps", "The number of terraces between value 0 and 1", 8, 1, 64, 1, false, null);
    public static final NumberParameter SHARPNESS = new NumberParameter("sharpness", "Sharpness", "How flat the terraces and how steep the steps between them are", 0.6, 0, 1, 0.01, false, null);
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "How strongly the terracing is applied", 1, 0, 1, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final long serialVersionUID = 1L;
}
