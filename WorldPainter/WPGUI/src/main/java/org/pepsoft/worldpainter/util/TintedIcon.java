package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Paints a monochrome icon in the foreground colour of the current theme (looked up at paint time), so that black
 * glyphs remain visible on dark themes. The alpha of the original icon is preserved.
 */
public class TintedIcon implements Icon {
    public TintedIcon(Icon delegate) {
        this.delegate = delegate;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        final int width = getIconWidth(), height = getIconHeight();
        if ((width <= 0) || (height <= 0)) {
            return;
        }
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = image.createGraphics();
        try {
            delegate.paintIcon(c, g2, 0, 0);
            g2.setComposite(AlphaComposite.SrcAtop);
            g2.setColor(ThemeUtils.getColour("Label.foreground", Color.BLACK));
            g2.fillRect(0, 0, width, height);
        } finally {
            g2.dispose();
        }
        g.drawImage(image, x, y, null);
    }

    @Override
    public int getIconWidth() {
        return delegate.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return delegate.getIconHeight();
    }

    private final Icon delegate;
}
