package org.pepsoft.worldpainter.nodes;

import java.util.Arrays;

/**
 * The immutable, thread safe evaluator of a {@link Node}.
 */
abstract class CompiledNode {
    /**
     * @param inputs        The evaluators connected to the inputs, with {@code null} for unconnected inputs.
     * @param inputDefaults The values to use for unconnected inputs.
     */
    CompiledNode(CompiledNode[] inputs, float... inputDefaults) {
        this.inputs = inputs;
        this.inputDefaults = inputDefaults;
    }

    /**
     * Compute the values of the node for a region. Implementations must return a new array, must not modify the
     * arrays obtained from their inputs and must only depend on the world coordinates of the samples (not on the
     * extent of the region).
     */
    abstract float[] compute(Evaluation evaluation, Region region);

    /**
     * Get the values of an input for a region.
     */
    final float[] input(Evaluation evaluation, int index, Region region) {
        if (inputs[index] == null) {
            final float[] values = new float[region.size()];
            Arrays.fill(values, inputDefaults[index]);
            return values;
        }
        return evaluation.evaluate(inputs[index], region);
    }

    final boolean isConnected(int index) {
        return inputs[index] != null;
    }

    private final CompiledNode[] inputs;
    private final float[] inputDefaults;

    /**
     * A node of which each value depends only on the location of the sample.
     */
    abstract static class Generator extends CompiledNode {
        Generator() {
            super(new CompiledNode[0]);
        }

        @Override
        final float[] compute(Evaluation evaluation, Region region) {
            final int width = region.getWidth(), height = region.getHeight();
            final float[] values = new float[width * height];
            for (int j = 0; j < height; j++) {
                if ((j & 0xf) == 0) {
                    evaluation.checkCancelled();
                }
                final double y = region.y(j);
                for (int i = 0; i < width; i++) {
                    values[i + j * width] = value(region.x(i), y);
                }
            }
            return values;
        }

        abstract float value(double x, double y);
    }

    /**
     * A node of which each value depends only on the value of its single input at the same location.
     */
    abstract static class Filter extends CompiledNode {
        Filter(CompiledNode input) {
            super(new CompiledNode[] {input}, 0.0f);
        }

        @Override
        final float[] compute(Evaluation evaluation, Region region) {
            final float[] in = input(evaluation, 0, region);
            final float[] values = new float[in.length];
            for (int i = 0; i < in.length; i++) {
                values[i] = apply(in[i]);
            }
            return values;
        }

        abstract float apply(float value);
    }
}
