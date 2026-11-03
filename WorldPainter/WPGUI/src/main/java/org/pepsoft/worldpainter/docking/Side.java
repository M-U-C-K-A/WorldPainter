package org.pepsoft.worldpainter.docking;

/**
 * A side of an area or of the map: where to put an area next to another one, or which panels around the map to fold
 * away.
 */
public enum Side {
    WEST, EAST, NORTH, SOUTH;

    /**
     * Whether this side is to the left or right (or else above or below).
     */
    public boolean isHorizontal() {
        return (this == WEST) || (this == EAST);
    }

    /**
     * Whether this side comes first: to the left or above.
     */
    public boolean isBefore() {
        return (this == WEST) || (this == NORTH);
    }

    public Side opposite() {
        switch (this) {
            case WEST:
                return EAST;
            case EAST:
                return WEST;
            case NORTH:
                return SOUTH;
            default:
                return NORTH;
        }
    }
}
