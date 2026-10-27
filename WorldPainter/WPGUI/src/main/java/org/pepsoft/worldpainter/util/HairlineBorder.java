package org.pepsoft.worldpainter.util;

import javax.swing.border.AbstractBorder;
import java.awt.*;

/**
 * A one pixel border on selected sides, in the border colour of the current theme (looked up at paint time, so that
 * it follows theme changes), with optional padding inside.
 */
public class HairlineBorder extends AbstractBorder {
    public HairlineBorder(boolean top, boolean left, boolean bottom, boolean right) {
        this(top, left, bottom, right, new Insets(0, 0, 0, 0));
    }

    public HairlineBorder(boolean top, boolean left, boolean bottom, boolean right, Insets padding) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.padding = padding;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(ThemeUtils.getColour("WorldPainter.border", ThemeUtils.getColour("Component.borderColor", Color.LIGHT_GRAY)));
        if (top) {
            g.fillRect(x, y, width, 1);
        }
        if (bottom) {
            g.fillRect(x, y + height - 1, width, 1);
        }
        if (left) {
            g.fillRect(x, y, 1, height);
        }
        if (right) {
            g.fillRect(x + width - 1, y, 1, height);
        }
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.top = (top ? 1 : 0) + padding.top;
        insets.left = (left ? 1 : 0) + padding.left;
        insets.bottom = (bottom ? 1 : 0) + padding.bottom;
        insets.right = (right ? 1 : 0) + padding.right;
        return insets;
    }

    private final boolean top, left, bottom, right;
    private final Insets padding;

    private static final long serialVersionUID = 1L;
}
