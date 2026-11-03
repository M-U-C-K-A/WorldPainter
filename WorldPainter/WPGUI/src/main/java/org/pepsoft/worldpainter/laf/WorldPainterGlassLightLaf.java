package org.pepsoft.worldpainter.laf;

/**
 * The light variant of the Glass theme of WorldPainter: windows of frosted glass over a soft gradient, translucent
 * cards with large rounded corners, pill shaped buttons and tabs, and a black accent. It builds on the Modern theme
 * ({@link WorldPainterModernLightLaf}), of which it keeps the layout and the line icons, and overrides its colours and
 * shapes in {@code WorldPainterGlassLightLaf.properties}.
 */
public class WorldPainterGlassLightLaf extends WorldPainterModernLightLaf {
    public static boolean setup() {
        return setup(new WorldPainterGlassLightLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Glass Light";
    private static final long serialVersionUID = 1L;
}
