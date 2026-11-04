package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.layers.HalfBlocks;

/**
 * Smooths the land like {@link Smooth}, and builds it in half blocks in Minecraft where it went: slabs on stone,
 * cobblestone, sandstone and the like, and snow layers on snow blocks, so that gentle slopes go up by half a block
 * instead of a whole block every other column. Right-dragging builds the land in whole blocks again, without changing
 * it.
 */
public class SmoothHalfBlocks extends Smooth {
    public SmoothHalfBlocks(WorldPainter view) {
        super("Half Blocks", "Smooth the land and build it in half blocks: slabs and snow layers",
                "<p>Drag to smooth the land and build it in half blocks in Minecraft: slabs on stone, cobblestone, sandstone and the like, snow layers on snow blocks.<p>Right-drag to go back to whole blocks.",
                view, "operation.smoothHalfBlocks");
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        if (! inverse) {
            super.tick(centreX, centreY, false, first, dynamicLevel);
        }
        final int radius = getEffectiveRadius();
        dimension.setEventsInhibited(true);
        try {
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                for (int y = centreY - radius; y <= centreY + radius; y++) {
                    if (getStrength(centreX, centreY, x, y) > 0.0f) {
                        dimension.setBitLayerValueAt(HalfBlocks.INSTANCE, x, y, ! inverse);
                    }
                }
            }
        } finally {
            dimension.setEventsInhibited(false);
        }
    }
}
