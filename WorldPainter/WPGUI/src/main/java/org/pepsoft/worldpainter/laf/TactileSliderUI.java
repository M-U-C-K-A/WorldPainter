package org.pepsoft.worldpainter.laf;

import com.formdev.flatlaf.ui.FlatSliderUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.Line2D;

/**
 * The sliders of the Tactile theme: a wide groove sunk into the surface, with the tick marks inside it, and a raised
 * pill shaped thumb which slides along it.
 */
public class TactileSliderUI extends FlatSliderUI {
    public static ComponentUI createUI(JComponent c) {
        return new TactileSliderUI();
    }

    @Override
    public void paintTrack(Graphics g) {
        if (slider.getOrientation() != JSlider.HORIZONTAL) {
            super.paintTrack(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final Rectangle groove = groove();
            Tactile.paintSunken(g2, groove.x, groove.y, groove.width, groove.height, UIScale.scale(12f));
            if (slider.getPaintTicks()) {
                paintNotches(g2, groove);
            }
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintTicks(Graphics g) {
        if (slider.getOrientation() != JSlider.HORIZONTAL) {
            super.paintTicks(g);
        }
        // Horizontal sliders have their ticks inside the groove, painted with it
    }

    /**
     * The tick marks inside the groove, as notches.
     */
    private void paintNotches(Graphics2D g2, Rectangle groove) {
        {
            g2.setColor(Tactile.colour("Tactile.tick", new Color(0xb5b5b5)));
            g2.setStroke(new BasicStroke(UIScale.scale(1.5f), BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
            final float middle = groove.y + groove.height / 2f;
            final int major = slider.getMajorTickSpacing(), minor = slider.getMinorTickSpacing();
            final int spacing = (minor > 0) ? minor : major;
            if (spacing > 0) {
                for (int value = slider.getMinimum(); value <= slider.getMaximum(); value += spacing) {
                    final boolean isMajor = (major > 0) && (((value - slider.getMinimum()) % major) == 0);
                    final float half = groove.height * (isMajor ? 0.22f : 0.13f);
                    final float x = xPositionForValue(value);
                    g2.draw(new Line2D.Float(x, middle - half, x, middle + half));
                }
            }
        }
    }

    @Override
    public void paintThumb(Graphics g) {
        if (slider.getOrientation() != JSlider.HORIZONTAL) {
            super.paintThumb(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            final Object[] hints = FlatUIUtils.setRenderingHints(g2);
            final Rectangle groove = groove();
            final float inset = UIScale.scale(3f);
            final float width = thumbRect.width - UIScale.scale(2f), height = groove.height - 2 * inset;
            final float x = thumbRect.x + UIScale.scale(1f), y = groove.y + inset - UIScale.scale(1f);
            Tactile.paintRaised(g2, x, y, width, height, width, Tactile.Colours.of("Tactile.thumb"), isDragging(), false, slider.isEnabled());
            FlatUIUtils.resetRenderingHints(g2, hints);
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected int getTickLength() {
        // The ticks are drawn inside the groove, so they need no room of their own
        return 0;
    }

    /**
     * The groove: as high as the track (which is as high as the thumb), from one end of it to the other with room for
     * the thumb at both ends, within the slider.
     */
    private Rectangle groove() {
        final int padding = thumbRect.width / 2 + Math.round(UIScale.scale(3f));
        final int x1 = Math.max(0, trackRect.x - padding), x2 = Math.min(slider.getWidth(), trackRect.x + trackRect.width + padding);
        final int y1 = Math.max(0, trackRect.y), y2 = Math.min(slider.getHeight(), trackRect.y + trackRect.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }
}
