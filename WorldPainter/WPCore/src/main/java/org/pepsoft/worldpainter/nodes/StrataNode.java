package org.pepsoft.worldpainter.nodes;

import org.pepsoft.worldpainter.procedural.SimplexNoise;

import java.util.List;

/**
 * A filter which breaks slopes up into ledges and bands of rock of uneven thickness, which dip and wander across the
 * map, like the layered walls of a canyon or a cliff.
 */
public class StrataNode extends Node {
    @Override
    public String getName() {
        return "Strata";
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
        return List.of(LAYERS, IRREGULARITY, SHARPNESS, STRENGTH, VARIANT);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final long seed = seed(context, getInt(VARIANT));
        final SimplexNoise noise = new SimplexNoise(seed);
        final float layers = getInt(LAYERS), irregularity = (float) getDouble(IRREGULARITY), strength = (float) getDouble(STRENGTH);
        final double exponent = 1 + 9 * getDouble(SHARPNESS);
        return new CompiledNode(new CompiledNode[] {inputs[0]}, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final float[] in = input(evaluation, 0, region);
                final float[] values = new float[in.length];
                final int width = region.getWidth(), height = region.getHeight();
                for (int j = 0; j < height; j++) {
                    final double y = region.y(j);
                    for (int i = 0; i < width; i++) {
                        final double x = region.x(i);
                        final int index = i + j * width;
                        final float value = in[index];
                        // The layers dip and wander: offset them by a slow noise, and vary their thickness by a
                        // second one
                        final float offset = (float) (noise.noise(x / 700, y / 700) * 0.6 + noise.noise(x / 180 + 17.3, y / 180 - 4.1) * 0.25) * irregularity;
                        final float k = (value * layers) + offset;
                        final float floor = (float) Math.floor(k);
                        final float within = k - floor;
                        // Uneven layers: each band has its own steepness
                        final double bandExponent = exponent * (0.6 + 0.8 * Math.abs(Math.sin(floor * 12.9898 + seed)));
                        final float stepped = (floor + (float) Math.pow(within, bandExponent) - offset) / layers;
                        values[index] = value + (stepped - value) * strength;
                    }
                }
                return values;
            }
        };
    }

    public static final NumberParameter LAYERS = new NumberParameter("layers", "Layers", "The number of rock layers between value 0 and 1", 14, 2, 64, 1, false, null);
    public static final NumberParameter IRREGULARITY = new NumberParameter("irregularity", "Irregularity", "How much the layers dip and wander", 0.6, 0, 2, 0.01, false, null);
    public static final NumberParameter SHARPNESS = new NumberParameter("sharpness", "Sharpness", "How flat the ledges and how steep the faces between them are", 0.6, 0, 1, 0.01, false, null);
    public static final NumberParameter STRENGTH = new NumberParameter("strength", "Strength", "How strongly the layering is applied", 1, 0, 1, 0.01, false, null);

    private static final List<String> INPUT_NAMES = List.of("In");
    private static final long serialVersionUID = 1L;
}
