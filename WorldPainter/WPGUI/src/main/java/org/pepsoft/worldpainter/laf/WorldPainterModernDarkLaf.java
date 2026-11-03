package org.pepsoft.worldpainter.laf;

/**
 * The dark variant of the Modern theme of WorldPainter: layered surfaces, a blue accent, softly rounded controls
 * and settings grouped on raised surfaces. It builds on the Classic theme ({@link WorldPainterDarkLaf}) and overrides
 * its colours and shapes in {@code WorldPainterModernDarkLaf.properties}.
 */
public class WorldPainterModernDarkLaf extends WorldPainterDarkLaf {
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
