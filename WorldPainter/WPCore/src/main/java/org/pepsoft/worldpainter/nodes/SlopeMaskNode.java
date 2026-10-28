package org.pepsoft.worldpainter.nodes;

import java.util.List;

/**
 * A mask which is one where the terrain is between two steepnesses, for instance to put rock on cliffs or keep forests
 * off them. The slope is measured over {@value #DISTANCE} blocks, the same in the preview as on the map. Works on the
 * final terrain unless something else is connected.
 */
public class SlopeMaskNode extends Node {
    @Override
    public String getName() {
        return "Slope range";
    }

    @Override
    public String getTitle() {
        return "Slope " + Math.round(getDouble(MIN)) + "\u2013" + Math.round(getDouble(MAX)) + "\u00b0";
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
        return List.of(MIN, MAX, FADE);
    }

    @Override
    CompiledNode compile(CompileContext context, CompiledNode[] inputs) {
        final double min = getDouble(MIN), max = getDouble(MAX), fade = getDouble(FADE);
        final double toBlocksPerBlock = context.getVerticalScale() / (2 * DISTANCE);
        return new CompiledNode(inputs, 0.0f) {
            @Override
            float[] compute(Evaluation evaluation, Region region) {
                final int width = region.getWidth(), height = region.getHeight();
                final float[] west, east, north, south;
                final int samples = (int) Math.round(DISTANCE / region.getStep());
                if (Math.abs(samples * region.getStep() - DISTANCE) < 1e-9) {
                    // The distance is a whole number of samples: evaluate one padded region and offset into it
                    final int paddedWidth = width + 2 * samples;
                    final float[] padded = input(evaluation, 0, new Region(region.x(-samples), region.y(-samples), region.getStep(), paddedWidth, height + 2 * samples));
                    west = new float[width * height];
                    east = new float[width * height];
                    north = new float[width * height];
                    south = new float[width * height];
                    for (int j = 0; j < height; j++) {
                        for (int i = 0; i < width; i++) {
                            final int index = (i + samples) + (j + samples) * paddedWidth;
                            west[i + j * width] = padded[index - samples];
                            east[i + j * width] = padded[index + samples];
                            north[i + j * width] = padded[index - samples * paddedWidth];
                            south[i + j * width] = padded[index + samples * paddedWidth];
                        }
                    }
                } else {
                    final double step = region.getStep();
                    west = input(evaluation, 0, new Region(region.getX0() - DISTANCE, region.getY0(), step, width, height));
                    east = input(evaluation, 0, new Region(region.getX0() + DISTANCE, region.getY0(), step, width, height));
                    north = input(evaluation, 0, new Region(region.getX0(), region.getY0() - DISTANCE, step, width, height));
                    south = input(evaluation, 0, new Region(region.getX0(), region.getY0() + DISTANCE, step, width, height));
                }
                final float[] result = new float[width * height];
                for (int i = 0; i < result.length; i++) {
                    final double gx = (east[i] - west[i]) * toBlocksPerBlock, gy = (south[i] - north[i]) * toBlocksPerBlock;
                    final double degrees = Math.toDegrees(Math.atan(Math.sqrt(gx * gx + gy * gy)));
                    result[i] = (float) (HeightMaskNode.rise(degrees, min - fade, min) * (1 - HeightMaskNode.rise(degrees, max, max + fade)));
                }
                return result;
            }
        };
    }

    public static final NumberParameter MIN = new NumberParameter("min", "From", "The gentlest slope that is selected (0\u00b0 is flat)", 35, 0, 90, 1, false, "\u00b0");
    public static final NumberParameter MAX = new NumberParameter("max", "To", "The steepest slope that is selected (90\u00b0 is vertical)", 90, 0, 90, 1, false, "\u00b0");
    public static final NumberParameter FADE = new NumberParameter("fade", "Fade", "How gradually the mask fades out beyond the range", 5, 0, 45, 1, false, "\u00b0");

    /**
     * The horizontal distance in blocks over which the slope is measured, on either side of each block.
     */
    static final double DISTANCE = 2;

    private static final List<String> INPUT_NAMES = List.of("Height");
    private static final long serialVersionUID = 1L;
}
