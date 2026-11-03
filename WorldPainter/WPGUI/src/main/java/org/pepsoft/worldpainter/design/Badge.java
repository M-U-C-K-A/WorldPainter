package org.pepsoft.worldpainter.design;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;

/**
 * A small tag next to a title which says something important about a setting at a glance, such as that it only takes
 * effect after a restart.
 */
public final class Badge extends JComponent {
    public enum Kind {
        EXPERIMENTAL("Experimental", 0xd9822b),
        RESTART("Restart required", 0x8b5cf6),
        RELOAD("After reloading the map", 0x8b5cf6),
        NEXT_EXPORT("On the next export", 0x0ea5e9),
        NEW_WORLDS("New worlds only", 0x0ea5e9),
        SLOW("Can be slow", 0xef4444),
        PERFORMANCE("Performance", 0x16a34a);

        Kind(String text, int colour) {
            this.text = text;
            this.colour = new Color(colour);
        }

        final String text;
        final Color colour;
    }

    public Badge(Kind kind) {
        this.kind = kind;
        setToolTipText(kind.text);
    }

    @Override
    public Dimension getPreferredSize() {
        final FontMetrics metrics = getFontMetrics(font());
        return new Dimension(metrics.stringWidth(kind.text.toUpperCase()) + UIScale.scale(10), metrics.getHeight() + UIScale.scale(2));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final Color colour = kind.colour;
            g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 140));
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setFont(font());
            g2.setColor(Design.isDark() ? Design.mix(colour, Color.WHITE, 0.35f) : Design.mix(colour, Color.BLACK, 0.2f));
            final FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(kind.text.toUpperCase(), UIScale.scale(5), (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
        } finally {
            g2.dispose();
        }
    }

    private static Font font() {
        final Font base = UIManager.getFont("Label.font");
        return base.deriveFont(Font.BOLD, base.getSize2D() - 3f);
    }

    private final Kind kind;
}
