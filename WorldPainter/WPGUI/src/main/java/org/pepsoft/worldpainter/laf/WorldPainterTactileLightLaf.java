package org.pepsoft.worldpainter.laf;

/**
 * The light variant of the Tactile theme of WorldPainter: the layout of the Modern theme, with controls you feel you could
 * touch: buttons standing half out of the window (coloured for the default button), sliders sunk into grooves, selected
 * tools standing out as tiles, and the map on a grid. The colours are defined in
 * {@code WorldPainterTactileLightLaf.properties}.
 */
public class WorldPainterTactileLightLaf extends WorldPainterModernLightLaf {
    public static boolean setup() {
        return setup(new WorldPainterTactileLightLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Tactile Light";
    private static final long serialVersionUID = 1L;
}
