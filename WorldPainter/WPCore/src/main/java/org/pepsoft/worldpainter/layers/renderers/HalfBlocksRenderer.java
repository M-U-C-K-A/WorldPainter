package org.pepsoft.worldpainter.layers.renderers;

/**
 * Shows where the land is built in half blocks with thin diagonal lines, which leave the colours of the land visible.
 */
public class HalfBlocksRenderer implements BitLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, boolean value) {
        if (value && (((x + y) & 7) < 2)) {
            // Lines two pixels wide, mostly a warm amber
            final int red   = (((underlyingColour >> 16) & 0xff) + 3 * 0xf0) / 4;
            final int green = (((underlyingColour >>  8) & 0xff) + 3 * 0x9c) / 4;
            final int blue  = (( underlyingColour        & 0xff) + 3 * 0x20) / 4;
            return (red << 16) | (green << 8) | blue;
        } else {
            return underlyingColour;
        }
    }
}
