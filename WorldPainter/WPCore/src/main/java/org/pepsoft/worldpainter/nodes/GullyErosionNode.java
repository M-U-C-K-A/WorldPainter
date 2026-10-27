package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A fast, procedural approximation of hydraulic erosion: carves branching gullies and valleys which run down the
 * slopes of its input. Each octave of gullies follows the slope of the terrain plus the gullies of the previous
 * octaves, so that smaller gullies branch off larger ones. Steep slopes are carved the most; flat land is left alone.
 *
 * <p>Unlike a simulation this is a function of the input and its slope at each location only, so it is fast, works at
 * any zoom level and is seamless across tiles.
 */
public class GullyErosionNode extends Node {
    @Override
    public String getName() {
        return "Erosion";
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
        return List.of(DEPTH, SPACING, OCTAVES, BRANCHING, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double depth = getDouble(DEPTH), spacing = getDouble(SPACING), branching = getDouble(BRANCHING);
        final int octaves = getInt(OCTAVES);
        final float verticalScale = context.getVerticalScale();
        final long seed = seed(context, getInt(VARIANT));
        return new CompiledNode(inputs, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                // Measure the slope over a distance proportional to the gully spacing, so that the gullies follow the
                // larger shapes of the terrain rather than its fine detail
                final double step = region.getStep();
                final int k = Math.max(1, (int) Math.round(spacing / 16 / step));
                final int width = region.getWidth(), height = region.getHeight(), paddedWidth = width + 2 * k;
                final Region padded = new Region(region.x(-k), region.y(-k), step, paddedWidth, height + 2 * k);
                final float[] in = input(evaluation, 0, padded);
                final double slopeFactor = verticalScale / (2 * k * step);
                final float[] result = new float[width * height];
                for (int j = 0; j < height; j++) {
                    if ((j & 0xf) == 0) {
                        evaluation.checkCancelled();
                    }
                    final double y = region.y(j);
                    for (int i = 0; i < width; i++) {
                        final int index = (i + k) + (j + k) * paddedWidth;
                        final double gx = (in[index + k] - in[index - k]) * slopeFactor;
                        final double gy = (in[index + k * paddedWidth] - in[index - k * paddedWidth]) * slopeFactor;
                        final double slope = Math.sqrt(gx * gx + gy * gy);
                        float value = in[index];
                        if (slope > MIN_SLOPE) {
                            final double mask = smoothStep(MIN_SLOPE, FULL_SLOPE, slope);
                            final double gullies = gullies(region.x(i), y, gx / slope, gy / slope);
                            // Gullies (-1) are carved to the full depth, ridges (1) are left alone
                            value -= (float) (depth * mask * (1 - gullies) / 2 / verticalScale);
                        }
                        result[i + j * width] = value;
                    }
                }
                return result;
            }

            /**
             * @return A value between -1 (the bottom of a gully) and 1 (a ridge between gullies).
             */
            private double gullies(double x, double y, double uphillX, double uphillY) {
                final double[] noise = BUFFER.get();
                double directionX = uphillX, directionY = uphillY, frequency = 1 / spacing, amplitude = 1, total = 0, totalAmplitude = 0;
                for (int octave = 0; octave < octaves; octave++) {
                    final double length = Math.sqrt(directionX * directionX + directionY * directionY);
                    if (length > 1e-9) {
                        gully(x * frequency + octave * 31.7, y * frequency - octave * 17.3, directionX / length, directionY / length, seed + octave, noise);
                        total += noise[0] * amplitude;
                        // Bend the direction of the next octave along the gullies so far, which makes them branch
                        directionX += noise[1] * amplitude * branching;
                        directionY += noise[2] * amplitude * branching;
                    }
                    totalAmplitude += amplitude;
                    amplitude *= 0.5;
                    frequency *= 2;
                }
                return total / totalAmplitude;
            }
        };
    }

    /**
     * Sum randomly placed, Gaussian weighted waves which vary across the slope, so that they form stripes running
     * downhill.
     *
     * @param result Receives the value (-1 to 1) and its derivative in X and Y.
     */
    static void gully(double x, double y, double uphillX, double uphillY, long seed, double[] result) {
        // The waves vary in the direction across the slope
        final double acrossX = -uphillY, acrossY = uphillX;
        final long cellX = (long) Math.floor(x), cellY = (long) Math.floor(y);
        final double fx = x - cellX, fy = y - cellY;
        double value = 0, derivativeX = 0, derivativeY = 0, totalWeight = 0;
        for (int j = -1; j <= 2; j++) {
            for (int i = -1; i <= 2; i++) {
                final long hash = hash(cellX + i, cellY + j, seed);
                final double dx = fx - i - ((hash & 0xffff) / 65536.0) * 0.5, dy = fy - j - (((hash >>> 16) & 0xffff) / 65536.0) * 0.5;
                final double weight = Math.exp(-(dx * dx + dy * dy) * 2);
                final double phase = (dx * acrossX + dy * acrossY) * TWO_PI;
                value += Math.cos(phase) * weight;
                final double slope = -Math.sin(phase) * weight;
                derivativeX += slope * acrossX;
                derivativeY += slope * acrossY;
                totalWeight += weight;
            }
        }
        result[0] = value / totalWeight;
        result[1] = derivativeX / totalWeight;
        result[2] = derivativeY / totalWeight;
    }

    private static long hash(long x, long y, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double smoothStep(double edge0, double edge1, double x) {
        final double t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }

    public static final NumberParameter DEPTH = new NumberParameter("depth", "Depth", "How deep the largest gullies are carved", 24, 0, 256, 1, false, "blocks");
    public static final NumberParameter SPACING = new NumberParameter("spacing", "Spacing", "The distance between the largest gullies", 128, 8, 2048, 1, true, "blocks");
    public static final NumberParameter OCTAVES = new NumberParameter("octaves", "Detail", "The number of levels of ever smaller gullies", 5, 1, 8, 1, false, null);
    public static final NumberParameter BRANCHING = new NumberParameter("branching", "Branching", "How strongly smaller gullies branch off larger ones", 0.5, 0, 1, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final double TWO_PI = 2 * Math.PI;
    /**
     * Slopes (in blocks per block) below which nothing is carved, and above which the full depth is carved.
     */
    private static final double MIN_SLOPE = 0.05, FULL_SLOPE = 0.6;
    private static final ThreadLocal<double[]> BUFFER = ThreadLocal.withInitial(() -> new double[3]);
    private static final long serialVersionUID = 1L;
}
