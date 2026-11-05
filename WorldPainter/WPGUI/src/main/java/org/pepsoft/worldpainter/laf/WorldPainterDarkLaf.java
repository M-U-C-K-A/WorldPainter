package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The dark variant of the Classic theme of WorldPainter, the look of a classic desktop application: a charcoal window in the spirit of Darcula, square controls with clear borders,
 * buttons with a slight gradient, scroll bars with arrows, card shaped tabs and a slate blue selection,
 * with the font of the system and the original icons of WorldPainter. The colours are defined in
 * {@code WorldPainterDarkLaf.properties}.
 */
public class WorldPainterDarkLaf extends FlatDarkLaf {
    public static boolean setup() {
        return setup(new WorldPainterDarkLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Dark";
    private static final long serialVersionUID = 1L;
}
