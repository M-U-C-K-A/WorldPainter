package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.FractalNoise;
import org.pepsoft.worldpainter.procedural.NoiseLayer;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A generator of table mountains: flat topped plateaus, in one or more levels, bounded by steep cliffs, with the
 * lowlands between them flat as well. Combine it with Strata and a cliff material for canyon walls.
 */
public class PlateausNode extends Node {
    @Override
    public String getName() {
        return "Plateaus";
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
        return List.of(SCALE, COVERAGE, LEVELS, CLIFFS, WARP, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final FractalNoise noise = new FractalNoise(seed, NoiseLayer.Type.FBM, getDouble(SCALE), 5, 0.5, getDouble(WARP), 0);
        final double coverage = getDouble(COVERAGE), cliffs = getDouble(CLIFFS);
        final int levels = getInt(LEVELS);
        // The thresholds of the levels, spread over the upper part of the noise range
        final double lowest = 1 - coverage, step = Math.max(0.02, (0.95 - lowest) / levels);
        final double halfWidth = 0.002 + 0.06 * (1 - cliffs);
        return new CompiledNode.Generator() {
            @Override
            float value(double x, double y) {
                final double n = noise.sample(x, y);
                double value = 0;
                for (int k = 0; k < levels; k++) {
                    final double threshold = lowest + k * step;
                    value += smoothstep(threshold - halfWidth, threshold + halfWidth, n);
                }
                return (float) (value / levels);
            }
        };
    }

    private static double smoothstep(double a, double b, double x) {
        final double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static final NumberParameter SCALE = new NumberParameter("scale", "Size", "The size of the plateaus", 600, 32, 8192, 1, true, "blocks");
    public static final NumberParameter COVERAGE = new NumberParameter("coverage", "Coverage", "How much of the land is raised", 0.45, 0, 1, 0.01, false, null);
    public static final NumberParameter LEVELS = new NumberParameter("levels", "Levels", "The number of stacked plateau levels", 2, 1, 6, 1, false, null);
    public static final NumberParameter CLIFFS = new NumberParameter("cliffs", "Cliffs", "How abrupt the cliffs between the levels are", 0.85, 0, 1, 0.01, false, null);
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How much the outlines are distorted, for more natural shapes", 120, 0, 2048, 1, false, "blocks");

    private static final long serialVersionUID = 1L;
}
