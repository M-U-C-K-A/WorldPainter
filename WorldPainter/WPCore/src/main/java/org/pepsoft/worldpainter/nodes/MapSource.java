package org.pepsoft.worldpainter.nodes;

/**
 * Read access to the existing map, for nodes which build on it. Implementations must be thread safe.
 */
public interface MapSource {
    /**
     * The terrain height at a block, or {@link Float#NaN} if the block is not part of the map.
     */
    float getHeight(int x, int y);

    /**
     * Whether a block is selected.
     */
    boolean isSelected(int x, int y);
}
