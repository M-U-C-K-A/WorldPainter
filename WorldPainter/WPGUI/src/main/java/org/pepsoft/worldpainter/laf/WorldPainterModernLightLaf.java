package org.pepsoft.worldpainter.laf;

/**
 * The light variant of the Modern theme of WorldPainter: layered surfaces, a blue accent, softly rounded controls
 * and settings grouped on raised surfaces. It builds on the Classic theme ({@link WorldPainterLightLaf}) and overrides
 * its colours and shapes in {@code WorldPainterModernLightLaf.properties}.
 */
public class WorldPainterModernLightLaf extends WorldPainterLightLaf {
    public static boolean setup() {
        return setup(new WorldPainterModernLightLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Modern Light";
    private static final long serialVersionUID = 1L;
}
