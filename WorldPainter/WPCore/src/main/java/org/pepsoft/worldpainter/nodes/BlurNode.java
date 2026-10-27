package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A filter which smooths its input with a Gaussian blur. Large radii are computed on a coarser grid, which is aligned
 * to world coordinates so that the result is seamless.
 */
public class BlurNode extends Node {
    @Override
    public String getName() {
        return "Blur";
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
        return List.of(RADIUS);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double radius = getDouble(RADIUS);
        return new CompiledNode(inputs, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                // At most this many cells per radius, to bound the cost of large radii
                final double cellSize = Math.max(region.getStep(), radius / 8);
                final double sigma = radius / 2 / cellSize;
                if (sigma < 0.25) {
                    return input(evaluation, 0, region).clone();
                }
                final int halfWidth = (int) Math.ceil(sigma * 3);
                final float[] kernel = new float[halfWidth * 2 + 1];
                double total = 0;
                for (int k = -halfWidth; k <= halfWidth; k++) {
                    total += kernel[k + halfWidth] = (float) Math.exp(-(k * k) / (2 * sigma * sigma));
                }
                for (int k = 0; k < kernel.length; k++) {
                    kernel[k] /= total;
                }
                final Region grid = region.aligned(cellSize, halfWidth);
                final float[] in = input(evaluation, 0, grid);
                final int width = grid.getWidth(), height = grid.getHeight();
                final float[] horizontal = new float[in.length], result = new float[in.length];
                for (int y = 0; y < height; y++) {
                    final int row = y * width;
                    for (int x = 0; x < width; x++) {
                        float sum = 0;
                        for (int k = -halfWidth; k <= halfWidth; k++) {
                            sum += in[row + Math.max(0, Math.min(width - 1, x + k))] * kernel[k + halfWidth];
                        }
                        horizontal[row + x] = sum;
                    }
                }
                evaluation.checkCancelled();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        float sum = 0;
                        for (int k = -halfWidth; k <= halfWidth; k++) {
                            sum += horizontal[Math.max(0, Math.min(height - 1, y + k)) * width + x] * kernel[k + halfWidth];
                        }
                        result[y * width + x] = sum;
                    }
                }
                return region.resample(result, grid);
            }
        };
    }

    public static final NumberParameter RADIUS = new NumberParameter("radius", "Radius", "How far the smoothing reaches", 16, 1, 1024, 1, true, "blocks");

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final long serialVersionUID = 1L;
}
