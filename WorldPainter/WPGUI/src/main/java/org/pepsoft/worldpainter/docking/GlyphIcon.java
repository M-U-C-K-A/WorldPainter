package org.pepsoft.worldpainter.docking;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static org.pepsoft.util.GUIUtils.getUIScale;

/**
 * Small vector icons for the dock headers, painted in the foreground colour of the component so that they work with
 * any look and feel and at any UI scale.
 */
final class GlyphIcon implements Icon {
    GlyphIcon(Glyph glyph) {
        this.glyph = glyph;
        size = Math.round(12 * getUIScale());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor((c != null) && c.isEnabled() ? c.getForeground() : Color.GRAY);
            g2.translate(x, y);
            final float s = size / 12f;
            g2.scale(s, s);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (glyph) {
                case CLOSE:
                    g2.draw(new java.awt.geom.Line2D.Float(3, 3, 9, 9));
                    g2.draw(new java.awt.geom.Line2D.Float(9, 3, 3, 9));
                    break;
                case FLOAT:
                    // A window with an arrow pointing out of it
                    g2.draw(new java.awt.geom.Rectangle2D.Float(1.5f, 4.5f, 6, 6));
                    final Path2D arrow = new Path2D.Float();
                    arrow.moveTo(6, 6);
                    arrow.lineTo(10.5, 1.5);
                    arrow.moveTo(7, 1.5);
                    arrow.lineTo(10.5, 1.5);
                    arrow.lineTo(10.5, 5);
                    g2.draw(arrow);
                    break;
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    private final Glyph glyph;
    private final int size;

    enum Glyph {CLOSE, FLOAT}
}
