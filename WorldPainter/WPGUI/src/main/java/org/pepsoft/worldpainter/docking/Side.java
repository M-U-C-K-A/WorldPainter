package org.pepsoft.worldpainter.docking;

/**
 * The side of the workspace to which a {@link DockableFrame} can be docked.
 */
public enum Side {
    WEST, EAST;

    public Side opposite() {
        return (this == WEST) ? EAST : WEST;
    }
}
