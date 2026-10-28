package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A mask which is one where the terrain is between two heights, fading out over a distance. Works on the final
 * terrain unless something else is connected.
 */
public class HeightMaskNode extends Node {
    @Override
    public String getName() {
        return "Height range";
    }

    @Override
    public String getTitle() {
        return "Height " + Math.round(getDouble(FROM)) + "\u2013" + Math.round(getDouble(TO));
    }

    @Override
    public NodeCategory getCategory() {
        return NodeCategory.MASK;
    }

    @Override
    public List<String> getInputNames() {
        return INPUT_NAMES;
    }

    @Override
    public boolean isTerrainInput(int index) {
        return true;
    }

    @Override
    public List<Parameter<?>> getParameters() {
        return List.of(FROM, TO, FADE);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double from = getDouble(FROM), to = getDouble(TO), fade = getDouble(FADE);
        final double minHeight = context.getMinHeight(), verticalScale = context.getVerticalScale();
        return new CompiledNode.Filter(inputs[0]) {
            @Override
            float apply(float value) {
                final double height = minHeight + value * verticalScale;
                return (float) (rise(height, from - fade, from) * (1 - rise(height, to, to + fade)));
            }
        };
    }

    /**
     * Zero below {@code edge0}, one above {@code edge1} and smooth in between (a step if they are equal).
     */
    static double rise(double x, double edge0, double edge1) {
        if (edge1 <= edge0) {
            return (x >= edge1) ? 1 : 0;
        }
        final double t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }

    public static final NumberParameter FROM = new NumberParameter("from", "From", "The lowest height that is selected", 100, -2048, 4096, 1, false, "blocks");
    public static final NumberParameter TO = new NumberParameter("to", "To", "The highest height that is selected", 320, -2048, 4096, 1, false, "blocks");
    public static final NumberParameter FADE = new NumberParameter("fade", "Fade", "The distance over which the mask fades out beyond the range", 8, 0, 256, 1, false, "blocks");

    private static final List<String> INPUT_NAMES = List.of("Height");
    private static final long serialVersionUID = 1L;
}
