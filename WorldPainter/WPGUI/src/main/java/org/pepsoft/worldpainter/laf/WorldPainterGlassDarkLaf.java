package org.pepsoft.worldpainter.laf;

/**
 * The dark variant of the Glass theme of WorldPainter: windows of frosted glass over a soft gradient, translucent
 * cards with large rounded corners, pill shaped buttons and tabs, and a white accent. It builds on the Modern theme
 * ({@link WorldPainterModernDarkLaf}), of which it keeps the layout and the line icons, and overrides its colours and
 * shapes in {@code WorldPainterGlassDarkLaf.properties}.
 */
public class WorldPainterGlassDarkLaf extends WorldPainterModernDarkLaf {
    public static boolean setup() {
        return setup(new WorldPainterGlassDarkLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final String NAME = "WorldPainter Glass Dark";
    private static final long serialVersionUID = 1L;
}
