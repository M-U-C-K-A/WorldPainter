package org.pepsoft.worldpainter.nodes;

import java.awt.*;

/**
 * Global information which nodes may use when they are compiled.
 */
final class CompileContext {
    CompileContext(long seed, float minHeight, float maxHeight, Rectangle mapBounds) {
        this.seed = seed;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.mapBounds = mapBounds;
    }

    long getSeed() {
        return seed;
    }

    /**
     * The number of blocks corresponding to a value difference of one, for nodes that need physical slopes.
     */
    float getVerticalScale() {
        return Math.max(1.0f, maxHeight - minHeight);
    }

    /**
     * The extent of the map in blocks, or {@code null} if not known.
     */
    Rectangle getMapBounds() {
        return mapBounds;
    }

    private final long seed;
    private final float minHeight, maxHeight;
    private final Rectangle mapBounds;
}
