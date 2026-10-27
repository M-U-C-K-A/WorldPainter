package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A combiner which blends between two inputs according to a mask: input A where the mask is zero, input B where it is
 * one.
 */
public class BlendNode extends Node {
    @Override
    public String getName() {
        return "Blend with mask";
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.COMBINER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(INVERT_MASK);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final boolean invert = get(INVERT_MASK);
        return new CompiledNode(inputs, 0.0f, 0.0f, 0.5f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final float[] a = input(evaluation, 0, region), b = input(evaluation, 1, region), mask = input(evaluation, 2, region);
                final float[] result = new float[a.length];
                for (int i = 0; i < a.length; i++) {
                    float m = Math.max(0.0f, Math.min(1.0f, mask[i]));
                    if (invert) {
                        m = 1.0f - m;
                    }
                    result[i] = a[i] + (b[i] - a[i]) * m;
                }
                return result;
            }
        };
    }

    public static final BooleanParameter INVERT_MASK = new BooleanParameter("invertMask", "Invert mask", "Use input B where the mask is zero instead of where it is one", false);

    private static final List<String> INPUT_NAMES = List.of("A", "B", "Mask");
    private static final long serialVersionUID = 1L;
}
