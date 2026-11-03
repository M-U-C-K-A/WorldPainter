package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Gently rolling hills: smooth noise, with its tops rounded off and its lows filled in by a curve.
 */
public class HillsNode extends LandformNode {
    @Override
    public String getName() {
        return "Rolling hills";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SIZE, HEIGHT, BASE, ROUGHNESS, OCTAVES, WARP, VARIANT);
    }

    @Override
    public boolean isSecondary(Parameter<?> parameter) {
        return super.isSecondary(parameter) || (parameter == ROUGHNESS);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final CompiledNode noise = part(context, noise(NoiseNode.Kind.PERLIN, getDouble(SIZE), getInt(OCTAVES), getDouble(ROUGHNESS), getDouble(WARP), 0));
        // Soft S curve from the base level up to the base plus the height of the hills
        final float base = (float) getDouble(BASE), height = (float) getDouble(HEIGHT);
        return part(context, new CurveNode(0, base, 0.3f, base + height * 0.12f, 0.7f, base + height * 0.88f, 1, base + height), noise);
    }

    public static final NumberParameter SIZE = new NumberParameter("scale", "Size", "The size of the hills", 400, 32, 8192, 1, true, "blocks");
    public static final NumberParameter HEIGHT = new NumberParameter("height", "Hill height", "How high the hills rise above their base", 0.35, 0, 1, 0.01, false, null);
    public static final NumberParameter BASE = new NumberParameter("base", "Base", "The value at the foot of the hills; the output of a new graph turns 0.2 into the water level", 0.25, 0, 1, 0.01, false, null);
    public static final NumberParameter ROUGHNESS = new NumberParameter("roughness", "Roughness", "How strong the finer detail is", 0.4, 0, 1, 0.01, false, null);
    public static final NumberParameter OCTAVES = new NumberParameter("octaves", "Detail", "The number of levels of finer detail", 5, 1, 10, 1, false, null);
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How much the hills are twisted, for a more organic look", 24, 0, 2048, 1, false, "blocks");

    private static final long serialVersionUID = 1L;
}
