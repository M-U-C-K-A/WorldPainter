package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Base class for erosion filters. Erosion is simulated as a cellular automaton on a grid of cells which is aligned to
 * world coordinates; every iteration only looks at a fixed number of neighbouring cells, so with a margin of that
 * many cells per iteration around the evaluated region the result is exactly the same as for the whole map, and
 * therefore seamless across tiles.
 *
 * <p>When the evaluated region is coarser than the cell size (for instance in a zoomed out preview) the simulation runs
 * at the coarser resolution with proportionally fewer iterations, which approximates the full resolution result.
 */
public abstract class ErosionNode extends Node {
    @Override
    public NodeCategory getCategory() {
        return NodeCategory.FILTER;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double cellSize = getDouble(CELL_SIZE);
        final int iterations = getInt(ITERATIONS);
        final boolean keepDetail = get(KEEP_DETAIL);
        final float verticalScale = context.getVerticalScale();
        // The simulation reads its parameters from the node, so use a copy which the user cannot change
        final ErosionNode snapshot = (ErosionNode) clone();
        return new CompiledNode(inputs, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final double cell = Math.max(region.getStep(), cellSize);
                final int effectiveIterations = Math.max(1, (int) Math.round(iterations * cellSize / cell));
                final Region grid = region.aligned(cell, snapshot.getRadius() * effectiveIterations + 1);
                final float[] original = input(evaluation, 0, grid);
                // Simulate in units of cells, so that slopes are physical
                final float toCells = (float) (verticalScale / cell);
                final float[] heights = new float[original.length];
                for (int i = 0; i < heights.length; i++) {
                    heights[i] = original[i] * toCells;
                }
                snapshot.erode(evaluation, heights, grid.getWidth(), grid.getHeight(), effectiveIterations);
                if (keepDetail) {
                    // Apply the change caused by the erosion to the input at full resolution, to keep its fine detail
                    final float[] delta = new float[heights.length];
                    for (int i = 0; i < heights.length; i++) {
                        delta[i] = heights[i] / toCells - original[i];
                    }
                    final float[] fine = input(evaluation, 0, region), change = region.resample(delta, grid);
                    final float[] result = new float[fine.length];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = fine[i] + change[i];
                    }
                    return result;
                } else {
                    for (int i = 0; i < heights.length; i++) {
                        heights[i] /= toCells;
                    }
                    return region.resample(heights, grid);
                }
            }
        };
    }

    /**
     * How many cells away each iteration of {@link #erode(Evaluation, float[], int, int, int)} looks.
     */
    abstract int getRadius();

    /**
     * Run the simulation in place.
     *
     * @param heights    The heights, in units of cells, in row major order.
     * @param iterations The number of iterations to perform.
     */
    abstract void erode(Evaluation evaluation, float[] heights, int width, int height, int iterations);

    public static final NumberParameter ITERATIONS = new NumberParameter("iterations", "Iterations", "How long the erosion runs; more iterations reach further but take longer", 30, 1, 200, 1, false, null);
    public static final NumberParameter CELL_SIZE = new NumberParameter("cellSize", "Resolution", "The size of the simulation cells; larger cells erode larger features, faster", 2, 1, 32, 1, false, "blocks");
    public static final BooleanParameter KEEP_DETAIL = new BooleanParameter("keepDetail", "Keep detail", "Keep the detail of the input that is finer than the resolution", true);

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final long serialVersionUID = 1L;
}
