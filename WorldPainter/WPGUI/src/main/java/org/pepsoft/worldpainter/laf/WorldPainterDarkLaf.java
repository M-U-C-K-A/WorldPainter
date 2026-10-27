package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The modern dark theme of WorldPainter: a neutral zinc palette with an orange accent, rounded corners and hairline
 * borders. The colours are defined in {@code WorldPainterDarkLaf.properties}.
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
