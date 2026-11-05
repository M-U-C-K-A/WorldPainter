package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * The light variant of the Classic theme of WorldPainter, the look of a classic desktop application: a grey window, square controls with clear borders, buttons with a slight gradient,
 * scroll bars with arrows, card shaped tabs, yellow tooltips and a steel blue selection,
 * with the font of the system and the original icons of WorldPainter. The colours are defined in
 * {@code WorldPainterLightLaf.properties}.
 */
public class WorldPainterLightLaf extends FlatLightLaf {
    public static boolean setup() {
        return setup(new WorldPainterLightLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Light";
    private static final long serialVersionUID = 1L;
}
