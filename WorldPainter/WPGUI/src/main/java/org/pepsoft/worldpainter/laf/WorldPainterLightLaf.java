package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * The modern light theme of WorldPainter: a neutral zinc palette with an orange accent, rounded corners and hairline
 * borders. The colours are defined in {@code WorldPainterLightLaf.properties}.
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
