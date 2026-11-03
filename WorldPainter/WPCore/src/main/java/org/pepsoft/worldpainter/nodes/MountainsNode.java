package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * Ranges of sharp, eroded peaks rising from lower land: ridged noise where a broad Perlin noise says there are
 * mountains, on top of gently rolling lowlands, carved by gullies.
 */
public class MountainsNode extends LandformNode {
    @Override
    public String getName() {
        return "Mountains";
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(SIZE, COVER, PEAKS, EROSION, ROUGHNESS, OCTAVES, WARP, VARIANT);
    }

    @Override
    public boolean isSecondary(Parameter<?> parameter) {
        return super.isSecondary(parameter) || (parameter == ROUGHNESS);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double size = getDouble(SIZE), cover = getDouble(COVER), erosion = getDouble(EROSION);
        final CompiledNode base = part(context, noise(NoiseNode.Kind.PERLIN, size * 2.6, 5, 0.5, 0, 0));
        final CompiledNode peaks = part(context, noise(NoiseNode.Kind.RIDGED, size, getInt(OCTAVES), getDouble(ROUGHNESS), getDouble(WARP), 1));
        // The lowlands follow the broad noise; the mountains rise where it is high, more of them with more cover
        final CompiledNode lowlands = part(context, new CurveNode(0, 0.05f, 1, 0.45f), base);
        final float threshold = (float) (0.7 - 0.5 * cover);
        final CompiledNode ranges = part(context, new CurveNode(0, 0, threshold, 0, Math.min(threshold + 0.3f, 0.99f), 1, 1, 1), base);
        final CombineNode multiply = new CombineNode(CombineNode.Mode.MULTIPLY);
        final CompiledNode mountains = part(context, multiply, peaks, ranges);
        final CombineNode add = new CombineNode(CombineNode.Mode.ADD);
        add.set(CombineNode.STRENGTH, getDouble(PEAKS));
        CompiledNode terrain = part(context, add, lowlands, mountains);
        if (erosion > 0) {
            final GullyErosionNode gullies = new GullyErosionNode();
            gullies.set(GullyErosionNode.DEPTH, erosion);
            gullies.set(GullyErosionNode.SPACING, Math.max(32, size / 7));
            gullies.set(VARIANT, getInt(VARIANT) + 6);
            terrain = part(context, gullies, terrain);
        }
        return terrain;
    }

    public static final NumberParameter SIZE = new NumberParameter("scale", "Size", "The size of the mountains", 900, 64, 16384, 1, true, "blocks");
    public static final NumberParameter COVER = new NumberParameter("cover", "Mountainous", "How much of the land is mountains rather than lowlands", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter PEAKS = new NumberParameter("peaks", "Peak height", "How high the mountains rise above the lowlands", 0.65, 0, 1.5, 0.01, false, null);
    public static final NumberParameter EROSION = new NumberParameter("erosion", "Erosion", "How deep the gullies are which are carved down the slopes; zero for none", 40, 0, 256, 1, false, "blocks");
    public static final NumberParameter ROUGHNESS = new NumberParameter("roughness", "Roughness", "How strong the finer detail of the peaks is", 0.5, 0, 1, 0.01, false, null);
    public static final NumberParameter OCTAVES = new NumberParameter("octaves", "Detail", "The number of levels of finer detail of the peaks", 7, 1, 10, 1, false, null);
    public static final NumberParameter WARP = new NumberParameter("warp", "Warp", "How much the ranges are twisted, for a more organic look", 0, 0, 2048, 1, false, "blocks");

    private static final long serialVersionUID = 1L;
}
