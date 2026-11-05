package org.pepsoft.worldpainter.laf;

/**
 * The dark variant of the Tactile theme of WorldPainter: the layout of the Modern theme, with controls you feel you could
 * touch: buttons standing half out of the window (coloured for the default button), sliders sunk into grooves, selected
 * tools standing out as tiles, and the map on a grid. The colours are defined in
 * {@code WorldPainterTactileDarkLaf.properties}.
 */
public class WorldPainterTactileDarkLaf extends WorldPainterModernDarkLaf {
    public static boolean setup() {
        return setup(new WorldPainterTactileDarkLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Tactile Dark";
    private static final long serialVersionUID = 1L;
}
