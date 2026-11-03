package org.pepsoft.worldpainter.docking;

/**
 * The side of the workspace to which a {@link DockableFrame} can be docked.
 */
public enum Side {
    WEST("Move to left side"), EAST("Move to right side"), SOUTH("Move to bottom");

    Side(String moveText) {
        this.moveText = moveText;
    }

    /**
     * The text of the menu item which moves a frame to this side.
     */
    public String getMoveText() {
        return moveText;
    }

    private final String moveText;
}
