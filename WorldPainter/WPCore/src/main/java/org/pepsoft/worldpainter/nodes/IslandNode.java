package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * An island in the middle of the map: a radial falloff to the edges of the map, with a coast made irregular by noise,
 * shaped by a curve into a sea floor, a coast and higher land inland.
 */
public class IslandNode extends LandformNode {
    @Override
    public String getName() {
        return "Island";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SIZE, IRREGULARITY, COAST_SIZE, HEIGHT, COAST, OCTAVES, VARIANT);
    }

    @Override
    public boolean isSecondary(Parameter<?> parameter) {
        return super.isSecondary(parameter) || (parameter == COAST);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final CompiledNode falloff = part(context, new GradientNode());
        final CompiledNode noise = part(context, noise(NoiseNode.Kind.PERLIN, getDouble(COAST_SIZE), getInt(OCTAVES), 0.55, getDouble(COAST_SIZE) / 10, 0));
        // The falloff, shifted up and down by the noise to make the coast irregular
        final float irregularity = (float) getDouble(IRREGULARITY);
        final CompiledNode shape = new CompiledNode(new CompiledNode[] {falloff, noise}, 0.0f, 0.5f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final float[] f = input(evaluation, 0, region), n = input(evaluation, 1, region);
                final float[] result = new float[f.length];
                for (int i = 0; i < f.length; i++) {
                    result[i] = f[i] + (n[i] - 0.5f) * irregularity;
                }
                return result;
            }
        };
        // The coast is where the shape reaches one minus the size; the land rises inland of it
        final float size = (float) getDouble(SIZE), coast = (float) getDouble(COAST), height = (float) getDouble(HEIGHT);
        final float shoreline = Math.max(0.02f, Math.min(0.98f, 1 - size));
        return part(context, new CurveNode(
                -0.5f, 0,
                shoreline * 0.6f, coast * 0.35f,
                shoreline, coast,
                shoreline + (1 - shoreline) * 0.35f, coast + (height - coast) * 0.45f,
                1.5f, height), shape);
    }

    public static final NumberParameter SIZE = new NumberParameter("size", "Size", "How much of the map the island covers", 0.55, 0.05, 0.98, 0.01, false, null);
    public static final NumberParameter IRREGULARITY = new NumberParameter("irregularity", "Irregular coast", "How far the coast wanders in and out, with bays and headlands", 0.4, 0, 1, 0.01, false, null);
    public static final NumberParameter COAST_SIZE = new NumberParameter("coastSize", "Size of the bays", "The size of the bays and headlands along the coast", 320, 16, 8192, 1, true, "blocks");
    public static final NumberParameter HEIGHT = new NumberParameter("height", "Height", "The value at the highest point of the island", 0.8, 0, 1, 0.01, false, null);
    public static final NumberParameter COAST = new NumberParameter("coast", "Coast at", "The value along the coast; the output of a new graph turns 0.2 into the water level", 0.2, 0, 1, 0.01, false, null);
    public static final NumberParameter OCTAVES = new NumberParameter("octaves", "Detail", "The number of levels of finer detail along the coast", 6, 1, 10, 1, false, null);

    private static final long serialVersionUID = 1L;
}
