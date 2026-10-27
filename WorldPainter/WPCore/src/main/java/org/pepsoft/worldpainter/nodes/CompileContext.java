package org.pepsoft.worldpainter.nodes;

import java.awt.*;

/**
 * Global information which nodes may use when they are compiled.
 */
final class CompileContext {
    CompileContext(long seed, float minHeight, float maxHeight, Rectangle mapBounds, MapSource mapSource) {
        this.seed = seed;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.mapBounds = mapBounds;
        this.mapSource = mapSource;
    }

    long getSeed() {
        return seed;
    }

    /**
     * The height in blocks corresponding to a value of zero.
     */
    float getMinHeight() {
        return minHeight;
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

    /**
     * The existing map, or {@code null} if not available.
     */
    MapSource getMapSource() {
        return mapSource;
    }

    private final long seed;
    private final float minHeight, maxHeight;
    private final Rectangle mapBounds;
    private final MapSource mapSource;
}
