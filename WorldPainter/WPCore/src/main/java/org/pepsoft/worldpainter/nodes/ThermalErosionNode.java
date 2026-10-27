package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Thermal erosion: material slides down slopes which are steeper than the angle of repose, softening cliffs and
 * creating scree slopes.
 */
public class ThermalErosionNode extends ErosionNode {
    public ThermalErosionNode() {
        set(ITERATIONS, 30.0);
        set(CELL_SIZE, 2.0);
        set(KEEP_DETAIL, false);
    }

    @Override
    public String getName() {
        return "Thermal erosion";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(ITERATIONS, ANGLE, STRENGTH, CELL_SIZE, KEEP_DETAIL);
    }

    @Override
    int getRadius() {
        // Outflow is calculated from the neighbours, and inflow from the outflow of the neighbours
        return 2;
    }

    @Override
    void erode(Evaluation evaluation, float[] heights, int width, int height, int iterations) {
        final float talus = (float) Math.tan(Math.toRadians(getDouble(ANGLE))), rate = (float) getDouble(STRENGTH) * 0.5f;
        final int n = heights.length;
        final float[] outflow = new float[n], excessSum = new float[n], next = new float[n];
        for (int iteration = 0; iteration < iterations; iteration++) {
            evaluation.checkCancelled();
            step(heights, next, outflow, excessSum, width, height, talus, rate);
        }
    }

    /**
     * Perform one iteration of thermal erosion in place. Looks at most two cells away.
     *
     * @param heights   The heights in units of cells.
     * @param next      Scratch space of the same size as {@code heights}.
     * @param outflow   Scratch space of the same size as {@code heights}.
     * @param excessSum Scratch space of the same size as {@code heights}.
     * @param talus     The steepest stable slope.
     * @param rate      The fraction of the excess material that moves.
     */
    static void step(float[] heights, float[] next, float[] outflow, float[] excessSum, int width, int height, float talus, float rate) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int i = x + y * width;
                final float h = heights[i];
                float sum = 0, max = 0;
                for (int k = 0; k < 8; k++) {
                    final int nx = x + DX[k], ny = y + DY[k];
                    if ((nx >= 0) && (nx < width) && (ny >= 0) && (ny < height)) {
                        final float excess = (h - heights[nx + ny * width]) - talus * DISTANCE[k];
                        if (excess > 0) {
                            sum += excess;
                            max = Math.max(max, excess);
                        }
                    }
                }
                outflow[i] = max * rate;
                excessSum[i] = sum;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int i = x + y * width;
                final float h = heights[i];
                float value = h - outflow[i];
                for (int k = 0; k < 8; k++) {
                    final int nx = x + DX[k], ny = y + DY[k];
                    if ((nx >= 0) && (nx < width) && (ny >= 0) && (ny < height)) {
                        final int j = nx + ny * width;
                        if (outflow[j] > 0) {
                            // The same expression as the neighbour used when it calculated its outflow
                            final float excess = (heights[j] - h) - talus * DISTANCE[k];
                            if (excess > 0) {
                                value += outflow[j] * excess / excessSum[j];
                            }
                        }
                    }
                }
                next[i] = value;
            }
        }
        System.arraycopy(next, 0, heights, 0, heights.length);
    }

    public static final NumberParameter ANGLE = new NumberParameter("angle", "Max slope", "Slopes steeper than this crumble", 40, 5, 85, 1, false, "\u00b0");
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "How quickly steep slopes crumble", 0.5, 0, 1, 0.01, false, null);

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1}, DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final float[] DISTANCE = {(float) Math.sqrt(2), 1, (float) Math.sqrt(2), 1, 1, (float) Math.sqrt(2), 1, (float) Math.sqrt(2)};
    private static final long serialVersionUID = 1L;
}
