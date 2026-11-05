package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The dark variant of the Modern theme of WorldPainter: layered surfaces, a blue accent, softly rounded controls
 * and settings grouped on raised surfaces. Its colours and shapes are defined in
 * {@code WorldPainterModernDarkLaf.properties}, which starts from the Classic theme as it was when Modern was derived
 * from it.
 */
public class WorldPainterModernDarkLaf extends FlatDarkLaf {
    public static boolean setup() {
        return setup(new WorldPainterModernDarkLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Modern Dark";
    private static final long serialVersionUID = 1L;
}
