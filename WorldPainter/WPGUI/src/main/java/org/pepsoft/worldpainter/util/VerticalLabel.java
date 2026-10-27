package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * A label which can display its text rotated by 90 degrees, reading either bottom to top (counter-clockwise, the
 * default) or top to bottom (clockwise).
 */
public class VerticalLabel extends JLabel {
    public VerticalLabel() {
        this("");
    }

    public VerticalLabel(String text) {
        super(text);
    }

    public int getOrientation() {
        return orientation;
    }

    /**
     * @param orientation {@link SwingConstants#VERTICAL} or {@link SwingConstants#HORIZONTAL}.
     */
    public void setOrientation(int orientation) {
        this.orientation = orientation;
        revalidate();
        repaint();
    }

    public boolean isClockwise() {
        return clockwise;
    }

    public void setClockwise(boolean clockwise) {
        this.clockwise = clockwise;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        return (orientation == VERTICAL) ? new Dimension(size.height, size.width) : size;
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        final Dimension size = super.getMinimumSize();
        return (orientation == VERTICAL) ? new Dimension(size.height, size.width) : size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (orientation != VERTICAL) {
            super.paintComponent(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final int width = getWidth(), height = getHeight();
            final AffineTransform transform = g2.getTransform();
            if (clockwise) {
                transform.translate(width, 0);
                transform.rotate(Math.PI / 2);
            } else {
                transform.translate(0, height);
                transform.rotate(-Math.PI / 2);
            }
            g2.setTransform(transform);
            // Temporarily swap the dimensions so the UI delegate lays out the text along the long axis
            painting = true;
            try {
                getUI().paint(g2, this);
            } finally {
                painting = false;
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getWidth() {
        return painting ? super.getHeight() : super.getWidth();
    }

    @Override
    public int getHeight() {
        return painting ? super.getWidth() : super.getHeight();
    }

    private int orientation = VERTICAL;
    private boolean clockwise;
    private transient boolean painting;

    private static final long serialVersionUID = 1L;
}
